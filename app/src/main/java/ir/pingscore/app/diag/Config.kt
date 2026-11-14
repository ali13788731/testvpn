package ir.pingscore.app.diag

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** یک مقصد تست سرعت. اگر [uploadUrl] خالی باشد آپلود روی آن انجام نمی‌شود. */
data class SpeedTarget(
    val label: String,
    val downloadUrl: String,
    val uploadUrl: String? = null,
)

/** پارامترهای هر حالت تست. */
data class ModeConfig(
    val pingTargetsMax: Int,
    val pingSamples: Int,
    val downloadTargetsMax: Int,
    val uploadTargetsMax: Int,
    val connections: Int,
    val durationMs: Long,
    val capBytesPerTarget: Long,
    val dnsDomainsMax: Int,
) {
    companion object {
        fun of(mode: TestMode): ModeConfig = when (mode) {
            TestMode.QUICK -> ModeConfig(
                pingTargetsMax = 2, pingSamples = 5,
                downloadTargetsMax = 2, uploadTargetsMax = 1,
                connections = 2, durationMs = 3_000L,
                capBytesPerTarget = 20L * 1024 * 1024,
                dnsDomainsMax = 4,
            )
            TestMode.FULL -> ModeConfig(
                pingTargetsMax = 4, pingSamples = 5,
                downloadTargetsMax = 3, uploadTargetsMax = 2,
                connections = 4, durationMs = 5_000L,
                capBytesPerTarget = 60L * 1024 * 1024,
                dnsDomainsMax = 6,
            )
        }
    }
}

/** پیش‌فرض‌ها. آدرس‌ها را می‌شود در تنظیمات (پینگ و سایت‌ها) یا همین‌جا (سرعت) عوض کرد. */
object Defaults {

    val pingTargets: List<PingTarget> = listOf(
        PingTarget("Cloudflare", host = "1.1.1.1", port = 443),
        PingTarget("Google", host = "8.8.8.8", port = 443),
        PingTarget("Quad9", host = "9.9.9.9", port = 443),
        PingTarget("Cloudflare HTTP", url = "https://cp.cloudflare.com/generate_204"),
    )

    /**
     * مقصدهای سرعت. آپلود فقط روی Cloudflare تعریف شده چون CDN های دیگر معمولاً
     * نقطه‌ی آپلود عمومی ندارند. اگر مقصد آپلود دیگری دارید، uploadUrl را پر کنید.
     */
    val speedTargets: List<SpeedTarget> = listOf(
        SpeedTarget(
            label = "Cloudflare",
            downloadUrl = "https://speed.cloudflare.com/__down?bytes=25000000",
            uploadUrl = "https://speed.cloudflare.com/__up",
        ),
        SpeedTarget(label = "CacheFly", downloadUrl = "https://cachefly.cachefly.net/100mb.test"),
        SpeedTarget(label = "OVH", downloadUrl = "https://proof.ovh.net/files/100Mb.dat"),
    )

    val dnsDomains: List<String> = listOf(
        "google.com", "github.com", "openai.com", "youtube.com", "instagram.com", "cloudflare.com",
    )

    val sites: List<SiteDef> = listOf(
        SiteDef("OpenAI", SiteCategory.AI, "https://api.openai.com/v1/models"),
        SiteDef("Claude", SiteCategory.AI, "https://api.anthropic.com/v1/models"),
        SiteDef("Gemini", SiteCategory.AI, "https://generativelanguage.googleapis.com/"),
        SiteDef("Hugging Face", SiteCategory.AI, "https://huggingface.co/"),
        SiteDef("GitHub", SiteCategory.DEV, "https://github.com/"),
        SiteDef("npm", SiteCategory.DEV, "https://registry.npmjs.org/"),
        SiteDef("Telegram", SiteCategory.CHAT, "https://api.telegram.org/"),
        SiteDef("WhatsApp", SiteCategory.CHAT, "https://web.whatsapp.com/"),
        SiteDef("Discord", SiteCategory.CHAT, "https://discord.com/api/v10/gateway"),
        SiteDef("Instagram", SiteCategory.SOCIAL, "https://www.instagram.com/"),
        SiteDef("X", SiteCategory.SOCIAL, "https://x.com/"),
        SiteDef("YouTube", SiteCategory.VIDEO, "https://www.youtube.com/generate_204"),
        SiteDef("Netflix", SiteCategory.VIDEO, "https://www.netflix.com/"),
        SiteDef("Twitch", SiteCategory.VIDEO, "https://www.twitch.tv/"),
        SiteDef("Google", SiteCategory.OTHER, "https://www.google.com/generate_204"),
    )
}

/** یک OkHttpClient مشترک؛ کلاینت‌های هر تست با newBuilder از آن مشتق می‌شوند. */
object Http {
    val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .build()
    }
}
