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
import wifiwalkie.core.AudioPlayback
import wifiwalkie.core.PlayoutBuffer
import wifiwalkie.core.PushToTalk
import wifiwalkie.core.Session
import wifiwalkie.core.TcpTransport
import wifiwalkie.core.UdpTransport

/**
 * Owns the connection and the audio, so they survive screen rotation. The
 * screens only read these states and call these methods.
 */
class WalkieViewModel(application: Application) : AndroidViewModel(application), Session.Listener {

    /** Everything the screens show. */
    data class State(
        val connected: Boolean = false,       // between Connect and Disconnect
        val peerConnected: Boolean = false,
        val peerOn: Boolean = false,
        val peerTalking: Boolean = false,     // the other person's voice is playing
        val localOn: Boolean = false,
        val talking: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val vibration = VibrationOutput(application)

    @Volatile private var session: Session? = null
    @Volatile private var buffer: PlayoutBuffer? = null
    @Volatile private var playback: AudioPlayback? = null
    private var pushToTalk: PushToTalk? = null
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
                startPlayback()
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

    /** Received audio goes through a playout buffer to the loudspeaker. Without a speaker, Morse still works. */
    private fun startPlayback() {
        val newBuffer = PlayoutBuffer { talking -> _state.update { it.copy(peerTalking = talking) } }
        try {
            playback = AudioPlayback(AndroidAudioOutput(), newBuffer).also { it.start() }
            buffer = newBuffer
        } catch (e: Exception) {
            _state.update { it.copy(error = "Couldn't open the speaker: ${e.message}") }
        }
    }

    fun setLocalSignal(on: Boolean) {
        _state.update { it.copy(localOn = on) }
        session?.setLocalSignal(on)
    }

    /** Push-to-talk. The screen asks for the microphone permission before the first call. */
    fun setTalking(on: Boolean) {
        val session = session ?: return
        if (on && pushToTalk == null) {
            pushToTalk = try {
                PushToTalk(AndroidAudioInput(getApplication()), session::sendAudio)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Couldn't open the microphone: ${e.message}") }
                return
            }
        }
        val talk = pushToTalk ?: return
        talk.setTalking(on)
        _state.update { it.copy(talking = on) }
    }

    fun disconnect() {
        pushToTalk?.close()
        pushToTalk = null
        session?.close()
        session = null
        playback?.close()
        playback = null
        buffer = null
        vibration.setOn(false)
        _state.value = State()
    }

    override fun onPeerConnectedChanged(peerId: Int, connected: Boolean) {
        buffer?.reset()
        _state.update { it.copy(peerConnected = connected) }
    }

    override fun onPeerSignalChanged(peerId: Int, on: Boolean) {
        _state.update { it.copy(peerOn = on) }
        vibration.setOn(on)
    }

    override fun onPeerAudio(peerId: Int, sequence: Int, startOfTransmission: Boolean, pcm: ByteArray) {
        // The same clock AudioPlayback uses.
        buffer?.push(sequence, startOfTransmission, pcm, System.nanoTime() / 1_000_000)
    }

    override fun onCleared() = disconnect()

    private companion object {
        /** The same port as the desktop program, on both sides. */
        const val PORT = 5000
    }
}
