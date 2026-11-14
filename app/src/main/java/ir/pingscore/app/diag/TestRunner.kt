package ir.pingscore.app.diag

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** پیکربندی مؤثر یک اجرا (از تنظیمات کاربر + پیش‌فرض‌ها). */
data class AppConfig(
    val pingTargets: List<PingTarget> = Defaults.pingTargets,
    val sites: List<SiteDef> = Defaults.sites,
    val dnsDomains: List<String> = Defaults.dnsDomains,
    val speedTargets: List<SpeedTarget> = Defaults.speedTargets,
)

data class RawResults(
    val ping: PingStats?,
    val speed: SpeedStats?,
    val sites: List<SiteResult>,
    val dns: DnsResult?,
)

/**
 * ترتیب اجرا: پینگ (روی شبکه‌ی بی‌بار) ← سایت‌ها + DNS هم‌زمان ← سرعت (چون پهنای باند را پر می‌کند).
 * هر بخش جداگانه محافظت شده؛ شکست یک بخش کل تست را خراب نمی‌کند.
 */
class TestRunner(dnsInfoProvider: () -> DnsInfo? = { null }) {

    private val pingTester = PingTester()
    private val speedTester = SpeedTester()
    private val siteChecker = SiteChecker()
    private val dnsChecker = DnsChecker(dnsInfoProvider = dnsInfoProvider)

    suspend fun run(
        mode: TestMode,
        config: AppConfig,
        includeSpeed: Boolean,
        onProgress: (TestProgress) -> Unit,
    ): RawResults {
        val mc = ModeConfig.of(mode)
        val pingEnd = 0.15f
        val sitesEnd = if (includeSpeed) 0.35f else 1.0f

        onProgress(TestProgress(TestStage.PING, 0f))
        val pingTargets = config.pingTargets.take(mc.pingTargetsMax)
        val ping: PingStats? = if (pingTargets.isEmpty()) null else safe {
            withTimeoutOrNull(PING_TIMEOUT_MS) {
                pingTester.run(pingTargets, mc.pingSamples) { f ->
                    onProgress(TestProgress(TestStage.PING, f * pingEnd))
                }
            }
        }

        onProgress(TestProgress(TestStage.SITES_DNS, pingEnd))
        val (sites, dns) = coroutineScope {
            val s = async {
                safe {
                    siteChecker.check(config.sites) { f ->
                        onProgress(TestProgress(TestStage.SITES_DNS, pingEnd + f * (sitesEnd - pingEnd)))
                    }
                } ?: emptyList()
            }
            val d = async {
                safe {
                    withTimeoutOrNull(DNS_TIMEOUT_MS) { dnsChecker.check(config.dnsDomains.take(mc.dnsDomainsMax)) }
                }
            }
            s.await() to d.await()
        }

        val speed: SpeedStats? = if (!includeSpeed) null else safe {
            val dl = config.speedTargets.take(mc.downloadTargetsMax)
            val ul = config.speedTargets.filter { it.uploadUrl != null }.take(mc.uploadTargetsMax)
            speedTester.run(
                SpeedConfig(dl, ul, mc.connections, mc.durationMs, mc.capBytesPerTarget),
            ) { stage, f, live ->
                onProgress(TestProgress(stage, sitesEnd + f * (1f - sitesEnd), live))
            }
        }

        return RawResults(ping, speed, sites, dns)
    }

    private suspend fun <T> safe(block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val PING_TIMEOUT_MS = 20_000L
        const val DNS_TIMEOUT_MS = 12_000L
    }
}
