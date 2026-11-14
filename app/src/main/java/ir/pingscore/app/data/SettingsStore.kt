package ir.pingscore.app.data

import android.content.Context
import ir.pingscore.app.diag.AppConfig
import ir.pingscore.app.diag.Defaults
import ir.pingscore.app.diag.PingTarget
import ir.pingscore.app.diag.SiteDef
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AppSettings(
    /** اجرای خودکار تست سریع هنگام تغییر شبکه. */
    val autoTestOnChange: Boolean = true,
    val backgroundEnabled: Boolean = false,
    val backgroundIntervalMinutes: Int = 60,
    /** true = فقط Wi-Fi (شبکه‌ی نامحدود)، false = هر شبکه. */
    val backgroundWifiOnly: Boolean = true,
    /** تست سرعت در پایش پس‌زمینه (دیتای بیشتر مصرف می‌کند). */
    val backgroundIncludeSpeed: Boolean = false,
    val pingTargets: List<PingTarget> = Defaults.pingTargets,
    val sites: List<SiteDef> = Defaults.sites,
) {
    fun toConfig() = AppConfig(pingTargets = pingTargets, sites = sites)
}

/** تنظیمات در SharedPreferences (یک JSON)؛ فقط روی گوشی. */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): AppSettings {
        val text = prefs.getString(KEY, null) ?: return AppSettings()
        return try {
            json.decodeFromString(AppSettings.serializer(), text)
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun save(settings: AppSettings) {
        prefs.edit().putString(KEY, json.encodeToString(AppSettings.serializer(), settings)).apply()
    }

    private companion object {
        const val KEY = "app_settings_v1"
    }
}
