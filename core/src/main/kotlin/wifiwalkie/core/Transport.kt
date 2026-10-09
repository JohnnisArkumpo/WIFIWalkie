package wifiwalkie.core

import java.io.Closeable

/** Carries [SignalMessage]s between this computer and one peer. */
interface Transport : Closeable {
    /** Short name shown to the user, such as "TCP". */
    val name: String

    /** Starts receiving. [onMessage] is called on a background thread. */
    fun start(onMessage: (SignalMessage) -> Unit)

    /** Sends [message], or drops it if the peer can't be reached right now. Must not block for long. */
    fun send(message: SignalMessage)
}
