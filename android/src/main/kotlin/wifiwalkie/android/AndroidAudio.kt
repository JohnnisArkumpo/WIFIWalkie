package wifiwalkie.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread
import wifiwalkie.core.AudioInput
import wifiwalkie.core.AudioOutput
import wifiwalkie.core.AudioSpec

/**
 * The phone's microphone, tuned for voice (Android applies its echo
 * cancellation and noise suppression). Throws if the permission hasn't been
 * granted or the microphone can't be opened.
 */
class AndroidAudioInput(context: Context) : AudioInput {
    private val record: AudioRecord

    init {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Microphone permission not granted")
        }
        val minBuffer = AudioRecord.getMinBufferSize(AudioSpec.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            AudioSpec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, AudioSpec.FRAME_BYTES * 8),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("The microphone didn't open")
        }
    }

    @Volatile private var reader: Thread? = null

    override fun start(onFrame: (ByteArray) -> Unit) {
        record.startRecording()
        reader = thread(name = "microphone", isDaemon = true) {
            val buffer = ByteArray(AudioSpec.FRAME_BYTES)
            while (reader === Thread.currentThread()) {
                // Blocks until a full frame is read, or returns early once recording stops.
                val read = record.read(buffer, 0, buffer.size)
                if (read < 0) break
                if (read == buffer.size && reader === Thread.currentThread()) onFrame(buffer.copyOf())
            }
        }
    }

    override fun stop() {
        reader = null
        record.stop()
    }

    override fun close() {
        stop()
        record.release()
    }
}

/** The phone's loudspeaker, like a walkie-talkie. The media volume keys control it. */
class AndroidAudioOutput : AudioOutput {
    private val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(AudioSpec.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(
            maxOf(
                AudioTrack.getMinBufferSize(AudioSpec.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
                AudioSpec.FRAME_BYTES * 4,
            ),
        )
        .build()

    override fun start() = track.play()

    override fun write(frame: ByteArray) {
        track.write(frame, 0, frame.size)   // blocks until there's room, which paces playback
    }

    override fun stop() {
        track.pause()
        track.flush()
    }

    override fun close() = track.release()
}
