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

    private fun checkSignal(makeTransport: (InetAddress, Int, Int) -> Transport) {
        val localhost = InetAddress.getLoopbackAddress()
        val portA = freePort()
        val portB = freePort()
        val eventsAtB = LinkedBlockingQueue<String>()

        val a = Session(makeTransport(localhost, portB, portA), RecordingListener(LinkedBlockingQueue()))
        val b = Session(makeTransport(localhost, portA, portB), RecordingListener(eventsAtB))
        a.use { b.use {
            a.start()
            b.start()
            assertEquals("connected", eventsAtB.next())

            a.setLocalSignal(true)
            assertEquals("on", eventsAtB.next())
            a.setLocalSignal(false)
            assertEquals("off", eventsAtB.next())
        } }
    }

    private fun LinkedBlockingQueue<String>.next() = poll(3, TimeUnit.SECONDS) ?: "nothing within 3 s"

    private fun freePort() = ServerSocket(0).use { it.localPort }

    private class RecordingListener(private val events: LinkedBlockingQueue<String>) : Session.Listener {
        override fun onPeerConnectedChanged(connected: Boolean) {
            events.add(if (connected) "connected" else "disconnected")
        }

        override fun onPeerSignalChanged(on: Boolean) {
            events.add(if (on) "on" else "off")
        }
    }
}
