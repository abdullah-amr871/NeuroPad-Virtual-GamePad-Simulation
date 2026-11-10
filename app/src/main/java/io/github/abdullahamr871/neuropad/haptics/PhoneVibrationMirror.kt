package io.github.abdullahamr871.neuropad.haptics

import io.github.abdullahamr871.neuropad.bridge.BridgeState
import io.github.abdullahamr871.neuropad.bridge.Shell
import io.github.abdullahamr871.neuropad.bridge.ShellProcess
import io.github.abdullahamr871.neuropad.core.HapticSplitter
import io.github.abdullahamr871.neuropad.core.VibrationEvent
import io.github.abdullahamr871.neuropad.core.VibrationLogParser
import kotlin.concurrent.thread

/**
 * Fallback for games that never send rumble to a controller and only vibrate the phone:
 * the system vibrator's log is followed and every effect is replayed on the gamepad.
 *
 * It switches itself off for the rest of the session as soon as real controller rumble
 * arrives through the virtual device ([onNativeRumble]), so effects are never doubled.
 */
class PhoneVibrationMirror(
    private val shell: Shell,
    private val rawTransport: Boolean,
    private val ownPackage: String,
    private val splitter: HapticSplitter,
    /** Receives motor values 0..255 (left = heavy, right = light). */
    private val sink: (left: Int, right: Int) -> Unit,
) {
    @Volatile private var running = false
    @Volatile private var nativePathActive = false
    @Volatile private var mutedCaller = false
    private var process: ShellProcess? = null
    private var readerThread: Thread? = null
    private var effectThread: Thread? = null
    private val effectLock = Any()

    private var richTapDurationMs = 0
    private var richTapAtNanos = 0L

    fun start() {
        if (running) return
        running = true
        readerThread = thread(name = "NeuroPad-Vibration-Log") {
            try {
                // Some vendors only print vibration details at DEBUG level.
                for (tag in listOf("VibratorService", "VibratorHal", "Vibrator")) {
                    try {
                        shell.run("setprop log.tag.$tag DEBUG", 2000)
                    } catch (_: Exception) {
                    }
                }
                if (!running) return@thread
                val p = shell.spawn(VibrationLogParser.LOGCAT_COMMAND, rawTransport)
                process = p
                BridgeState.log("Haptics", "Phone-vibration fallback is listening")
                p.stdout.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (!running) break
                        handle(line)
                    }
                }
            } catch (e: Exception) {
                if (running) BridgeState.log("Haptics", "Vibration log reader stopped: ${e.message}")
            }
        }
    }

    fun stop() {
        running = false
        cancelEffect()
        Shell.closeQuietly(process)
        process = null
        readerThread?.interrupt()
        readerThread = null
    }

    /** Called when the game drives the virtual controller's own rumble motors. */
    fun onNativeRumble() {
        if (!nativePathActive) {
            nativePathActive = true
            cancelEffect()
            BridgeState.log("Haptics", "Controller rumble detected, phone-vibration fallback paused")
        }
    }

    private fun handle(line: String) {
        for (event in VibrationLogParser.parse(line)) {
            when (event) {
                is VibrationEvent.Caller ->
                    mutedCaller = VibrationLogParser.isSystemCaller(event.packageName, ownPackage)
                else -> {
                    // Keyboard clicks, notifications and other system haptics are not game rumble.
                    if (nativePathActive || mutedCaller) continue
                    play(event)
                }
            }
        }
    }

    private fun play(event: VibrationEvent) {
        when (event) {
            is VibrationEvent.Stop -> {
                cancelEffect()
                sink(0, 0)
            }
            is VibrationEvent.OneShot ->
                playSteps(longArrayOf(event.durationMs), intArrayOf(event.amplitude), -1)
            is VibrationEvent.Waveform -> playSteps(event.timings, event.amplitudes, event.repeat)
            is VibrationEvent.RichTapDuration -> {
                richTapDurationMs = event.durationMs
                richTapAtNanos = System.nanoTime()
            }
            is VibrationEvent.RichTapStrength ->
                if (System.nanoTime() - richTapAtNanos < 100_000_000L) {
                    playSteps(
                        longArrayOf(maxOf(richTapDurationMs, 100).toLong()),
                        intArrayOf(event.strength.coerceIn(0, 255)),
                        -1,
                    )
                }
            is VibrationEvent.Caller -> Unit
        }
    }

    private fun playSteps(timings: LongArray, amplitudes: IntArray, repeat: Int) {
        synchronized(effectLock) {
            effectThread?.interrupt()
            effectThread = thread(name = "NeuroPad-Effect") {
                val deadline = System.currentTimeMillis() + MAX_EFFECT_MS
                try {
                    var i = 0
                    while (i < timings.size && System.currentTimeMillis() < deadline) {
                        val (left, right) = splitter.split(amplitudes[i])
                        sink(left, right)
                        if (timings[i] > 0) Thread.sleep(timings[i])
                        i++
                        if (i >= timings.size && repeat in timings.indices) {
                            // A repeating pattern made only of zero-length steps would spin.
                            if (timings.drop(repeat).all { it <= 0 }) break
                            i = repeat
                        }
                    }
                    sink(0, 0)
                } catch (_: InterruptedException) {
                    // Replaced by a newer effect or stopped; the new owner sets the motors.
                }
            }
        }
    }

    private fun cancelEffect() {
        synchronized(effectLock) {
            effectThread?.interrupt()
            effectThread = null
        }
        splitter.reset()
    }

    private companion object {
        /** Upper bound for a repeating pattern whose stop line never shows up in the log. */
        const val MAX_EFFECT_MS = 30_000L
    }
}
