package wifiwalkie.core

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/** Runs two sessions against each other on this computer. */
class SessionTest {

    @Test
    fun `signal goes on and off over UDP`() = checkSignal(::UdpTransport)

    @Test
    fun `signal goes on and off over TCP`() = checkSignal(::TcpTransport)

    @Test
    fun `audio arrives over UDP`() = checkAudio(::UdpTransport)

    @Test
    fun `audio arrives over TCP`() = checkAudio(::TcpTransport)

    private fun checkSignal(makeTransport: (InetAddress, Int, Int) -> Transport) = withPair(makeTransport) { a, eventsAtB ->
        a.setLocalSignal(true)
        assertEquals("on ${a.sessionId}", eventsAtB.next())
        a.setLocalSignal(false)
        assertEquals("off ${a.sessionId}", eventsAtB.next())
    }

    private fun checkAudio(makeTransport: (InetAddress, Int, Int) -> Transport) = withPair(makeTransport) { a, eventsAtB ->
        a.sendAudio(ByteArray(640) { 1 }, startOfTransmission = true)
        a.sendAudio(ByteArray(640) { 2 }, startOfTransmission = false)
        // UDP on loopback keeps order in practice, and the session sends from one thread.
        assertEquals("audio ${a.sessionId} #1 start 640x1", eventsAtB.next())
        assertEquals("audio ${a.sessionId} #2 cont 640x2", eventsAtB.next())
    }

    @Test
    fun `a restarted peer is reported as a disconnect then a connect`() {
        val localhost = InetAddress.getLoopbackAddress()
        val portA = freePort()
        val portB = freePort()
        val eventsAtB = LinkedBlockingQueue<String>()

        Session(UdpTransport(localhost, portA, portB), RecordingListener(eventsAtB)).use { b ->
            b.start()
            val first = Session(UdpTransport(localhost, portB, portA), RecordingListener(LinkedBlockingQueue()))
            first.use {
                first.start()
                assertEquals("connected ${first.sessionId}", eventsAtB.next())
                first.setLocalSignal(true)
                assertEquals("on ${first.sessionId}", eventsAtB.next())
            }
            Session(UdpTransport(localhost, portB, portA), RecordingListener(LinkedBlockingQueue())).use { second ->
                second.start()
                assertEquals("disconnected ${first.sessionId}", eventsAtB.next())
                assertEquals("off ${first.sessionId}", eventsAtB.next())
                assertEquals("connected ${second.sessionId}", eventsAtB.next())
            }
        }
    }

    /** Starts sessions a and b, waits for b to see a connect, then runs [check] with b's events. */
    private fun withPair(
        makeTransport: (InetAddress, Int, Int) -> Transport,
        check: (Session, LinkedBlockingQueue<String>) -> Unit,
    ) {
        val localhost = InetAddress.getLoopbackAddress()
        val portA = freePort()
        val portB = freePort()
        val eventsAtB = LinkedBlockingQueue<String>()

        val a = Session(makeTransport(localhost, portB, portA), RecordingListener(LinkedBlockingQueue()))
        val b = Session(makeTransport(localhost, portA, portB), RecordingListener(eventsAtB))
        a.use { b.use {
            a.start()
            b.start()
            assertEquals("connected ${a.sessionId}", eventsAtB.next())
            check(a, eventsAtB)
        } }
    }

    private fun LinkedBlockingQueue<String>.next() = poll(3, TimeUnit.SECONDS) ?: "nothing within 3 s"

    private fun freePort() = ServerSocket(0).use { it.localPort }

    private class RecordingListener(private val events: LinkedBlockingQueue<String>) : Session.Listener {
        override fun onPeerConnectedChanged(peerId: Int, connected: Boolean) {
            events.add("${if (connected) "connected" else "disconnected"} $peerId")
        }

        override fun onPeerSignalChanged(peerId: Int, on: Boolean) {
            events.add("${if (on) "on" else "off"} $peerId")
        }

        override fun onPeerAudio(peerId: Int, sequence: Int, startOfTransmission: Boolean, pcm: ByteArray) {
            val fill = pcm.distinct().singleOrNull() ?: "mixed"
            events.add("audio $peerId #$sequence ${if (startOfTransmission) "start" else "cont"} ${pcm.size}x$fill")
        }
    }
}
