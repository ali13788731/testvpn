package ir.pingscore.app.diag

import ir.pingscore.app.score.ScoreReport
import kotlinx.serialization.Serializable

/** حالت تست: «سریع» کم‌حجم و کوتاه است، «کامل» دقیق‌تر. */
@Serializable
enum class TestMode { QUICK, FULL }

/** تست از کجا شروع شده است. */
@Serializable
enum class TestSource { MANUAL, NETWORK_CHANGE, BACKGROUND }

@Serializable
enum class SiteCategory { AI, DEV, CHAT, SOCIAL, VIDEO, OTHER }

@Serializable
enum class SiteStatus { OPEN, IP_BLOCKED, SLOW, FAILED }

@Serializable
enum class DnsDomainStatus { OK, SUSPICIOUS, MISMATCH, FAILED }

/** مقصد پینگ. اگر [url] خالی نباشد نمونه‌ی HTTP است، وگرنه اتصال TCP به host:port. */
@Serializable
data class PingTarget(
    val label: String,
    val host: String = "",
    val port: Int = 443,
    val url: String? = null,
)

@Serializable
data class SiteDef(
    val name: String,
    val category: SiteCategory,
    val url: String,
)

// ---------- نتیجه‌ها ----------

@Serializable
data class TargetPing(
    val label: String,
    val sent: Int,
    val received: Int,
    val medianMs: Double?,
    val jitterMs: Double?,
)

@Serializable
data class PingStats(
    val medianMs: Double?,
    val jitterMs: Double?,
    val lossPercent: Double,
    val sent: Int,
    val received: Int,
    val perTarget: List<TargetPing>,
)

@Serializable
data class TargetSpeed(val label: String, val mbps: Double?)

@Serializable
data class SpeedStats(
    val downloadMbps: Double?,
    val uploadMbps: Double?,
    val downloads: List<TargetSpeed>,
    val uploads: List<TargetSpeed>,
)

@Serializable
data class SiteResult(
    val name: String,
    val category: SiteCategory,
    val url: String,
    val status: SiteStatus,
    val httpCode: Int?,
    val latencyMs: Long?,
)

@Serializable
data class DnsDomainResult(
    val domain: String,
    val status: DnsDomainStatus,
    val systemIps: List<String>,
    val referenceIps: List<String>,
    val ms: Long?,
)

@Serializable
data class DnsResult(
    val domains: List<DnsDomainResult>,
    val okCount: Int,
    val suspiciousCount: Int,
    val failedCount: Int,
    val medianMs: Long?,
    /** null یعنی قابل ارزیابی نبود؛ true یعنی نشت احتمالی. */
    val leakSuspected: Boolean?,
    val dnsServers: List<String>,
)

@Serializable
data class ConnectionInfo(
    val transport: String,
    val viaTunnel: Boolean,
    val isMetered: Boolean,
    val label: String,
)

@Serializable
data class TestReport(
    val timeMillis: Long,
    val mode: TestMode,
    val source: TestSource,
    val connection: ConnectionInfo,
    val ping: PingStats?,
    val speed: SpeedStats?,
    val sites: List<SiteResult>,
    val dns: DnsResult?,
    val score: ScoreReport,
)

/** اطلاعات DNS سیستم برای تشخیص نشت. */
data class DnsInfo(
    val activeServers: List<String>,
    val underlyingServers: List<String>,
    val viaTunnel: Boolean,
)

// ---------- پیشرفت ----------

enum class TestStage { PING, SITES_DNS, DOWNLOAD, UPLOAD }

data class TestProgress(
    val stage: TestStage,
    /** پیشرفت کل ۰ تا ۱. */
    val fraction: Float,
    val liveMbps: Double? = null,
)
