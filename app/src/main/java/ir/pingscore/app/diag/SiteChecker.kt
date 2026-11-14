package ir.pingscore.app.diag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** بررسی هم‌زمان دسترسی به سایت‌ها با یک درخواست سبک و timeout حدود ۴٫۵ ثانیه. */
class SiteChecker(baseClient: OkHttpClient = Http.base) {

    private val client: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun check(sites: List<SiteDef>, onProgress: (Float) -> Unit = {}): List<SiteResult> = coroutineScope {
        val done = AtomicInteger(0)
        val total = sites.size.coerceAtLeast(1)
        sites.map { site ->
            async(Dispatchers.IO) {
                checkOne(site).also { onProgress(done.incrementAndGet().toFloat() / total) }
            }
        }.awaitAll()
    }

    private fun checkOne(site: SiteDef): SiteResult {
        return try {
            val req = Request.Builder()
                .url(site.url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .build()
            val t0 = System.nanoTime()
            client.newCall(req).execute().use { resp ->
                val ms = (System.nanoTime() - t0) / 1_000_000
                SiteResult(site.name, site.category, site.url, classify(resp.code, ms), resp.code, ms)
            }
        } catch (e: Exception) {
            SiteResult(site.name, site.category, site.url, SiteStatus.FAILED, null, null)
        }
    }

    companion object {
        const val CALL_TIMEOUT_MS = 4_500L
        const val SLOW_MS = 2_500L
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

        /** 403/429 = بلاک IP؛ 5xx = ناموفق؛ بقیه‌ی کدها یعنی سرور در دسترس است (حتی 401/404). */
        fun classify(code: Int, latencyMs: Long): SiteStatus = when {
            code == 403 || code == 429 -> SiteStatus.IP_BLOCKED
            code >= 500 -> SiteStatus.FAILED
            latencyMs > SLOW_MS -> SiteStatus.SLOW
            else -> SiteStatus.OPEN
        }
    }
}
