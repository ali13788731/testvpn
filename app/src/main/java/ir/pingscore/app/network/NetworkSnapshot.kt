package ir.pingscore.app.network

/** نوع شبکه‌ی پایه‌ی اتصال فعلی. */
enum class TransportType { WIFI, CELLULAR, ETHERNET, UNKNOWN }

/** عکس لحظه‌ای از وضعیت اتصال پیش‌فرض دستگاه. */
data class NetworkSnapshot(
    val isConnected: Boolean,
    /** آیا اندروید اتصال را «دارای اینترنت واقعی» تأیید کرده است (NET_CAPABILITY_VALIDATED). */
    val isValidated: Boolean,
    val transport: TransportType,
    /** آیا مسیر اتصال از یک تونل/شبکه‌ی مجازی عبور می‌کند. */
    val viaTunnel: Boolean,
    val isMetered: Boolean,
    /** تخمین پهنای باند دانلود از طرف سیستم (Kbps). فقط یک برآورد است نه سرعت واقعی. */
    val downstreamKbps: Int,
    val upstreamKbps: Int,
) {
    /**
     * امضای اتصال: فقط تغییرهای معنادار (نه نوسان پهنای باند) را رویداد حساب می‌کنیم.
     */
    val signature: String
        get() = "$isConnected|$isValidated|$transport|$viaTunnel"

    companion object {
        val DISCONNECTED = NetworkSnapshot(
            isConnected = false,
            isValidated = false,
            transport = TransportType.UNKNOWN,
            viaTunnel = false,
            isMetered = false,
            downstreamKbps = 0,
            upstreamKbps = 0,
        )
    }
}
