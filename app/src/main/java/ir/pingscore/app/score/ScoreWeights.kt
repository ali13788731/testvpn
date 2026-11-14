package ir.pingscore.app.score

import ir.pingscore.app.diag.SiteCategory
import ir.pingscore.app.diag.SiteStatus

/**
 * ===== همه‌ی وزن‌ها و آستانه‌های نمره‌دهی فقط در این فایل هستند. =====
 * برای تنظیم، فقط مقادیر [ScoreConfig.Default] را عوض کنید.
 */

/** وزن سه مؤلفه برای یک کاربرد؛ جمع لازم نیست ۱ باشد (نرمال می‌شود). */
data class ComponentWeights(
    /** دسترسی سایت‌ها و پایداری (DNS) */
    val access: Double,
    /** سرعت و packet loss */
    val speedLoss: Double,
    /** ping و jitter */
    val latency: Double,
)

data class UseCaseProfile(
    val weights: ComponentWeights,
    /** سایت‌هایی که برای این کاربرد مهم‌اند. */
    val siteCategories: Set<SiteCategory>,
    // --- سرعت ---
    val downloadTargetMbps: Double,
    val uploadTargetMbps: Double,
    /** سهم دانلود در نمره‌ی سرعت (باقی‌مانده سهم آپلود). */
    val downloadShare: Double,
    /** سهم سرعت در مؤلفه‌ی «سرعت و packet loss» (باقی‌مانده سهم loss). */
    val speedShare: Double,
    /** درصد packet loss که در آن نمره‌ی loss صفر می‌شود. */
    val lossZeroAtPercent: Double,
    // --- تأخیر ---
    val pingGoodMs: Double,
    val pingBadMs: Double,
    val jitterGoodMs: Double,
    val jitterBadMs: Double,
    /** سهم ping در مؤلفه‌ی «ping و jitter» (باقی‌مانده سهم jitter). */
    val pingShare: Double,
)

data class ScoreConfig(
    val profiles: Map<UseCase, UseCaseProfile>,
    /** حداقل نمره برای «عالی». */
    val excellentMin: Int,
    /** حداقل نمره برای «متوسط». */
    val mediumMin: Int,
    /** امتیاز هر وضعیت سایت (۰ تا ۱). */
    val sitePoints: Map<SiteStatus, Double>,
    /** جریمه‌ها (از ۱۰۰ نمره‌ی دسترسی کم می‌شود). */
    val dnsSuspiciousPenalty: Double,
    val dnsLeakPenalty: Double,
    val dnsFailedPenaltyMax: Double,
) {
    companion object {
        val Default = ScoreConfig(
            profiles = mapOf(
                UseCase.AI to UseCaseProfile(
                    weights = ComponentWeights(access = 0.60, speedLoss = 0.15, latency = 0.25),
                    siteCategories = setOf(SiteCategory.AI, SiteCategory.DEV),
                    downloadTargetMbps = 5.0, uploadTargetMbps = 2.0,
                    downloadShare = 0.7, speedShare = 0.6, lossZeroAtPercent = 15.0,
                    pingGoodMs = 100.0, pingBadMs = 600.0,
                    jitterGoodMs = 20.0, jitterBadMs = 150.0, pingShare = 0.6,
                ),
                UseCase.VIDEO to UseCaseProfile(
                    weights = ComponentWeights(access = 0.20, speedLoss = 0.60, latency = 0.20),
                    siteCategories = setOf(SiteCategory.VIDEO),
                    downloadTargetMbps = 25.0, uploadTargetMbps = 5.0,
                    downloadShare = 0.9, speedShare = 0.75, lossZeroAtPercent = 10.0,
                    pingGoodMs = 100.0, pingBadMs = 500.0,
                    jitterGoodMs = 25.0, jitterBadMs = 150.0, pingShare = 0.6,
                ),
                UseCase.CHAT to UseCaseProfile(
                    weights = ComponentWeights(access = 0.20, speedLoss = 0.20, latency = 0.60),
                    siteCategories = setOf(SiteCategory.CHAT, SiteCategory.SOCIAL),
                    downloadTargetMbps = 3.0, uploadTargetMbps = 2.0,
                    downloadShare = 0.5, speedShare = 0.4, lossZeroAtPercent = 5.0,
                    pingGoodMs = 60.0, pingBadMs = 300.0,
                    jitterGoodMs = 8.0, jitterBadMs = 60.0, pingShare = 0.5,
                ),
            ),
            excellentMin = 75,
            mediumMin = 50,
            sitePoints = mapOf(
                SiteStatus.OPEN to 1.0,
                SiteStatus.SLOW to 0.5,
                SiteStatus.IP_BLOCKED to 0.0,
                SiteStatus.FAILED to 0.0,
            ),
            dnsSuspiciousPenalty = 15.0,
            dnsLeakPenalty = 10.0,
            dnsFailedPenaltyMax = 20.0,
        )
    }
}
