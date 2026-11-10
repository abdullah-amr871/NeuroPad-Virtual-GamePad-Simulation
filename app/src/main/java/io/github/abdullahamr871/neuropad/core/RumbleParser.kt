package io.github.abdullahamr871.neuropad.core

/** Rumble request decoded from a HID output report. Motor values are 0..255. */
data class Rumble(
    val left: Int,
    val right: Int,
    val leftTrigger: Int = 0,
    val rightTrigger: Int = 0,
) {
    companion object {
        val OFF = Rumble(0, 0)
    }
}

/**
 * Decodes the output reports that the kernel driver writes to the virtual controller
 * when a game asks for vibration. `data[0]` is the report id.
 */
object RumbleParser {

    fun parse(profile: EmulationProfile, data: IntArray): Rumble? {
        if (data.isEmpty()) return null
        fun at(i: Int) = data.getOrElse(i) { 0 } and 0xFF
        val id = at(0)
        val n = data.size
        return when (profile) {
            EmulationProfile.DUALSENSE ->
                if (id == 0x02 && n >= 5) Rumble(left = at(4), right = at(3)) else Rumble(at(1), at(2))

            EmulationProfile.DUALSHOCK4 -> when {
                id == 0x11 && n >= 8 -> Rumble(left = at(7), right = at(6))
                id == 0x05 && n >= 6 -> Rumble(left = at(5), right = at(4))
                else -> Rumble(at(1), at(2))
            }

            EmulationProfile.XBOX_WIRELESS, EmulationProfile.XBOX_SERIES -> when {
                // hid-microsoft: [3, enable, trigL, trigR, strong, weak, ...] with 0..100 magnitudes.
                id == 0x03 && n >= 6 -> Rumble(
                    left = (at(4) * 255 / 100).coerceIn(0, 255),
                    right = (at(5) * 255 / 100).coerceIn(0, 255),
                    leftTrigger = at(2),
                    rightTrigger = at(3),
                )
                id == 0x09 && n >= 8 -> Rumble(at(6), at(7))
                n >= 5 && at(1) == 0x08 && at(2) == 0x00 -> Rumble(at(3), at(4))
                else -> Rumble(at(1), at(2))
            }

            EmulationProfile.XBOX_360 ->
                if (n >= 5 && at(1) == 0x08 && at(2) == 0x00) Rumble(at(3), at(4)) else Rumble(at(1), at(2))
        }
    }

    /**
     * Final motor strength: adds the trigger motors on the Xbox Bluetooth profiles (the
     * physical pad has no trigger motors, so their energy is folded into the main ones)
     * and applies the user's gain. NeuroPad 1.0 used a fixed gain of 4.
     */
    fun boost(value: Int, trigger: Int, gain: Float): Int {
        val v = value + trigger
        if (v <= 0) return 0
        return (v * gain).toInt().coerceIn(0, 255)
    }
}
