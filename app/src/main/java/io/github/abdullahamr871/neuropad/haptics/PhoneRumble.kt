package io.github.abdullahamr871.neuropad.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.github.abdullahamr871.neuropad.core.PhoneRumbleLevel

/**
 * Plays controller rumble on the phone's own vibration motor, for gamepads without rumble
 * motors or for players who want to feel it in their hands on the phone as well.
 *
 * A game's rumble can last any length, so every level is started as a long one-shot and
 * replaced or cancelled when the game changes it.
 */
class PhoneRumble(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val lock = Any()
    private var current = 0

    val available: Boolean get() = vibrator?.hasVibrator() == true

    /** Motor values 0..255 as sent to the gamepad. */
    fun set(left: Int, right: Int) {
        val v = vibrator ?: return
        synchronized(lock) {
            var level = PhoneRumbleLevel.of(left, right)
            val amplitudeControl = Build.VERSION.SDK_INT >= 26 && v.hasAmplitudeControl()
            if (!amplitudeControl) level = if (level >= PhoneRumbleLevel.ON_OFF_THRESHOLD) 255 else 0
            if (!PhoneRumbleLevel.shouldUpdate(current, level)) return
            current = level
            try {
                if (level == 0) {
                    v.cancel()
                } else if (Build.VERSION.SDK_INT >= 26) {
                    val amplitude = if (amplitudeControl) level else VibrationEffect.DEFAULT_AMPLITUDE
                    @Suppress("DEPRECATION")
                    v.vibrate(VibrationEffect.createOneShot(MAX_MS, amplitude), attributes)
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(MAX_MS, attributes)
                }
            } catch (_: Exception) {
                // A vendor vibrator service that refuses the request must not break the bridge.
            }
        }
    }

    fun off() = set(0, 0)

    companion object {
        /** Upper bound for one request; the bridge's stuck-motor guard usually ends it sooner. */
        const val MAX_MS = 10_000L

        /**
         * With Android's "Vibrate input devices" on, app vibration is sent to connected
         * controllers instead of the phone, so it would come straight back as rumble on the
         * virtual controller.
         */
        fun inputDevicesTakeVibration(context: Context): Boolean = try {
            android.provider.Settings.System.getInt(context.contentResolver, "vibrate_input_devices", 0) == 1
        } catch (_: Exception) {
            false
        }
    }
}
