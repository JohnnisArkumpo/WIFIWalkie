package wifiwalkie.core

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

/**
 * Sends each message as one UDP packet to [peer]:[peerPort] and receives on [localPort].
 *
 * There's no connection to set up: packets either arrive or they don't, and
 * they can arrive out of order. [Session] handles both.
 */
class UdpTransport(
    private val peer: InetAddress,
    private val peerPort: Int,
    localPort: Int,
) : Transport {
    override val name = "UDP"

    private val socket = DatagramSocket(localPort)

    override fun start(onMessage: (SignalMessage) -> Unit) {
        thread(name = "udp-receive", isDaemon = true) {
            val buffer = ByteArray(64)
            val packet = DatagramPacket(buffer, buffer.size)
            while (!socket.isClosed) {
                try {
                    packet.length = buffer.size
                    socket.receive(packet)
                    SignalMessage.decode(buffer, packet.length)?.let(onMessage)
                } catch (e: IOException) {
                    if (!socket.isClosed) System.err.println("UDP receive failed: ${e.message}")
                }
            }
        }
    }

    override fun send(message: SignalMessage) {
        val bytes = message.encode()
        try {
            socket.send(DatagramPacket(bytes, bytes.size, peer, peerPort))
        } catch (e: IOException) {
            // Usually means this computer has no network right now. The next heartbeat tries again.
        }
    }

    override fun close() = socket.close()
}
