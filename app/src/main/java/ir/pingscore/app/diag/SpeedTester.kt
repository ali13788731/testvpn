package ir.pingscore.app.diag

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

data class SpeedConfig(
    val downloadTargets: List<SpeedTarget>,
    val uploadTargets: List<SpeedTarget>,
    val connections: Int,
    val durationMs: Long,
    val capBytesPerTarget: Long,
)

/**
 * تست سرعت دانلود/آپلود با چند اتصال هم‌زمان (HTTP/1.1 تا هر درخواست اتصال جدا بگیرد).
 * هر مقصد به‌ترتیب و برای مدت [SpeedConfig.durationMs] تست می‌شود؛ ۰٫۵ ثانیه‌ی اول (گرم‌شدن) شمرده نمی‌شود.
 * برای صرفه‌جویی در دیتا، اگر حجم از [SpeedConfig.capBytesPerTarget] گذشت تست زودتر تمام می‌شود.
 */
class SpeedTester(baseClient: OkHttpClient = Http.base) {

    private val client: OkHttpClient = baseClient.newBuilder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .retryOnConnectionFailure(false)
        .build()

    /** onProgress(مرحله، پیشرفت کل بخش سرعت ۰..۱، سرعت لحظه‌ای Mbps) */
    suspend fun run(
        config: SpeedConfig,
        onProgress: (TestStage, Float, Double?) -> Unit = { _, _, _ -> },
    ): SpeedStats {
        val steps = (config.downloadTargets.size + config.uploadTargets.size).coerceAtLeast(1)
        var step = 0

        val downloads = ArrayList<TargetSpeed>()
        for (t in config.downloadTargets) {
            val base = step
            val mbps = safe {
                measure(config, { stop, counter, calls -> downloadWorker(t.downloadUrl, stop, counter, calls) }) { live, f ->
                    onProgress(TestStage.DOWNLOAD, (base + f) / steps, live)
                }
            }
            downloads.add(TargetSpeed(t.label, mbps))
            step++
        }

        val uploads = ArrayList<TargetSpeed>()
        for (t in config.uploadTargets) {
            val url = t.uploadUrl ?: continue
            val base = step
            val mbps = safe {
                measure(config, { stop, counter, calls -> uploadWorker(url, stop, counter, calls) }) { live, f ->
                    onProgress(TestStage.UPLOAD, (base + f) / steps, live)
                }
            }
            uploads.add(TargetSpeed(t.label, mbps))
            step++
        }

        return SpeedStats(
            downloadMbps = median(downloads.mapNotNull { it.mbps }),
            uploadMbps = median(uploads.mapNotNull { it.mbps }),
            downloads = downloads,
            uploads = uploads,
        )
    }

    private suspend fun safe(block: suspend () -> Double?): Double? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun measure(
        config: SpeedConfig,
        worker: (AtomicBoolean, AtomicLong, ConcurrentLinkedQueue<Call>) -> Unit,
        onLive: (Double, Float) -> Unit,
    ): Double? = coroutineScope {
        val counter = AtomicLong(0)
        val stop = AtomicBoolean(false)
        val calls = ConcurrentLinkedQueue<Call>()

        val jobs = List(config.connections) {
            async(Dispatchers.IO) {
                try {
                    worker(stop, counter, calls)
                } catch (e: Exception) {
                    // خطای یک اتصال کل تست را خراب نمی‌کند؛ بایت‌های شمرده‌شده معتبر می‌مانند.
                }
            }
        }

        val t0 = System.nanoTime()
        var warmBytes = -1L
        var warmMs = 0L
        var elapsedMs = 0L
        var bytes = 0L
        try {
            while (true) {
                delay(POLL_MS)
                elapsedMs = (System.nanoTime() - t0) / 1_000_000
                bytes = counter.get()
                if (warmBytes < 0 && elapsedMs >= WARMUP_MS) {
                    warmBytes = bytes
                    warmMs = elapsedMs
                }
                onLive(mbps(bytes, elapsedMs), (elapsedMs.toFloat() / config.durationMs).coerceIn(0f, 1f))
                if (elapsedMs >= config.durationMs || bytes >= config.capBytesPerTarget || jobs.all { it.isCompleted }) break
            }
        } finally {
            stop.set(true)
            calls.forEach { it.cancel() }
        }

        val endMs = elapsedMs
        val endBytes = bytes
        jobs.awaitAll()

        if (endBytes <= 0L) {
            null
        } else {
            val useWarm = warmBytes >= 0 && endMs - warmMs >= MIN_WINDOW_MS && endBytes > warmBytes
            val b = if (useWarm) endBytes - warmBytes else endBytes
            val ms = if (useWarm) endMs - warmMs else endMs
            if (ms <= 0L) null else mbps(b, ms)
        }
    }

    private fun mbps(bytes: Long, ms: Long): Double =
        if (ms <= 0L) 0.0 else bytes * 8.0 / (ms / 1000.0) / 1_000_000.0

    private fun downloadWorker(
        url: String,
        stop: AtomicBoolean,
        counter: AtomicLong,
        calls: ConcurrentLinkedQueue<Call>,
    ) {
        val buf = ByteArray(64 * 1024)
        while (!stop.get()) {
            val call = client.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build())
            calls.add(call)
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val stream = resp.body?.byteStream() ?: throw IOException("empty body")
                    while (!stop.get()) {
                        val n = stream.read(buf)
                        if (n < 0) break
                        counter.addAndGet(n.toLong())
                    }
                }
            } finally {
                calls.remove(call)
            }
        }
    }

    private fun uploadWorker(
        url: String,
        stop: AtomicBoolean,
        counter: AtomicLong,
        calls: ConcurrentLinkedQueue<Call>,
    ) {
        while (!stop.get()) {
            val body = CountingBody(stop, counter, UPLOAD_CHUNK_TOTAL)
            val call = client.newCall(Request.Builder().url(url).post(body).build())
            calls.add(call)
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                }
            } finally {
                calls.remove(call)
            }
        }
    }

    private class CountingBody(
        private val stop: AtomicBoolean,
        private val counter: AtomicLong,
        private val total: Long,
    ) : RequestBody() {
        override fun contentType() = "application/octet-stream".toMediaType()
        override fun contentLength(): Long = total
        override fun writeTo(sink: BufferedSink) {
            val chunk = ByteArray(32 * 1024).also { Random.nextBytes(it) }
            var left = total
            while (left > 0) {
                if (stop.get()) throw IOException("stopped")
                val n = minOf(left, chunk.size.toLong()).toInt()
                sink.write(chunk, 0, n)
                counter.addAndGet(n.toLong())
                left -= n
            }
        }
    }

    private companion object {
        const val POLL_MS = 100L
        const val WARMUP_MS = 500L
        const val MIN_WINDOW_MS = 800L
        const val UPLOAD_CHUNK_TOTAL = 2L * 1024 * 1024
    }
}
