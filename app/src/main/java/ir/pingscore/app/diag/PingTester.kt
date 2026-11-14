package ir.pingscore.app.diag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * پینگ سبک: چند نمونه TCP connect یا درخواست HTTP سبک روی چند مقصد (موازی).
 * خروجی: میانه، jitter و packet loss.
 */
class PingTester(baseClient: OkHttpClient = Http.base) {

    private class Raw(val label: String, val sent: Int, val rtts: List<Double>)

    private val httpClient: OkHttpClient = baseClient.newBuilder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .callTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .build()

    suspend fun run(
        targets: List<PingTarget>,
        samples: Int,
        onProgress: (Float) -> Unit = {},
    ): PingStats = coroutineScope {
        val total = (targets.size * samples).coerceAtLeast(1)
        val done = AtomicInteger(0)
        val tick = { onProgress(done.incrementAndGet().toFloat() / total) }

        val raws = targets.map { t ->
            async(Dispatchers.IO) {
                if (t.url != null) httpTarget(t, samples, tick) else tcpTarget(t, samples, tick)
            }
        }.awaitAll()

        aggregate(raws)
    }

    private suspend fun tcpTarget(t: PingTarget, samples: Int, tick: () -> Unit): Raw {
        val addr: InetAddress? = try {
            InetAddress.getByName(t.host)
        } catch (e: Exception) {
            null
        }
        val rtts = ArrayList<Double>()
        for (i in 0 until samples) {
            if (addr != null) tcpOnce(addr, t.port)?.let { rtts.add(it) }
            tick()
            if (i < samples - 1) delay(GAP_MS)
        }
        return Raw(t.label, samples, rtts)
    }

    private fun tcpOnce(addr: InetAddress, port: Int): Double? {
        val socket = Socket()
        return try {
            val t0 = System.nanoTime()
            socket.connect(InetSocketAddress(addr, port), TIMEOUT_MS)
            (System.nanoTime() - t0) / 1_000_000.0
        } catch (e: Exception) {
            null
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun httpTarget(t: PingTarget, samples: Int, tick: () -> Unit): Raw {
        val url = t.url ?: return Raw(t.label, samples, emptyList())
        // نمونه‌ی گرم‌کردن (DNS/TLS) شمرده نمی‌شود؛ بعدش اتصال زنده می‌ماند و تقریباً یک RTT اندازه می‌گیریم.
        val warmOk = httpOnce(url) != null
        val rtts = ArrayList<Double>()
        for (i in 0 until samples) {
            if (warmOk) httpOnce(url)?.let { rtts.add(it) }
            tick()
            if (i < samples - 1) delay(GAP_MS)
        }
        return Raw(t.label, samples, rtts)
    }

    private fun httpOnce(url: String): Double? = try {
        val req = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
        val t0 = System.nanoTime()
        httpClient.newCall(req).execute().use { resp ->
            if (resp.code >= 500) null else (System.nanoTime() - t0) / 1_000_000.0
        }
    } catch (e: Exception) {
        null
    }

    private fun aggregate(raws: List<Raw>): PingStats {
        val all = raws.flatMap { it.rtts }
        val sent = raws.sumOf { it.sent }
        val received = all.size
        val perTarget = raws.map {
            TargetPing(it.label, it.sent, it.rtts.size, median(it.rtts), jitter(it.rtts))
        }
        val jitters = perTarget.mapNotNull { it.jitterMs }
        val loss = if (sent == 0) 0.0 else (sent - received) * 100.0 / sent
        return PingStats(
            medianMs = median(all),
            jitterMs = if (jitters.isEmpty()) null else jitters.average(),
            lossPercent = loss,
            sent = sent,
            received = received,
            perTarget = perTarget,
        )
    }

    private companion object {
        const val TIMEOUT_MS = 2_000
        const val GAP_MS = 120L
    }
}
