package wifiwalkie.core

import java.io.Closeable

/** A speaker. Desktop and Android each supply their own. */
interface AudioOutput : Closeable {
    fun start()

    /** Plays one frame. Blocks until the speaker has room, which paces playback. */
    fun write(frame: ByteArray)

    /** Stops and discards anything not yet played. */
    fun stop()
}
