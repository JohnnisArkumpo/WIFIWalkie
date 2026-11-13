package wifiwalkie.core

/** The one audio format used everywhere: 16 kHz, mono, 16-bit signed little-endian PCM, in 20 ms frames. */
object AudioSpec {
    const val SAMPLE_RATE = 16_000
    const val CHANNELS = 1
    const val BYTES_PER_SAMPLE = 2
    const val FRAME_MS = 20
    const val SAMPLES_PER_FRAME = SAMPLE_RATE * FRAME_MS / 1000
    const val FRAME_BYTES = SAMPLES_PER_FRAME * BYTES_PER_SAMPLE * CHANNELS
}
