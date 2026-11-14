package ir.pingscore.app.ui

import androidx.compose.ui.graphics.Color
import ir.pingscore.app.data.TestEntity
import ir.pingscore.app.diag.DnsDomainStatus
import ir.pingscore.app.diag.SiteCategory
import ir.pingscore.app.diag.SiteStatus
import ir.pingscore.app.diag.TestMode
import ir.pingscore.app.diag.TestReport
import ir.pingscore.app.diag.TestSource
import ir.pingscore.app.diag.TestStage
import ir.pingscore.app.score.ScoreEngine
import ir.pingscore.app.score.ScoreLabel
import ir.pingscore.app.score.UseCase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun labelColor(l: ScoreLabel): Color = when (l) {
    ScoreLabel.EXCELLENT -> ScoreColors.good
    ScoreLabel.MEDIUM -> ScoreColors.medium
    ScoreLabel.POOR -> ScoreColors.poor
}

fun scoreColor(score: Int): Color = labelColor(ScoreEngine.labelFor(score))

fun fmt1(v: Double?): String = if (v == null) "—" else String.format(Locale.US, "%.1f", v)
fun fmt0(v: Double?): String = if (v == null) "—" else String.format(Locale.US, "%.0f", v)

fun formatTime(ms: Long): String =
    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(ms))

fun formatClock(ms: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

fun modeName(mode: TestMode) = if (mode == TestMode.QUICK) "سریع" else "کامل"
fun modeName(name: String) = if (name == TestMode.FULL.name) "کامل" else "سریع"

fun sourceName(s: TestSource) = when (s) {
    TestSource.MANUAL -> "دستی"
    TestSource.NETWORK_CHANGE -> "تغییر شبکه"
    TestSource.BACKGROUND -> "پایش خودکار"
}

fun siteStatusName(s: SiteStatus) = when (s) {
    SiteStatus.OPEN -> "باز شد"
    SiteStatus.IP_BLOCKED -> "IP بلاک شده (403/429)"
    SiteStatus.SLOW -> "کند"
    SiteStatus.FAILED -> "وصل نشد"
}

fun siteStatusColor(s: SiteStatus): Color = when (s) {
    SiteStatus.OPEN -> ScoreColors.good
    SiteStatus.SLOW -> ScoreColors.medium
    SiteStatus.IP_BLOCKED, SiteStatus.FAILED -> ScoreColors.poor
}

fun categoryName(c: SiteCategory) = when (c) {
    SiteCategory.AI -> "هوش مصنوعی"
    SiteCategory.DEV -> "توسعه"
    SiteCategory.CHAT -> "پیام‌رسان"
    SiteCategory.SOCIAL -> "شبکه اجتماعی"
    SiteCategory.VIDEO -> "ویدیو و رسانه"
    SiteCategory.OTHER -> "عمومی"
}

fun dnsStatusName(s: DnsDomainStatus) = when (s) {
    DnsDomainStatus.OK -> "سالم"
    DnsDomainStatus.SUSPICIOUS -> "پاسخ مشکوک"
    DnsDomainStatus.MISMATCH -> "پاسخ متفاوت"
    DnsDomainStatus.FAILED -> "پاسخ نداد"
}

fun stageName(s: TestStage) = when (s) {
    TestStage.PING -> "در حال سنجش تأخیر…"
    TestStage.SITES_DNS -> "در حال بررسی سایت‌ها و DNS…"
    TestStage.DOWNLOAD -> "در حال سنجش سرعت دانلود…"
    TestStage.UPLOAD -> "در حال سنجش سرعت آپلود…"
}

/** خروجی متنی یک نتیجه برای اشتراک‌گذاری. */
fun reportText(entity: TestEntity, r: TestReport): String = buildString {
    appendLine("PingScore — ${entity.name}")
    appendLine("زمان: ${formatTime(r.timeMillis)}  |  حالت: ${modeName(r.mode)}  |  شبکه: ${r.connection.label}")
    if (entity.isBaseline) appendLine("(اتصال پایه)")
    appendLine()
    appendLine("کیفیت اتصال: ${r.score.overall}/100 (${ScoreEngine.labelName(r.score.overallLabel)})")
    r.score.useCases.forEach {
        appendLine("• ${ScoreEngine.useCaseName(it.useCase)}: ${it.score} (${ScoreEngine.labelName(it.label)})")
    }
    appendLine(r.score.overallSummary)
    appendLine()
    r.ping?.let {
        appendLine("ping: ${fmt0(it.medianMs)} ms | jitter: ${fmt1(it.jitterMs)} ms | packet loss: ${fmt0(it.lossPercent)}%")
    }
    r.speed?.let {
        appendLine("دانلود: ${fmt1(it.downloadMbps)} Mbps | آپلود: ${fmt1(it.uploadMbps)} Mbps")
    }
    if (r.sites.isNotEmpty()) {
        appendLine()
        appendLine("سایت‌ها:")
        r.sites.forEach { appendLine("- ${it.name}: ${siteStatusName(it.status)}") }
    }
    r.dns?.let {
        appendLine()
        appendLine("DNS: ${it.okCount}/${it.domains.size} سالم، ${it.suspiciousCount} مشکوک" +
            if (it.leakSuspected == true) "، نشت احتمالی" else "")
    }
}

/** خروجی متنی مقایسه‌ی چند اتصال. */
fun compareText(items: List<Pair<TestEntity, TestReport>>): String = buildString {
    appendLine("PingScore — مقایسه‌ی اتصال‌ها")
    items.forEach { (e, r) ->
        appendLine()
        appendLine("■ ${e.name}${if (e.isBaseline) " (پایه)" else ""} — ${formatTime(r.timeMillis)} — ${modeName(r.mode)}")
        appendLine("  کیفیت: ${r.score.overall} | " +
            UseCase.values().joinToString(" | ") { uc ->
                "${ScoreEngine.useCaseName(uc)}: ${r.score.of(uc)?.score ?: 0}"
            })
        appendLine("  ping ${fmt0(r.ping?.medianMs)} ms | jitter ${fmt1(r.ping?.jitterMs)} | loss ${fmt0(r.ping?.lossPercent)}%" +
            " | دانلود ${fmt1(r.speed?.downloadMbps)} | آپلود ${fmt1(r.speed?.uploadMbps)} Mbps")
    }
}
