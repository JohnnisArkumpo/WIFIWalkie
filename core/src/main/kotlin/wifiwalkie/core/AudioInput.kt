package wifiwalkie.core

import java.io.Closeable

/** A microphone. Desktop and Android each supply their own. */
interface AudioInput : Closeable {
    /** Starts capturing. [onFrame] gets one [AudioSpec.FRAME_BYTES] frame every 20 ms, on a background thread. */
    fun start(onFrame: (ByteArray) -> Unit)

    /** Stops capturing. [start] may be called again later. */
    fun stop()
}
