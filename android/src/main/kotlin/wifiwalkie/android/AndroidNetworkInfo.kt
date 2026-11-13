package wifiwalkie.android

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address

/** What the Connect screen shows about this phone's network. */
object AndroidNetworkInfo {
    /** This phone's IPv4 address on its current network, or null if it has none. */
    fun localIp(context: Context): String? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val link = connectivity.getLinkProperties(connectivity.activeNetwork) ?: return null
        return link.linkAddresses
            .map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }
}
