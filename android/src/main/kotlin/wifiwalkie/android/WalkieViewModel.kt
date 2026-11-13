package wifiwalkie.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wifiwalkie.core.Session
import wifiwalkie.core.TcpTransport
import wifiwalkie.core.UdpTransport

/**
 * Owns the connection, so it survives screen rotation. The screens only read
 * these states and call these methods.
 */
class WalkieViewModel(application: Application) : AndroidViewModel(application), Session.Listener {

    /** Everything the screens show. */
    data class State(
        val connected: Boolean = false,       // between Connect and Disconnect
        val peerConnected: Boolean = false,
        val peerOn: Boolean = false,
        val localOn: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val vibration = VibrationOutput(application)

    @Volatile private var session: Session? = null
    private var connecting = false

    /** This phone's IP address, for the Connect screen. */
    fun localIp(): String? = AndroidNetworkInfo.localIp(getApplication())

    fun connect(peerIp: String, useTcp: Boolean) {
        if (session != null || connecting) return
        connecting = true
        // Looking up the address and opening the port are network work, which Android forbids on the main thread.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val peer = InetAddress.getByName(peerIp.trim())
                val transport = if (useTcp) TcpTransport(peer, PORT, PORT) else UdpTransport(peer, PORT, PORT)
                // Show the signal screen before starting, so an instant reply from the peer isn't overwritten.
                _state.value = State(connected = true)
                session = Session(transport, this@WalkieViewModel).also { it.start() }
            } catch (e: UnknownHostException) {
                _state.value = State(error = "Couldn't find \"$peerIp\". Enter an address like 192.168.1.20.")
            } catch (e: IOException) {
                _state.value = State(error = "Couldn't open port $PORT: ${e.message}")
            } finally {
                launch(Dispatchers.Main) { connecting = false }
            }
        }
    }

    fun setLocalSignal(on: Boolean) {
        _state.update { it.copy(localOn = on) }
        session?.setLocalSignal(on)
    }

    fun disconnect() {
        session?.close()
        session = null
        vibration.setOn(false)
        _state.value = State()
    }

    override fun onPeerConnectedChanged(peerId: Int, connected: Boolean) {
        _state.update { it.copy(peerConnected = connected) }
    }

    override fun onPeerSignalChanged(peerId: Int, on: Boolean) {
        _state.update { it.copy(peerOn = on) }
        vibration.setOn(on)
    }

    override fun onCleared() = disconnect()

    private companion object {
        /** The same port as the desktop program, on both sides. */
        const val PORT = 5000
    }
}
