package wifiwalkie.desktop

import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

/** Best-effort lookups of this computer's network details. Each returns null when it can't tell. */
object NetworkInfo {

    /** The address the other computer should type in to reach this one. */
    fun localIp(): String? {
        // Connecting a UDP socket doesn't send anything. It only asks the OS which
        // local address it would use for outside traffic, which skips VPNs and Docker.
        try {
            DatagramSocket().use { socket ->
                socket.connect(InetAddress.getByName("8.8.8.8"), 53)
                val address = socket.localAddress
                if (address is Inet4Address && !address.isAnyLocalAddress && !address.isLoopbackAddress) {
                    return address.hostAddress
                }
            }
        } catch (e: Exception) {
            // No route to the internet. Fall back to listing the network adapters.
        }
        return allIpv4().firstOrNull()?.substringBefore(' ')
    }

    /** Every IPv4 address on this computer, like "192.168.1.20 (wlan0)". */
    fun allIpv4(): List<String> =
        NetworkInterface.networkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { adapter ->
                adapter.inetAddresses().toList()
                    .filterIsInstance<Inet4Address>()
                    .map { "${it.hostAddress} (${adapter.displayName})" }
            }

    /** The Wi-Fi network name, or the wired connection's name if there's no Wi-Fi. */
    fun networkName(): String? {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("win") -> field(run("netsh", "wlan", "show", "interfaces"), "SSID")
            os.contains("mac") -> field(run("ipconfig", "getsummary", "en0"), "SSID")
                ?: run("networksetup", "-getairportnetwork", "en0")
                    ?.substringAfter("Current Wi-Fi Network: ", "")?.trim()?.ifEmpty { null }
            else -> linuxNetworkName()
        }
    }

    private fun linuxNetworkName(): String? {
        val wifi = run("nmcli", "-t", "-f", "ACTIVE,SSID", "device", "wifi", "list", "--rescan", "no")
            ?.lines()?.firstOrNull { it.startsWith("yes:") }?.removePrefix("yes:")
        if (!wifi.isNullOrBlank()) return wifi
        return run("nmcli", "-t", "-f", "NAME,TYPE", "connection", "show", "--active")
            ?.lines()?.firstOrNull { it.isNotBlank() && !it.endsWith(":loopback") }?.substringBeforeLast(':')
            ?: run("iwgetid", "-r")?.trim()?.ifEmpty { null }
    }

    /** Finds a "key : value" line in [output] and returns the value. */
    private fun field(output: String?, key: String): String? =
        output?.lines()
            ?.firstOrNull { it.substringBefore(':').trim() == key }
            ?.substringAfter(':')?.trim()
            ?.ifEmpty { null }

    /** Runs a command and returns what it printed, or null if it failed or isn't installed. */
    private fun run(vararg command: String): String? = try {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        if (process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0) {
            process.inputStream.bufferedReader().readText()
        } else {
            process.destroy()
            null
        }
    } catch (e: Exception) {
        null
    }
}
