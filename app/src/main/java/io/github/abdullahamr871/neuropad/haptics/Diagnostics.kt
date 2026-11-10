package io.github.abdullahamr871.neuropad.haptics

import android.content.Context
import android.hardware.input.InputManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.view.InputDevice
import io.github.abdullahamr871.neuropad.BuildConfig
import io.github.abdullahamr871.neuropad.bridge.AccessMode
import io.github.abdullahamr871.neuropad.bridge.BridgeState
import io.github.abdullahamr871.neuropad.bridge.DaemonInstaller
import io.github.abdullahamr871.neuropad.bridge.GamepadBridgeService
import io.github.abdullahamr871.neuropad.bridge.Settings
import io.github.abdullahamr871.neuropad.bridge.Shell
import io.github.abdullahamr871.neuropad.adb.AdbConnectionManager
import io.github.abdullahamr871.neuropad.usb.UsbGamepadManager
import java.io.File

/** One line of the rumble-chain checklist. */
data class Check(val title: String, val ok: Boolean?, val detail: String)

/**
 * Collects everything needed to understand why rumble does or does not arrive:
 * the path is game -> Android input -> kernel HID driver -> /dev/uhid -> NeuroPad -> USB.
 */
object Diagnostics {

    /** Fast checks based on what the app already knows. Safe on the main thread. */
    fun quickChecks(context: Context): List<Check> {
        val settings = Settings(context)
        val counters = BridgeState.counters.value
        val running = GamepadBridgeService.isRunning
        val usbName = BridgeState.usbDeviceName.value
        val virtual = virtualDevice(context, settings)
        return listOf(
            Check(
                "Shell access",
                when (settings.accessMode) {
                    AccessMode.ROOT -> null
                    else -> AdbConnectionManager.get(context).isConnected
                },
                "Mode: ${settings.accessMode.label}",
            ),
            Check("Bridge service", running, BridgeState.status.value.describe()),
            Check(
                "USB gamepad",
                usbName != null || UsbGamepadManager.findCompatible(context) != null,
                usbName ?: UsbGamepadManager.findCompatible(context)?.let(UsbGamepadManager::displayName)
                    ?: "not detected",
            ),
            Check("Input reaching the bridge", if (running) counters.usbPackets > 0 else null, "${counters.usbPackets} USB packets"),
            Check(
                "Virtual controller visible to Android",
                if (running) virtual != null else null,
                virtual?.name ?: "not listed by InputManager",
            ),
            Check(
                "Android exposes a vibrator on it",
                if (virtual != null) hasVibrator(virtual) else null,
                if (virtual == null) "start the bridge first" else "games can only rumble a pad that has one",
            ),
            Check(
                "Rumble commands received",
                if (running) counters.rumbleReports > 0 else null,
                "${counters.rumbleReports} output reports" +
                    if (counters.lastOutputReport.isNotEmpty()) " (last: ${counters.lastOutputReport})" else "",
            ),
        )
    }

    private fun io.github.abdullahamr871.neuropad.bridge.BridgeStatus.describe(): String = when (this) {
        is io.github.abdullahamr871.neuropad.bridge.BridgeStatus.Stopped -> "stopped"
        is io.github.abdullahamr871.neuropad.bridge.BridgeStatus.Starting -> "starting: $step"
        is io.github.abdullahamr871.neuropad.bridge.BridgeStatus.Running -> "running as $deviceName"
        is io.github.abdullahamr871.neuropad.bridge.BridgeStatus.Failed -> "failed: $message"
    }

    private fun virtualDevice(context: Context, settings: Settings): InputDevice? {
        val profile = settings.profile
        val im = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        return im.inputDeviceIds.asSequence().mapNotNull(im::getInputDevice).firstOrNull {
            it.vendorId == profile.vendorId && it.productId == profile.productId &&
                it.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        }
    }

    @Suppress("DEPRECATION")
    private fun hasVibrator(device: InputDevice): Boolean =
        if (Build.VERSION.SDK_INT >= 31) device.vibratorManager.vibratorIds.isNotEmpty() else device.vibrator.hasVibrator()

    /** Full text report. Runs shell commands, so call it from a background thread. */
    fun buildReport(context: Context): String = buildString {
        val settings = Settings(context)
        val profile = settings.profile
        appendLine("NeuroPad ${BuildConfig.VERSION_NAME} diagnostics")
        appendLine("=".repeat(40))
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Kernel: ${System.getProperty("os.version")}")
        appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine()
        appendLine("Profile: ${profile.label} (mode ${profile.id}, %04X:%04X, ${profile.bus})".format(profile.vendorId, profile.productId))
        appendLine("Layout: ${settings.layoutMode.label}")
        appendLine("Access: ${settings.accessMode.label}, port ${settings.adbPort}")
        appendLine("Driver version: ${BridgeState.daemonVersion.value ?: "unknown / not running"}")
        appendLine("Rumble gain: ${settings.rumbleGain}, guard: ${settings.rumbleTimeoutMs} ms, fallback: ${settings.phoneVibrationFallback}")
        appendLine()

        appendLine("--- Rumble chain ---")
        for (c in quickChecks(context)) {
            val mark = when (c.ok) {
                true -> "[ok]"
                false -> "[!!]"
                null -> "[--]"
            }
            appendLine("$mark ${c.title}: ${c.detail}")
        }
        appendLine()

        appendLine("--- HID descriptor ---")
        val d = profile.descriptor
        fun has(a: Int, b: Int) = (0 until d.size - 1).any { d[it] == a && d[it + 1] == b }
        appendLine("Generic Desktop page: ${has(0x05, 0x01)}, Gamepad usage: ${has(0x09, 0x05)}, output report: ${d.contains(0x91)}")
        appendLine()

        appendLine("--- Kernel (through the shell) ---")
        try {
            val shell = Shell(context, settings.accessMode == AccessMode.ROOT)
            val probe = shell.run(
                "echo uhid_writable=\$([ -w /dev/uhid ] && echo yes || echo no); " +
                    "echo hid_playstation=\$([ -d /sys/module/hid_playstation ] && echo yes || echo no); " +
                    "echo hid_sony=\$([ -d /sys/module/hid_sony ] && echo yes || echo no); " +
                    "echo hid_microsoft=\$([ -d /sys/module/hid_microsoft ] && echo yes || echo no); " +
                    "ls -l ${DaemonInstaller.PATH} 2>&1; " +
                    "for d in /sys/bus/hid/devices/*; do echo \"\$d -> \$(basename \$(readlink \$d/driver 2>/dev/null) 2>/dev/null)\"; done; " +
                    "tail -n 15 /data/local/tmp/uhid_err.log 2>/dev/null",
                5000,
            )
            appendLine(probe.ifBlank { "(no output)" })
        } catch (e: Throwable) {
            appendLine("Shell not available: ${e.message}")
        }
        appendLine()

        appendLine("--- Input devices ---")
        val im = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        for (id in im.inputDeviceIds) {
            val dev = im.getInputDevice(id) ?: continue
            if (dev.isVirtual) continue
            val pad = dev.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                dev.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            if (!pad) continue
            appendLine("#$id ${dev.name}  %04X:%04X  vibrator=${hasVibrator(dev)}".format(dev.vendorId, dev.productId))
        }
        appendLine()

        appendLine("--- USB devices ---")
        val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
        for (dev in usb.deviceList.values) {
            appendLine(
                "${UsbGamepadManager.displayName(dev)}  %04X:%04X  interfaces=${dev.interfaceCount}  compatible=${UsbGamepadManager.isCompatible(dev)}"
                    .format(dev.vendorId, dev.productId),
            )
            for (i in 0 until dev.interfaceCount) {
                val intf = dev.getInterface(i)
                appendLine("   if$i class=%02X sub=%02X proto=%02X endpoints=${intf.endpointCount}".format(intf.interfaceClass, intf.interfaceSubclass, intf.interfaceProtocol))
            }
        }
        appendLine()

        val c = BridgeState.counters.value
        appendLine("--- Counters ---")
        appendLine("USB packets: ${c.usbPackets}, HID reports: ${c.reportsSent}, rumble reports: ${c.rumbleReports}")
        appendLine("Bridge latency (USB read -> driver pipe): %.2f ms".format(c.bridgeLatencyMs))
        appendLine("Last USB packet: ${c.lastRawPacket}")
        appendLine("Last output report: ${c.lastOutputReport}")
        appendLine()
        appendLine("--- Log ---")
        BridgeState.log.value.takeLast(120).forEach(::appendLine)
    }

    /** Writes the report where a file manager and the share sheet can reach it. */
    fun export(context: Context): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        return File(dir, "neuropad_diagnostics.txt").apply { writeText(buildReport(context)) }
    }
}
