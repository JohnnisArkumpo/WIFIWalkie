package wifiwalkie.android

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

/** Vibrates for as long as the signal is on. */
class VibrationOutput(context: Context) {
    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator

    fun setOn(on: Boolean) {
        if (on) {
            // Longer than anyone holds a key; cancelled when the signal goes off.
            vibrator.vibrate(VibrationEffect.createOneShot(LONGEST_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.cancel()
        }
    }

    private companion object {
        const val LONGEST_MS = 60_000L
    }
}
