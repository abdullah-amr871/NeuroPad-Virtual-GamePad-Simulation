package io.github.abdullahamr871.neuropad.bridge

import android.content.Context
import android.hardware.usb.UsbDevice
import io.github.abdullahamr871.neuropad.adb.AdbConnectionManager
import io.github.abdullahamr871.neuropad.core.ButtonLayout
import io.github.abdullahamr871.neuropad.core.EmulationProfile
import io.github.abdullahamr871.neuropad.core.HapticSplitter
import io.github.abdullahamr871.neuropad.core.HidProtocol
import io.github.abdullahamr871.neuropad.core.PadState
import io.github.abdullahamr871.neuropad.core.ReportBuilder
import io.github.abdullahamr871.neuropad.core.RumbleParser
import io.github.abdullahamr871.neuropad.core.XInputParser
import io.github.abdullahamr871.neuropad.haptics.PhoneVibrationMirror
import io.github.abdullahamr871.neuropad.usb.UsbGamepadManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * One run of the bridge:
 *
 *     USB gamepad --XInput--> [this class] --JSON--> uinput_proto --/dev/uhid--> Android
 *     USB gamepad <--rumble-- [this class] <--JSON-- uinput_proto <--output reports-- games
 *
 * @param onLost called once if the session dies on its own (driver pipe closed).
 */
class BridgeSession(
    private val context: Context,
    private val settings: Settings,
    private val onLost: (reason: String) -> Unit,
) : UsbGamepadManager.Listener {

    val profile: EmulationProfile = settings.profile
    private val usb = UsbGamepadManager(context, this)
    private val shell = Shell(context, settings.accessMode == AccessMode.ROOT)
    private val splitter = HapticSplitter()
    @Volatile private var builder = newBuilder()
    @Volatile private var gain = settings.rumbleGain
    @Volatile private var rumbleTimeoutMs = settings.rumbleTimeoutMs

    private var process: ShellProcess? = null
    private var stdin: OutputStream? = null
    private val writeLock = Any()
    private var mirror: PhoneVibrationMirror? = null
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "NeuroPad-Watchdog") }
    private var watchdog: ScheduledFuture<*>? = null

    @Volatile private var stopping = false
    @Volatile private var lostReported = false
    @Volatile private var registered = false

    @Volatile private var ready = CountDownLatch(1)
    private val registeredLatch = CountDownLatch(1)
    /** Set when the daemon itself reported a fatal problem. */
    @Volatile private var daemonError: String? = null
    @Volatile private var greeted = false
    @Volatile private var exitedEarly = false
    private val strayOutput = StringBuilder()

    // Statistics, published to the UI at most ten times a second.
    private var usbPackets = 0L
    private var reportsSent = 0L
    private var rumbleReports = 0L
    private var latencyMs = 0f
    private var lastPublishNanos = 0L
    private var lastRaw = ""
    @Volatile private var lastOutput = ""

    private fun newBuilder() = ReportBuilder(
        settings.profile,
        ButtonLayout.of(settings.layoutMode, settings.customMapping()),
        settings.homeCombo,
    )

    /** Blocking; call from a worker thread. Throws with a user-readable message on failure. */
    fun start() {
        BridgeState.resetSession()
        applyHapticSettings()

        if (!shell.root) {
            step("Connecting to ADB")
            connectAdb()
        } else {
            step("Requesting root")
            if (!Shell.hasRoot()) throw IllegalStateException("Root access was denied.")
        }

        step("Checking the driver")
        BridgeState.log("Bridge", DaemonInstaller.ensureInstalled(context, shell))
        // A daemon left behind by a crash would keep a second virtual controller alive.
        try {
            shell.run("killall uinput_proto 2>/dev/null", 3000)
        } catch (_: Exception) {
        }

        step("Starting the driver")
        var raw = true
        if (!launchDaemon(raw = true) && daemonError == null && !shell.root) {
            // adbd without the raw exec service, or a 1.0 daemon that prints no greeting:
            // retry over the terminal-backed transport that 1.0 used.
            closeProcess()
            raw = false
            launchDaemon(raw = false)
        }
        daemonError?.let { throw IllegalStateException(it) }
        if (!greeted && exitedEarly) throw IllegalStateException(explainSilentExit())
        if (!greeted) BridgeState.log("Bridge", "Driver sent no greeting, assuming a 1.0 driver")

        step("Creating ${profile.shortLabel} controller")
        write(HidProtocol.register(profile, VIRTUAL_UNIQ))
        val confirmed = registeredLatch.await(if (greeted) 3000 else 1200, TimeUnit.MILLISECONDS)
        daemonError?.let { throw IllegalStateException(it) }
        if (stopping) return
        if (exitedEarly) throw IllegalStateException(explainSilentExit())
        if (greeted && !confirmed) throw IllegalStateException("The driver did not confirm the virtual controller.")
        registered = true

        if (settings.phoneVibrationFallback) {
            mirror = PhoneVibrationMirror(shell, raw, context.packageName, splitter) { l, r ->
                applyRumble(l, r, RumbleSource.PHONE_VIBRATION)
            }.also { it.start() }
        }

        usb.register()
        usb.scanAndConnect()
        BridgeState.status.value = BridgeStatus.Running(profile.deviceName)
        BridgeState.log("Bridge", "Virtual ${profile.label} is live")
    }

    private fun step(text: String) {
        BridgeState.status.value = BridgeStatus.Starting(text)
        BridgeState.log("Bridge", text)
    }

    private fun connectAdb() {
        val manager = AdbConnectionManager.get(context)
        if (manager.isConnected) {
            if (BridgeState.adb.value !is AdbStatus.Connected) {
                BridgeState.adb.value = AdbStatus.Connected(settings.adbPort)
            }
            return
        }
        // Same default as 1.0: without a discovered port, try the classic tcpip port.
        val port = settings.adbPort.takeIf { it > 0 } ?: 5555
        BridgeState.adb.value = AdbStatus.Connecting
        try {
            if (!manager.connect(AdbConnectionManager.LOOPBACK, port)) throw IllegalStateException("refused")
            BridgeState.adb.value = AdbStatus.Connected(port)
        } catch (e: Throwable) {
            val reason = e.message ?: e.javaClass.simpleName
            BridgeState.adb.value = AdbStatus.Failed(reason)
            throw IllegalStateException(
                if (settings.accessMode == AccessMode.PORT_5555) {
                    "ADB port 5555 is closed ($reason). Run the port activator from your computer again."
                } else {
                    "Could not reach Wireless debugging on port $port ($reason). Check that it is on and paired."
                },
            )
        }
    }

    /** @return true when the daemon greeted us (2.x), false on silence, exit or error. */
    private fun launchDaemon(raw: Boolean): Boolean {
        ready = CountDownLatch(1)
        greeted = false
        exitedEarly = false
        synchronized(strayOutput) { strayOutput.setLength(0) }
        val p = try {
            shell.spawn("${DaemonInstaller.PATH} -", raw)
        } catch (e: Exception) {
            BridgeState.log("Bridge", "Could not open a ${if (raw) "raw" else "terminal"} channel: ${e.message}")
            exitedEarly = true
            return false
        }
        process = p
        stdin = p.stdin
        thread(name = "NeuroPad-HID-Reader", priority = Thread.MAX_PRIORITY) { readLoop(p) }
        ready.await(1500, TimeUnit.MILLISECONDS)
        return greeted && daemonError == null
    }

    private fun explainSilentExit(): String {
        val stray = synchronized(strayOutput) { strayOutput.toString() }.trim()
        val lower = stray.lowercase()
        return when {
            "not found" in lower || "no such file" in lower ->
                "The driver is not installed on this phone. Run the PC installer once."
            "denied" in lower || "inaccessible" in lower || "not executable" in lower ->
                "Android refused to run the driver (${stray.take(80)})."
            stray.isNotEmpty() -> "The driver stopped right away (${stray.take(80)})."
            else -> "The driver stopped right away."
        }
    }

    private fun readLoop(p: ShellProcess) {
        val splitter = HidProtocol.ObjectSplitter(::onDaemonEvent)
        val buf = CharArray(2048)
        try {
            val reader = InputStreamReader(p.stdout, Charsets.UTF_8)
            while (true) {
                val n = reader.read(buf)
                if (n < 0) break
                if (!registered) synchronized(strayOutput) {
                    if (strayOutput.length < 600) strayOutput.append(buf, 0, n)
                }
                splitter.feed(buf, n)
            }
        } catch (_: Exception) {
        }
        if (p !== process) return // replaced or closed on purpose
        if (registered) {
            reportLost("The connection to the driver was lost.")
        } else {
            exitedEarly = true
            ready.countDown()
            registeredLatch.countDown()
        }
    }

    private fun onDaemonEvent(json: String) {
        val obj = try {
            JSONObject(json)
        } catch (_: Exception) {
            return
        }
        when (obj.optString("event")) {
            "ready" -> {
                BridgeState.daemonVersion.value = obj.optString("version", "?")
                greeted = true
                ready.countDown()
            }
            "registered" -> registeredLatch.countDown()
            "error" -> {
                daemonError = friendlyDaemonError(obj.optString("message"))
                BridgeState.log("Driver", obj.optString("message"))
                ready.countDown()
                registeredLatch.countDown()
            }
            "output" -> (obj.optJSONArray("reportData") ?: obj.optJSONArray("data"))?.let(::onOutputReport)
            "get_report" -> {
                val requestId = obj.optInt("id", -1)
                val reportId = obj.optInt("reportId", -1)
                if (requestId >= 0 && reportId >= 0) {
                    write(HidProtocol.getReportReply(requestId, profile.featureReports[reportId]))
                }
            }
            "set_report" -> {
                // 1.0 never answered these, leaving the kernel driver waiting 5 s for a timeout.
                val requestId = obj.optInt("id", -1)
                if (requestId >= 0) write(HidProtocol.setReportReply(requestId))
                if (obj.optInt("reportType", -1) == UHID_OUTPUT_REPORT) {
                    obj.optJSONArray("reportData")?.let(::onOutputReport)
                }
            }
            else -> if (obj.optInt("eventId", -1) == 6) {
                (obj.optJSONArray("reportData") ?: obj.optJSONArray("data"))?.let(::onOutputReport)
            }
        }
    }

    private fun friendlyDaemonError(message: String): String = when {
        "/dev/uhid" in message ->
            "This phone does not let the shell user create virtual controllers ($message)."
        else -> "Driver error: $message"
    }

    private fun onOutputReport(array: JSONArray) {
        if (array.length() == 0) return
        val data = IntArray(array.length()) { array.optInt(it, 0) }
        val rumble = RumbleParser.parse(profile, data) ?: return
        rumbleReports++
        lastOutput = "id ${data[0]}: " + data.take(12).joinToString(" ") { "%02X".format(it and 0xFF) }
        mirror?.onNativeRumble()
        val left = RumbleParser.boost(rumble.left, rumble.leftTrigger, gain)
        val right = RumbleParser.boost(rumble.right, rumble.rightTrigger, gain)
        applyRumble(left, right, RumbleSource.CONTROLLER_RUMBLE)
    }

    private fun applyRumble(left: Int, right: Int, source: RumbleSource) {
        usb.writeRumble(left, right)
        BridgeState.rumble.value = RumbleState(left, right, if (left > 0 || right > 0) source else RumbleSource.NONE)
        synchronized(scheduler) {
            watchdog?.cancel(false)
            watchdog = null
            val timeout = rumbleTimeoutMs
            if ((left > 0 || right > 0) && timeout > 0 && !scheduler.isShutdown) {
                watchdog = scheduler.schedule({
                    usb.writeRumble(0, 0)
                    BridgeState.rumble.value = RumbleState()
                }, timeout.toLong(), TimeUnit.MILLISECONDS)
            }
        }
    }

    /** Short pulse on the physical motors, independent of any game. */
    fun testRumble(left: Int, right: Int, durationMs: Long) {
        applyRumble(left, right, RumbleSource.TEST)
        synchronized(scheduler) {
            if (!scheduler.isShutdown) {
                watchdog?.cancel(false)
                watchdog = scheduler.schedule({
                    usb.writeRumble(0, 0)
                    BridgeState.rumble.value = RumbleState()
                }, durationMs, TimeUnit.MILLISECONDS)
            }
        }
    }

    /** Picks up changes to layout, mapping and haptic settings without a restart. */
    fun reloadSettings() {
        builder = newBuilder()
        applyHapticSettings()
    }

    private fun applyHapticSettings() {
        gain = settings.rumbleGain
        rumbleTimeoutMs = settings.rumbleTimeoutMs
        splitter.gain = gain
        splitter.lowThreshold = settings.hapticLowThreshold
        splitter.highThreshold = settings.hapticHighThreshold
        splitter.smoothing = settings.hapticSmoothing
    }

    // ---- USB side ------------------------------------------------------------------------

    override fun onConnected(device: UsbDevice, name: String) {
        BridgeState.usbDeviceName.value = name
    }

    override fun onDisconnected() {
        BridgeState.usbDeviceName.value = null
        BridgeState.rumble.value = RumbleState()
        // Release every button on the virtual pad so nothing stays held down.
        if (registered && !stopping) {
            val idle = PadState()
            write(HidProtocol.report(builder.build(idle)))
            BridgeState.physical.value = idle
            BridgeState.mappedButtons.value = 0
        }
    }

    override fun onMessage(message: String) = BridgeState.log("USB", message)

    override fun onPacket(data: ByteArray, length: Int) {
        val t0 = System.nanoTime()
        usbPackets++
        val state = XInputParser.parse(data, length)
        if (state != null && registered) {
            val b = builder
            val mapped = b.mapButtons(state.buttons)
            if (write(HidProtocol.report(b.build(state, mapped)))) reportsSent++
            BridgeState.physical.value = state
            BridgeState.mappedButtons.value = mapped
            val ms = (System.nanoTime() - t0) / 1_000_000f
            latencyMs = if (latencyMs == 0f) ms else latencyMs * 0.9f + ms * 0.1f
        }
        if (t0 - lastPublishNanos > 100_000_000L) {
            lastPublishNanos = t0
            lastRaw = buildString(48) {
                for (i in 0 until minOf(length, 16)) append("%02X ".format(data[i].toInt() and 0xFF))
            }.trim()
            publishCounters()
        }
    }

    private fun publishCounters() {
        BridgeState.counters.value = BridgeCounters(usbPackets, reportsSent, rumbleReports, lastRaw, lastOutput, latencyMs)
    }

    // ---- Driver pipe ---------------------------------------------------------------------

    private fun write(line: String): Boolean {
        val out = stdin ?: return false
        return try {
            synchronized(writeLock) {
                out.write(line.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            true
        } catch (e: Exception) {
            reportLost("The connection to the driver was lost (${e.message}).")
            false
        }
    }

    private fun reportLost(reason: String) {
        if (stopping || lostReported) return
        lostReported = true
        onLost(reason)
    }

    private fun closeProcess() {
        val p = process
        process = null
        stdin = null
        Shell.closeQuietly(p)
    }

    fun stop() {
        if (stopping) return
        stopping = true
        ready.countDown()
        registeredLatch.countDown()
        synchronized(scheduler) {
            watchdog?.cancel(false)
            scheduler.shutdownNow()
        }
        mirror?.stop()
        mirror = null
        usb.writeRumble(0, 0)
        usb.disconnect()
        usb.unregister()
        closeProcess()
        // Closing the pipe makes the daemon exit (stdin EOF) and the kernel destroy the
        // virtual controller. Leftovers from a crash are swept at the next start instead of
        // here, where a delayed `killall` could hit the daemon of a quick restart.
        publishCounters()
        BridgeState.usbDeviceName.value = null
        BridgeState.rumble.value = RumbleState()
        BridgeState.log("Bridge", "Stopped")
    }

    private companion object {
        /** Sent for wire compatibility; the daemon deliberately does not hand it to the kernel. */
        const val VIRTUAL_UNIQ = "aa:bb:cc:dd:ee:ff"
        const val UHID_OUTPUT_REPORT = 1
    }
}
