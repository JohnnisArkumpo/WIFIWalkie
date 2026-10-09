package wifiwalkie.core

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Uses two one-way TCP connections: one this computer opens to send, and one
 * the peer opens to send back. Both sides listen and both sides connect, so
 * neither has to be the "host", and either can start first.
 *
 * If the outgoing connection drops, it's reopened every [RETRY_MS] until it works.
 */
class TcpTransport(
    private val peer: InetAddress,
    private val peerPort: Int,
    localPort: Int,
) : Transport {
    override val name = "TCP"

    private val server = ServerSocket(localPort)

    @Volatile private var closed = false
    @Volatile private var outgoing: Socket? = null

    override fun start(onMessage: (SignalMessage) -> Unit) {
        thread(name = "tcp-accept", isDaemon = true) {
            while (!closed) {
                val socket = try {
                    server.accept()
                } catch (e: IOException) {
                    if (!closed) System.err.println("TCP accept failed: ${e.message}")
                    continue
                }
                thread(name = "tcp-receive", isDaemon = true) { receive(socket, onMessage) }
            }
        }
        thread(name = "tcp-connect", isDaemon = true) {
            while (!closed) {
                if (outgoing == null) connect()
                Thread.sleep(RETRY_MS)
            }
        }
    }

    private fun receive(socket: Socket, onMessage: (SignalMessage) -> Unit) {
        socket.use {
            val input = DataInputStream(BufferedInputStream(it.getInputStream()))
            val bytes = ByteArray(SignalMessage.SIZE)
            try {
                while (!closed) {
                    input.readFully(bytes)
                    // A bad message means we've lost track of where messages start, so drop the connection.
                    val message = SignalMessage.decode(bytes) ?: return
                    onMessage(message)
                }
            } catch (e: IOException) {
                // The peer closed the connection or the network dropped it.
            }
        }
    }

    private fun connect() {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(peer, peerPort), CONNECT_TIMEOUT_MS)
            // Send each 10-byte message right away instead of waiting to batch them.
            socket.tcpNoDelay = true
            outgoing = socket
        } catch (e: IOException) {
            socket.close()
        }
    }

    override fun send(message: SignalMessage) {
        val socket = outgoing ?: return
        try {
            socket.getOutputStream().write(message.encode())
        } catch (e: IOException) {
            socket.close()
            outgoing = null
        }
    }

    override fun close() {
        closed = true
        server.close()
        outgoing?.close()
    }

    private companion object {
        const val RETRY_MS = 500L
        const val CONNECT_TIMEOUT_MS = 1000
    }
}
