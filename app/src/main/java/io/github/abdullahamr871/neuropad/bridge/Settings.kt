package io.github.abdullahamr871.neuropad.bridge

import android.content.Context
import android.content.SharedPreferences
import io.github.abdullahamr871.neuropad.core.Buttons
import io.github.abdullahamr871.neuropad.core.EmulationProfile
import io.github.abdullahamr871.neuropad.core.LayoutMode

/** How the app obtains the shell privileges needed to open `/dev/uhid`. */
enum class AccessMode(val label: String) {
    /** Android 11+: pair once with Wireless debugging, the port is found automatically. */
    WIRELESS_DEBUGGING("Wireless debugging"),

    /** `adb tcpip 5555` was run from a computer (the one-click installer does this). */
    PORT_5555("PC port 5555"),

    /** Rooted phone: run the daemon through `su`. */
    ROOT("Root"),
}

/**
 * Typed access to the app's preferences. Key names are the ones NeuroPad 1.0 used.
 */
class Settings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("virtualizer_settings", Context.MODE_PRIVATE)
    private val mappingPrefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("button_mapping_prefs", Context.MODE_PRIVATE)

    var profile: EmulationProfile
        get() = EmulationProfile.fromId(prefs.getInt("emulation_mode", 0))
        set(value) = prefs.edit().putInt("emulation_mode", value.id).apply()

    var layoutMode: LayoutMode
        get() = LayoutMode.fromId(prefs.getInt("layout_mode", 0))
        set(value) = prefs.edit().putInt("layout_mode", value.id).apply()

    var accessMode: AccessMode
        get() = when {
            prefs.getBoolean("use_root", false) -> AccessMode.ROOT
            prefs.getBoolean("use_port_5555", false) -> AccessMode.PORT_5555
            else -> AccessMode.WIRELESS_DEBUGGING
        }
        set(value) = prefs.edit()
            .putBoolean("use_root", value == AccessMode.ROOT)
            .putBoolean("use_port_5555", value == AccessMode.PORT_5555)
            .apply()

    /** Wireless debugging over the phone's own hotspot, for use without any Wi-Fi network. */
    var hotspotOffline: Boolean
        get() = prefs.getBoolean("use_hotspot_offline", false)
        set(value) = prefs.edit().putBoolean("use_hotspot_offline", value).apply()

    /** Last known Wireless debugging connection port (changes on every toggle of the feature). */
    var wirelessPort: Int
        get() = prefs.getInt("adb_port", 0)
        set(value) = prefs.edit().putInt("adb_port", value).apply()

    /** Port to use for the current access mode, 0 when it still has to be discovered. */
    val adbPort: Int get() = if (accessMode == AccessMode.PORT_5555) 5555 else wirelessPort

    var paired: Boolean
        get() = prefs.getBoolean("adb_paired", false)
        set(value) = prefs.edit().putBoolean("adb_paired", value).apply()

    /** True while the user wants the bridge running; lets the service restore itself. */
    var bridgeEnabled: Boolean
        get() = prefs.getBoolean("is_virtualization_enabled", false)
        set(value) = prefs.edit().putBoolean("is_virtualization_enabled", value).apply()

    // ---- Haptics -------------------------------------------------------------------------

    /** Multiplier applied to rumble strength. 4.0 is what NeuroPad 1.0 hard-coded. */
    var rumbleGain: Float
        get() = prefs.getFloat("rumble_gain", DEFAULT_GAIN).coerceIn(0.5f, 8f)
        set(value) = prefs.edit().putFloat("rumble_gain", value).apply()

    /**
     * Safety net against stuck motors: rumble is switched off when the game has not
     * refreshed it for this long. 0 disables the guard. 1.0 used a fixed 500 ms, which cut
     * every long rumble short because the kernel only reports when an effect starts or stops.
     */
    var rumbleTimeoutMs: Int
        get() = prefs.getInt("rumble_timeout_ms", 5000)
        set(value) = prefs.edit().putInt("rumble_timeout_ms", value).apply()

    var hapticLowThreshold: Int
        get() = prefs.getInt("haptic_low_threshold", 80)
        set(value) = prefs.edit().putInt("haptic_low_threshold", value).apply()

    var hapticHighThreshold: Int
        get() = prefs.getInt("haptic_high_threshold", 160)
        set(value) = prefs.edit().putInt("haptic_high_threshold", value).apply()

    var hapticSmoothing: Boolean
        get() = prefs.getBoolean("enable_smoothing", true)
        set(value) = prefs.edit().putBoolean("enable_smoothing", value).apply()

    /** Mirror the phone's own vibration to the gamepad for games without controller rumble. */
    var phoneVibrationFallback: Boolean
        get() = prefs.getBoolean("phone_vibration_fallback", true)
        set(value) = prefs.edit().putBoolean("phone_vibration_fallback", value).apply()

    var homeCombo: Boolean
        get() = prefs.getBoolean("home_combo", true)
        set(value) = prefs.edit().putBoolean("home_combo", value).apply()

    // ---- Custom button mapping -----------------------------------------------------------

    fun customMapping(): IntArray =
        IntArray(Buttons.COUNT) { mappingPrefs.getInt("custom_map_$it", it) }

    fun setCustomMapping(physical: Int, virtual: Int) =
        mappingPrefs.edit().putInt("custom_map_$physical", virtual).apply()

    fun resetCustomMapping() = mappingPrefs.edit().clear().apply()

    companion object {
        const val DEFAULT_GAIN = 4f
    }
}
