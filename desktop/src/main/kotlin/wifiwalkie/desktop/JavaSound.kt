package wifiwalkie.desktop

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread
import wifiwalkie.core.AudioInput
import wifiwalkie.core.AudioOutput
import wifiwalkie.core.AudioSpec

/** [AudioSpec] as a Java Sound format: signed, little-endian. */
private val FORMAT = AudioFormat(
    AudioSpec.SAMPLE_RATE.toFloat(), AudioSpec.BYTES_PER_SAMPLE * 8, AudioSpec.CHANNELS, true, false,
)

/** The default microphone. Throws if there isn't one or it can't be opened. */
class JavaSoundInput : AudioInput {
    private val line: TargetDataLine = AudioSystem.getTargetDataLine(FORMAT).apply {
        open(FORMAT, AudioSpec.FRAME_BYTES * 8)
    }

    @Volatile private var reader: Thread? = null

    override fun start(onFrame: (ByteArray) -> Unit) {
        line.flush()
        line.start()
        reader = thread(name = "microphone", isDaemon = true) {
            val buffer = ByteArray(AudioSpec.FRAME_BYTES)
            while (reader === Thread.currentThread()) {
                // Blocks until a full frame is read, or returns early once the line is stopped.
                val read = line.read(buffer, 0, buffer.size)
                if (read == buffer.size && reader === Thread.currentThread()) onFrame(buffer.copyOf())
            }
        }
    }

    override fun stop() {
        reader = null
        line.stop()
        line.flush()
    }

    override fun close() {
        stop()
        line.close()
    }
}

/** The default speaker. Throws if there isn't one or it can't be opened. */
class JavaSoundOutput : AudioOutput {
    // A small buffer keeps delay low; much smaller and Windows starts to crackle.
    private val line: SourceDataLine = AudioSystem.getSourceDataLine(FORMAT).apply {
        open(FORMAT, AudioSpec.FRAME_BYTES * 6)
    }

    override fun start() = line.start()

    override fun write(frame: ByteArray) {
        line.write(frame, 0, frame.size)
    }

    override fun stop() {
        line.stop()
        line.flush()
    }

    override fun close() = line.close()
}
