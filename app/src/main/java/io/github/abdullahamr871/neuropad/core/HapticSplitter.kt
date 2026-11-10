package io.github.abdullahamr871.neuropad.core

/**
 * Converts a single phone-vibration amplitude (0..255) into a strong (left) and weak
 * (right) gamepad motor value. Used by the logcat fallback when a game only drives the
 * phone's own vibrator.
 *
 *  - quiet effects go to the light, high-frequency motor only,
 *  - medium effects fade the heavy motor in,
 *  - strong effects drive both motors fully.
 */
class HapticSplitter(
    @Volatile var gain: Float = 4f,
    @Volatile var lowThreshold: Int = 80,
    @Volatile var highThreshold: Int = 160,
    @Volatile var smoothing: Boolean = true,
) {
    private var lastLeft = 0
    private var lastRight = 0

    @Synchronized
    fun split(amplitude: Int): Pair<Int, Int> {
        val amp = if (amplitude > 0) (amplitude * gain).toInt().coerceAtMost(255) else 0
        val low = lowThreshold
        val high = if (highThreshold > low) highThreshold else low + 1
        val left: Int
        val right: Int
        when {
            amp <= 0 -> {
                left = 0
                right = 0
            }
            amp < low -> {
                left = 0
                right = minOf(255, amp * 2)
            }
            amp < high -> {
                left = minOf(255, (amp - low) * 255 / (high - low))
                right = amp
            }
            else -> {
                left = amp
                right = amp
            }
        }
        val alpha = if (smoothing) 0.3f else 0f
        val outLeft = (left * (1 - alpha) + lastLeft * alpha).toInt()
        val outRight = (right * (1 - alpha) + lastRight * alpha).toInt()
        lastLeft = outLeft
        lastRight = outRight
        return outLeft to outRight
    }

    @Synchronized
    fun reset() {
        lastLeft = 0
        lastRight = 0
    }
}
