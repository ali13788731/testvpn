package ir.pingscore.app.data

import android.content.Context
import ir.pingscore.app.diag.ConnectionInfo
import ir.pingscore.app.diag.TestMode
import ir.pingscore.app.diag.TestProgress
import ir.pingscore.app.diag.TestReport
import ir.pingscore.app.diag.TestRunner
import ir.pingscore.app.diag.TestSource
import ir.pingscore.app.network.NetworkMonitor
import ir.pingscore.app.network.NetworkSnapshot
import ir.pingscore.app.network.TransportType
import ir.pingscore.app.score.ScoreEngine
import ir.pingscore.app.score.ScoreInput

/** اجرای کامل: تست ← نمره‌دهی ← ذخیره. هم ViewModel و هم Worker استفاده می‌کنند. */
class TestPipeline(context: Context) {

    private val appContext = context.applicationContext
    private val monitor = NetworkMonitor(appContext)
    private val runner = TestRunner { monitor.readDnsInfo() }
    private val repo = TestRepository(AppGraph.db(appContext).testDao())

    /** شناسه‌ی ردیف ذخیره‌شده، یا null اگر اتصالی نبود. */
    suspend fun run(
        mode: TestMode,
        source: TestSource,
        name: String?,
        includeSpeed: Boolean,
        settings: AppSettings,
        onProgress: (TestProgress) -> Unit = {},
    ): Long? {
        val snapshot = monitor.currentSnapshot()
        if (!snapshot.isConnected) return null

        val raw = runner.run(mode, settings.toConfig(), includeSpeed, onProgress)
        val score = ScoreEngine.evaluate(ScoreInput(raw.ping, raw.speed, raw.sites, raw.dns))
        val info = connectionInfo(snapshot)
        val report = TestReport(
            timeMillis = System.currentTimeMillis(),
            mode = mode,
            source = source,
            connection = info,
            ping = raw.ping,
            speed = raw.speed,
            sites = raw.sites,
            dns = raw.dns,
            score = score,
        )
        return repo.save(report, name?.takeIf { it.isNotBlank() } ?: info.label)
    }

    companion object {
        fun connectionInfo(s: NetworkSnapshot) = ConnectionInfo(
            transport = s.transport.name,
            viaTunnel = s.viaTunnel,
            isMetered = s.isMetered,
            label = describe(s),
        )

        fun describe(s: NetworkSnapshot): String {
            if (!s.isConnected) return "قطع"
            val base = when (s.transport) {
                TransportType.WIFI -> "Wi-Fi"
                TransportType.CELLULAR -> "اینترنت همراه"
                TransportType.ETHERNET -> "کابل شبکه"
                TransportType.UNKNOWN -> "نامشخص"
            }
            return if (s.viaTunnel) "$base + مسیر واسط" else base
        }
    }
}
