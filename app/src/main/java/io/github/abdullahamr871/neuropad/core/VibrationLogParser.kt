package io.github.abdullahamr871.neuropad.core

/** One event recognised in the system vibrator's log output. */
sealed interface VibrationEvent {
    /** The app that is about to vibrate; used to mute system / keyboard haptics. */
    data class Caller(val packageName: String) : VibrationEvent
    data class OneShot(val durationMs: Long, val amplitude: Int) : VibrationEvent
    class Waveform(val timings: LongArray, val amplitudes: IntArray, val repeat: Int) : VibrationEvent

    /** RichTap (OnePlus / Oppo / Nothing ...) announces duration first, strength second. */
    data class RichTapDuration(val durationMs: Int) : VibrationEvent
    data class RichTapStrength(val strength: Int) : VibrationEvent
    data object Stop : VibrationEvent
}

/**
 * Parser for the lines printed by `logcat -s VibratorService VibratorHal ...`.
 * Pure string handling so that it can be unit tested without a device.
 */
object VibrationLogParser {
    const val LOGCAT_COMMAND =
        "logcat -T 1 -s VibratorService VibratorHal Vibrator NtVibratorManagerImpl " +
            "VibratorController RichTapVibratorService AacRichTapConvert"

    private val ONE_SHOT = Regex("""OneShot\{mDuration=(\d+),\s*mAmplitude=(-?\d+)\}""")
    private val WAVEFORM =
        Regex("""Waveform\{mTimings=\[([\d,\s\-]+)],\s*mAmplitudes=\[([\d,\s\-]+)],\s*mRepeat=(-?\d+)\}""")
    private val WHITESPACE = Regex("\\s+")
    private const val PKG = "pkgName = "
    private val STOP_MARKERS = listOf("richtap service stop!!", "vibratorStop", "end current vibrate")

    /** A line may carry both a caller and an effect, so a list is returned. */
    fun parse(line: String): List<VibrationEvent> {
        val out = ArrayList<VibrationEvent>(2)
        val pkgAt = line.indexOf(PKG)
        if (pkgAt >= 0) {
            val name = line.substring(pkgAt + PKG.length).substringBefore(',').replace("\"", "").trim()
            out += VibrationEvent.Caller(name)
        }
        when {
            STOP_MARKERS.any { it in line } -> out += VibrationEvent.Stop
            "richTapVibratorOn" in line ->
                numberAfter(line, "duration :")?.let { out += VibrationEvent.RichTapDuration(it) }
            "richTapVibratorSetAmplitude" in line ->
                numberAfter(line, "strength :")?.let { out += VibrationEvent.RichTapStrength(it) }
            "OneShot" in line -> ONE_SHOT.find(line)?.let { m ->
                val duration = m.groupValues[1].toLongOrNull()
                val amplitude = m.groupValues[2].toIntOrNull()
                if (duration != null && amplitude != null) {
                    out += VibrationEvent.OneShot(duration, normalise(amplitude))
                }
            }
            "Waveform" in line -> WAVEFORM.find(line)?.let { m ->
                val timings = m.groupValues[1].split(',').mapNotNull { it.trim().toLongOrNull() }
                val amplitudes = m.groupValues[2].split(',').mapNotNull { it.trim().toIntOrNull() }
                val repeat = m.groupValues[3].toIntOrNull() ?: -1
                // NeuroPad 1.0 indexed both arrays with the timings length and killed the
                // playback thread when a vendor printed arrays of different sizes.
                val n = minOf(timings.size, amplitudes.size)
                if (n > 0) {
                    out += VibrationEvent.Waveform(
                        timings.take(n).toLongArray(),
                        amplitudes.take(n).map(::normalise).toIntArray(),
                        repeat,
                    )
                }
            }
        }
        return out
    }

    /** True when haptics from this caller are UI feedback rather than game rumble. */
    fun isSystemCaller(packageName: String, ownPackage: String): Boolean =
        packageName == "android" || packageName == ownPackage || packageName == "null" ||
            "systemui" in packageName || "latin" in packageName

    /** -1 is VibrationEffect.DEFAULT_AMPLITUDE. */
    private fun normalise(amplitude: Int): Int = if (amplitude == -1) 255 else amplitude.coerceIn(0, 255)

    private fun numberAfter(line: String, key: String): Int? {
        val at = line.indexOf(key)
        if (at < 0) return null
        val token = line.substring(at + key.length).trim().split(WHITESPACE).firstOrNull() ?: return null
        return token.filter(Char::isDigit).toIntOrNull()
    }
}
