package ir.pingscore.app.score

import ir.pingscore.app.diag.DnsResult
import ir.pingscore.app.diag.PingStats
import ir.pingscore.app.diag.SiteResult
import ir.pingscore.app.diag.SpeedStats
import kotlin.math.roundToInt

data class ScoreInput(
    val ping: PingStats?,
    val speed: SpeedStats?,
    val sites: List<SiteResult>,
    val dns: DnsResult?,
)

/** موتور نمره‌دهی ۰ تا ۱۰۰ برای سه کاربرد. کاملاً خالص و قابل تست واحد. */
object ScoreEngine {

    fun evaluate(input: ScoreInput, config: ScoreConfig = ScoreConfig.Default): ScoreReport {
        val list = UseCase.values().map { evaluateUseCase(it, input, config) }
        val overall = list.map { it.score }.average().roundToInt().coerceIn(0, 100)
        val label = labelFor(overall, config)
        return ScoreReport(overall, label, overallSummary(label, list), list)
    }

    fun labelFor(score: Int, config: ScoreConfig = ScoreConfig.Default): ScoreLabel = when {
        score >= config.excellentMin -> ScoreLabel.EXCELLENT
        score >= config.mediumMin -> ScoreLabel.MEDIUM
        else -> ScoreLabel.POOR
    }

    private fun evaluateUseCase(uc: UseCase, input: ScoreInput, config: ScoreConfig): UseCaseScore {
        val p = config.profiles.getValue(uc)
        val raw = listOf(
            Triple(ScoreComponent.ACCESS, accessScore(input, p, config), p.weights.access),
            Triple(ScoreComponent.SPEED_LOSS, speedLossScore(input, p), p.weights.speedLoss),
            Triple(ScoreComponent.LATENCY, latencyScore(input, p), p.weights.latency),
        )
        val present = raw.filter { it.second != null && it.third > 0.0 }
        val totalWeight = present.sumOf { it.third }

        val score = if (present.isEmpty() || totalWeight <= 0.0) {
            0
        } else {
            (present.sumOf { (it.second ?: 0.0) * it.third } / totalWeight).roundToInt().coerceIn(0, 100)
        }

        val components = raw.map { (c, v, w) ->
            val usable = v != null && w > 0.0 && totalWeight > 0.0
            ComponentScore(c, v?.roundToInt()?.coerceIn(0, 100), if (usable) w / totalWeight else 0.0)
        }
        val label = labelFor(score, config)
        val weakest = components.filter { it.score != null }.minByOrNull { it.score ?: 0 }?.component
        return UseCaseScore(uc, score, label, summaryFor(uc, label, weakest), components)
    }

    /** دسترسی سایت‌ها و پایداری (DNS). null اگر هیچ سایتی تست نشده باشد. */
    internal fun accessScore(input: ScoreInput, p: UseCaseProfile, config: ScoreConfig): Double? {
        val relevant = input.sites.filter { it.category in p.siteCategories }.ifEmpty { input.sites }
        if (relevant.isEmpty()) return null
        val base = relevant.map { config.sitePoints[it.status] ?: 0.0 }.average() * 100.0

        var penalty = 0.0
        input.dns?.let { dns ->
            if (dns.suspiciousCount > 0) penalty += config.dnsSuspiciousPenalty
            if (dns.leakSuspected == true) penalty += config.dnsLeakPenalty
            val total = dns.domains.size
            if (total > 0) penalty += config.dnsFailedPenaltyMax * dns.failedCount / total
        }
        return (base - penalty).coerceIn(0.0, 100.0)
    }

    /** سرعت و packet loss. */
    internal fun speedLossScore(input: ScoreInput, p: UseCaseProfile): Double? {
        val speedPart: Double? = input.speed?.let { s ->
            val parts = ArrayList<Pair<Double, Double>>()
            // دانلودِ اندازه‌گیری‌شده ولی ناموفق = ۰
            parts.add(ratio(s.downloadMbps ?: 0.0, p.downloadTargetMbps) to p.downloadShare)
            // آپلودِ null یعنی تست نشده (یا مقصدی نبوده) و حذف می‌شود
            s.uploadMbps?.let { parts.add(ratio(it, p.uploadTargetMbps) to (1.0 - p.downloadShare)) }
            weightedAverage(parts)?.times(100.0)
        }
        val lossPart: Double? = input.ping?.let {
            (1.0 - it.lossPercent / p.lossZeroAtPercent).coerceIn(0.0, 1.0) * 100.0
        }
        return weightedAverage(
            listOfNotNull(
                speedPart?.let { it to p.speedShare },
                lossPart?.let { it to (1.0 - p.speedShare) },
            ),
        )
    }

    /** ping و jitter. */
    internal fun latencyScore(input: ScoreInput, p: UseCaseProfile): Double? {
        val ping = input.ping ?: return null
        val median = ping.medianMs ?: return 0.0
        val pingScore = lowerIsBetter(median, p.pingGoodMs, p.pingBadMs) * 100.0
        val jitter = ping.jitterMs ?: return pingScore
        val jitterScore = lowerIsBetter(jitter, p.jitterGoodMs, p.jitterBadMs) * 100.0
        return pingScore * p.pingShare + jitterScore * (1.0 - p.pingShare)
    }

    private fun ratio(value: Double, target: Double): Double =
        if (target <= 0.0) 1.0 else (value / target).coerceIn(0.0, 1.0)

    internal fun lowerIsBetter(value: Double, good: Double, bad: Double): Double =
        if (bad <= good) (if (value <= good) 1.0 else 0.0)
        else ((bad - value) / (bad - good)).coerceIn(0.0, 1.0)

    private fun weightedAverage(parts: List<Pair<Double, Double>>): Double? {
        val usable = parts.filter { it.second > 0.0 }
        val total = usable.sumOf { it.second }
        if (usable.isEmpty() || total <= 0.0) return null
        return usable.sumOf { it.first * it.second } / total
    }

    // ---------- متن‌های فارسی ----------

    fun useCaseName(uc: UseCase): String = when (uc) {
        UseCase.AI -> "کار با هوش مصنوعی"
        UseCase.VIDEO -> "ویدیو و دانلود"
        UseCase.CHAT -> "چت و تماس"
    }

    fun componentName(c: ScoreComponent): String = when (c) {
        ScoreComponent.ACCESS -> "دسترسی سایت‌ها و پایداری"
        ScoreComponent.SPEED_LOSS -> "سرعت و packet loss"
        ScoreComponent.LATENCY -> "ping و jitter"
    }

    fun labelName(l: ScoreLabel): String = when (l) {
        ScoreLabel.EXCELLENT -> "عالی"
        ScoreLabel.MEDIUM -> "متوسط"
        ScoreLabel.POOR -> "ضعیف"
    }

    private fun summaryFor(uc: UseCase, label: ScoreLabel, weakest: ScoreComponent?): String {
        val name = useCaseName(uc)
        val weak = weakest?.let { componentName(it) }
        return when (label) {
            ScoreLabel.EXCELLENT -> "برای $name عالی است."
            ScoreLabel.MEDIUM ->
                if (weak != null) "برای $name قابل‌قبول است؛ ضعف اصلی: $weak." else "برای $name قابل‌قبول است."
            ScoreLabel.POOR ->
                if (weak != null) "برای $name مناسب نیست؛ مشکل اصلی: $weak." else "برای $name مناسب نیست."
        }
    }

    private fun overallSummary(label: ScoreLabel, list: List<UseCaseScore>): String {
        val best = list.maxByOrNull { it.score }
        return when (label) {
            ScoreLabel.EXCELLENT -> "کیفیت اتصال عالی است و برای همه‌ی کاربردها مناسب است."
            ScoreLabel.MEDIUM ->
                if (best != null) "کیفیت اتصال متوسط است؛ برای «${useCaseName(best.useCase)}» از بقیه بهتر است."
                else "کیفیت اتصال متوسط است."
            ScoreLabel.POOR -> "کیفیت اتصال ضعیف است؛ بهتر است اتصال دیگری را امتحان کنید."
        }
    }
}
