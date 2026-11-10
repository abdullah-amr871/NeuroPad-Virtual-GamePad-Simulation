package io.github.abdullahamr871.neuropad.core

/**
 * The virtual controllers NeuroPad can present to Android.
 *
 * [id] is the persisted "emulation mode" (kept identical to NeuroPad 1.0 so that the
 * numbering in docs, diagnostics and saved settings stays stable).
 */
enum class EmulationProfile(
    val id: Int,
    val label: String,
    val shortLabel: String,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val bus: String,
    val descriptor: IntArray,
    val family: Family,
) {
    XBOX_360(
        0, "Xbox 360 Controller", "Xbox 360", "Virtual Xbox 360 Controller",
        0x045E, 0x028E, "usb", HidDescriptors.XBOX_360, Family.XBOX,
    ),
    XBOX_WIRELESS(
        1, "Xbox Wireless Controller", "Xbox One", "Xbox Wireless Controller",
        0x045E, 0x02FD, "bluetooth", HidDescriptors.XBOX_BLUETOOTH, Family.XBOX,
    ),
    DUALSHOCK4(
        2, "Sony DualShock 4", "DualShock 4", "Wireless Controller",
        0x054C, 0x05C4, "bluetooth", HidDescriptors.DUALSHOCK4, Family.PLAYSTATION,
    ),
    DUALSENSE(
        3, "Sony DualSense (PS5)", "DualSense", "DualSense Wireless Controller",
        0x054C, 0x0CE6, "usb", HidDescriptors.DUALSENSE_USB, Family.PLAYSTATION,
    ),
    XBOX_SERIES(
        4, "Xbox Series X|S Controller", "Xbox Series", "Xbox Wireless Controller",
        0x045E, 0x0B13, "bluetooth", HidDescriptors.XBOX_BLUETOOTH, Family.XBOX,
    );

    enum class Family { XBOX, PLAYSTATION }

    /** True for the Bluetooth Xbox profiles, whose output report also carries trigger motors. */
    val hasTriggerMotors: Boolean get() = this == XBOX_WIRELESS || this == XBOX_SERIES

    /**
     * Feature reports the kernel driver asks for while it probes the device.
     * Key = report id, value = full payload (first byte is the report id).
     */
    val featureReports: Map<Int, IntArray>
        get() = when (this) {
            DUALSHOCK4 -> mapOf(
                0x05 to HidDescriptors.SONY_CALIBRATION,
                0xA3 to HidDescriptors.DS4_FIRMWARE_INFO,
            )
            DUALSENSE -> mapOf(
                0x05 to HidDescriptors.SONY_CALIBRATION,
                0x09 to HidDescriptors.DUALSENSE_PAIRING_INFO,
                0x20 to HidDescriptors.DUALSENSE_FIRMWARE_INFO,
            )
            else -> emptyMap()
        }

    /** Names of the 16 logical buttons as this controller family labels them. */
    val buttonNames: List<String>
        get() = if (family == Family.PLAYSTATION) Buttons.PLAYSTATION_NAMES else Buttons.XBOX_NAMES

    companion object {
        fun fromId(id: Int): EmulationProfile = entries.firstOrNull { it.id == id } ?: XBOX_360
    }
}

/** Indices of the 16 logical buttons used throughout the app. */
object Buttons {
    const val A = 0
    const val B = 1
    const val X = 2
    const val Y = 3
    const val LB = 4
    const val RB = 5
    const val LT = 6
    const val RT = 7
    const val SELECT = 8
    const val START = 9
    const val L3 = 10
    const val R3 = 11
    const val GUIDE = 12
    const val SHARE = 13
    const val M1 = 14
    const val M2 = 15
    const val COUNT = 16

    val XBOX_NAMES = listOf(
        "A", "B", "X", "Y", "LB", "RB", "LT", "RT", "Select", "Start",
        "L3", "R3", "Home / Guide", "Share (Capture)", "M1", "M2",
    )
    val PLAYSTATION_NAMES = listOf(
        "Cross", "Circle", "Square", "Triangle", "L1", "R1", "L2", "R2",
        "Share", "Options", "L3", "R3", "PS Home", "Touchpad", "M1", "M2",
    )
}
