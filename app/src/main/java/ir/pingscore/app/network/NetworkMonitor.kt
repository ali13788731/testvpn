package ir.pingscore.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import ir.pingscore.app.diag.DnsInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * تشخیص تغییر اتصال پیش‌فرض دستگاه با NetworkCallback.
 * فقط به مجوز ACCESS_NETWORK_STATE نیاز دارد.
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager: ConnectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    val snapshots: Flow<NetworkSnapshot> = callbackFlow {
        // اگر اتصالی نباشد، callback ای فراخوانی نمی‌شود؛ پس وضعیت اولیه را خودمان می‌فرستیم.
        trySend(NetworkSnapshot.DISCONNECTED)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(connectivityManager.getNetworkCapabilities(network).toSnapshot())
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.toSnapshot())
            }

            override fun onLost(network: Network) {
                trySend(NetworkSnapshot.DISCONNECTED)
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    /** وضعیت لحظه‌ای اتصال پیش‌فرض (برای تست‌های پس‌زمینه که Flow ندارند). */
    fun currentSnapshot(): NetworkSnapshot {
        val network = connectivityManager.activeNetwork ?: return NetworkSnapshot.DISCONNECTED
        return connectivityManager.getNetworkCapabilities(network).toSnapshot()
    }

    /** سرورهای DNS اتصال فعلی و شبکه‌ی زیرین؛ فقط با ACCESS_NETWORK_STATE. */
    @Suppress("DEPRECATION")
    fun readDnsInfo(): DnsInfo? {
        val active = connectivityManager.activeNetwork ?: return null
        val caps = connectivityManager.getNetworkCapabilities(active) ?: return null
        val activeServers = connectivityManager.getLinkProperties(active)
            ?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()
        val viaTunnel = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        val underlying = connectivityManager.allNetworks
            .filter { n ->
                val c = connectivityManager.getNetworkCapabilities(n)
                c != null && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
            .flatMap { n -> connectivityManager.getLinkProperties(n)?.dnsServers.orEmpty() }
            .mapNotNull { it.hostAddress }
            .distinct()
        return DnsInfo(activeServers, underlying, viaTunnel)
    }

    private fun NetworkCapabilities?.toSnapshot(): NetworkSnapshot {
        if (this == null) return NetworkSnapshot.DISCONNECTED

        val transport = when {
            hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TransportType.WIFI
            hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TransportType.CELLULAR
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> TransportType.ETHERNET
            else -> TransportType.UNKNOWN
        }

        return NetworkSnapshot(
            isConnected = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            isValidated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            transport = transport,
            viaTunnel = hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            isMetered = !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            downstreamKbps = linkDownstreamBandwidthKbps,
            upstreamKbps = linkUpstreamBandwidthKbps,
        )
    }
}
