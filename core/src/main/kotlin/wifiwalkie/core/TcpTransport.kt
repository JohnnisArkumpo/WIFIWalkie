package wifiwalkie.core

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
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

    override fun start(onMessage: (Message) -> Unit) {
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

    private fun receive(socket: Socket, onMessage: (Message) -> Unit) {
        socket.use {
            val input = DataInputStream(BufferedInputStream(it.getInputStream()))
            val bytes = ByteArray(Message.MAX_SIZE)
            try {
                while (!closed) {
                    // A bad length or message means we've lost track of where messages start, so drop the connection.
                    val length = input.readUnsignedShort()
                    if (length > Message.MAX_SIZE) return
                    input.readFully(bytes, 0, length)
                    val message = Message.decode(bytes, length) ?: return
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
            // Send each message right away instead of waiting to batch them.
            socket.tcpNoDelay = true
            outgoing = socket
        } catch (e: IOException) {
            socket.close()
        }
    }

    // Synchronized so messages from different threads (audio, heartbeat) can't interleave on the stream.
    @Synchronized
    override fun send(message: Message) {
        val socket = outgoing ?: return
        val bytes = message.encode()
        // Each message is preceded by its 2-byte length, written together so it goes out in one piece.
        val framed = ByteBuffer.allocate(2 + bytes.size).putShort(bytes.size.toShort()).put(bytes).array()
        try {
            socket.getOutputStream().write(framed)
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
