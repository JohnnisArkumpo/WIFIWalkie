package wifiwalkie.desktop

import java.awt.GraphicsEnvironment
import java.net.BindException
import java.net.InetAddress
import java.net.UnknownHostException
import javax.swing.SwingUtilities
import kotlin.system.exitProcess
import wifiwalkie.core.Session
import wifiwalkie.core.TcpTransport
import wifiwalkie.core.UdpTransport

const val DEFAULT_PORT = 5000

const val USAGE = """Usage: wifi-walkie [--port N] [--peer-port N]
  --port N       Port this computer listens on (default $DEFAULT_PORT)
  --peer-port N  Port the other computer listens on (default $DEFAULT_PORT)

To try it on one computer, run two copies with swapped ports and use 127.0.0.1:
  wifi-walkie --port 5000 --peer-port 5001
  wifi-walkie --port 5001 --peer-port 5000"""

fun main(args: Array<String>) {
    var port = DEFAULT_PORT
    var peerPort = DEFAULT_PORT
    val options = args.iterator()
    for (option in options) {
        val value = if (options.hasNext()) options.next().toIntOrNull() else null
        when {
            value == null || value !in 1..65535 -> fail(USAGE)
            option == "--port" -> port = value
            option == "--peer-port" -> peerPort = value
            else -> fail(USAGE)
        }
    }
    if (GraphicsEnvironment.isHeadless()) fail("This program needs a desktop to open its window.")

    println("Wi-fi Walkie: Morse test")
    println("Network:  ${NetworkInfo.networkName() ?: "unknown"}")
    println("Local IP: ${NetworkInfo.localIp() ?: "unknown"}")
    val all = NetworkInfo.allIpv4()
    if (all.size > 1) println("          (all addresses: ${all.joinToString(", ")})")
    println()

    val peer = promptPeer()
    val useTcp = promptProtocol()

    val transport = try {
        if (useTcp) TcpTransport(peer, peerPort, port) else UdpTransport(peer, peerPort, port)
    } catch (e: BindException) {
        fail("Port $port is already in use. Is another copy running? Pick another with --port.\n\n$USAGE")
    }

    println()
    println("Sending to ${peer.hostAddress}:$peerPort over ${transport.name}, listening on port $port.")
    println("Click the window and hold SPACE. Close the window or press Ctrl+C to stop.")

    SwingUtilities.invokeLater {
        val window = SignalWindow("Wi-fi Walkie: ${transport.name} to ${peer.hostAddress}")
        val session = Session(transport, window)
        window.onLocalSignal = session::setLocalSignal
        window.show()
        session.start()
    }
}

private fun promptPeer(): InetAddress {
    while (true) {
        val text = prompt("Other computer's IP: ")
        if (text.isEmpty()) continue
        try {
            return InetAddress.getByName(text)
        } catch (e: UnknownHostException) {
            println("Couldn't find \"$text\". Enter an address like 192.168.1.20.")
        }
    }
}

/** Returns true for TCP, false for UDP. */
private fun promptProtocol(): Boolean {
    while (true) {
        when (prompt("Protocol (TCP or UDP): ").lowercase()) {
            "tcp", "t" -> return true
            "udp", "u" -> return false
            else -> println("Type TCP or UDP.")
        }
    }
}

private fun prompt(message: String): String {
    print(message)
    System.out.flush()
    return readlnOrNull()?.trim() ?: exitProcess(0)
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}
