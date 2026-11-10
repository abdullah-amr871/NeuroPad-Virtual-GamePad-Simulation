package io.github.abdullahamr871.neuropad.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as SystemSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.abdullahamr871.neuropad.adb.AdbConnectionManager
import io.github.abdullahamr871.neuropad.adb.AdbDiscovery
import io.github.abdullahamr871.neuropad.adb.AdbPairingService
import io.github.abdullahamr871.neuropad.bridge.AccessMode
import io.github.abdullahamr871.neuropad.bridge.AdbStatus
import io.github.abdullahamr871.neuropad.bridge.BridgeState
import io.github.abdullahamr871.neuropad.bridge.GamepadBridgeService
import io.github.abdullahamr871.neuropad.bridge.Settings
import io.github.abdullahamr871.neuropad.bridge.Shell
import io.github.abdullahamr871.neuropad.core.Buttons
import io.github.abdullahamr871.neuropad.core.EmulationProfile
import io.github.abdullahamr871.neuropad.core.LayoutMode
import io.github.abdullahamr871.neuropad.haptics.Check
import io.github.abdullahamr871.neuropad.haptics.Diagnostics
import io.github.abdullahamr871.neuropad.usb.UsbGamepadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** UI state and actions. Everything that touches ADB, root or files runs on Dispatchers.IO. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val context get() = getApplication<Application>()
    val settings = Settings(app)

    var profile by mutableStateOf(settings.profile); private set
    var layoutMode by mutableStateOf(settings.layoutMode); private set
    var accessMode by mutableStateOf(settings.accessMode); private set
    var hotspotOffline by mutableStateOf(settings.hotspotOffline); private set
    var customMapping by mutableStateOf(settings.customMapping()); private set
    var rumbleGain by mutableFloatStateOf(settings.rumbleGain); private set
    var rumbleTimeoutMs by mutableIntStateOf(settings.rumbleTimeoutMs); private set
    var phoneFallback by mutableStateOf(settings.phoneVibrationFallback); private set
    var lowThreshold by mutableIntStateOf(settings.hapticLowThreshold); private set
    var highThreshold by mutableIntStateOf(settings.hapticHighThreshold); private set
    var smoothing by mutableStateOf(settings.hapticSmoothing); private set
    var homeCombo by mutableStateOf(settings.homeCombo); private set
    var paired by mutableStateOf(settings.paired); private set

    /** Name of a compatible pad that is plugged in (whether or not the bridge runs). */
    var detectedPad by mutableStateOf<String?>(null); private set
    /** Text of a blocking progress dialog, or null. */
    var busy by mutableStateOf<String?>(null); private set
    /** Virtual button currently waiting for a physical press in the mapping editor. */
    var captureTarget by mutableStateOf<Int?>(null)
    var checks by mutableStateOf<List<Check>>(emptyList()); private set
    var report by mutableStateOf<String?>(null)
    var batteryExempt by mutableStateOf(true); private set

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Emits the index of the tab the UI should switch to. */
    val navigate = MutableSharedFlow<Int>(extraBufferCapacity = 2)

    private var discovery: AdbDiscovery? = null

    private fun say(text: String) {
        messages.tryEmit(text)
    }

    // ---- Lifecycle -----------------------------------------------------------------------

    fun onResume() {
        refreshUsb()
        paired = settings.paired
        val pm = context.getSystemService(PowerManager::class.java)
        batteryExempt = Build.VERSION.SDK_INT < 23 || pm.isIgnoringBatteryOptimizations(context.packageName)
        viewModelScope.launch(Dispatchers.IO) {
            // Reflect a connection that already exists (service, pairing notification, ...).
            if (accessMode != AccessMode.ROOT) {
                val connected = try {
                    AdbConnectionManager.get(context).isConnected
                } catch (_: Throwable) {
                    false
                }
                if (connected && BridgeState.adb.value !is AdbStatus.Connected) {
                    BridgeState.adb.value = AdbStatus.Connected(settings.adbPort)
                } else if (!connected && BridgeState.adb.value is AdbStatus.Connected) {
                    BridgeState.adb.value = AdbStatus.Disconnected
                }
            }
            refreshChecksBlocking()
        }
        if (accessMode == AccessMode.WIRELESS_DEBUGGING) startDiscovery()
    }

    fun onPause() = stopDiscovery()

    fun refreshUsb() {
        detectedPad = BridgeState.usbDeviceName.value
            ?: UsbGamepadManager.findCompatible(context)?.let(UsbGamepadManager::displayName)
    }

    private fun startDiscovery() {
        if (Build.VERSION.SDK_INT < 30) return // Wireless debugging exists from Android 11
        if (discovery == null) {
            discovery = AdbDiscovery(context, object : AdbDiscovery.Listener {
                override fun onConnectPort(port: Int) {
                    if (settings.accessMode != AccessMode.WIRELESS_DEBUGGING) return
                    settings.wirelessPort = port
                    val idle = BridgeState.adb.value.let { it is AdbStatus.Disconnected || it is AdbStatus.Failed }
                    if (idle && settings.paired) connectAdb(port, quiet = true)
                }
            })
        }
        discovery?.start()
    }

    private fun stopDiscovery() {
        discovery?.stop()
    }

    // ---- Bridge --------------------------------------------------------------------------

    fun toggleBridge() {
        if (BridgeState.status.value.isActive) {
            GamepadBridgeService.stop(context)
            return
        }
        val adbConnected = BridgeState.adb.value is AdbStatus.Connected
        if (accessMode == AccessMode.WIRELESS_DEBUGGING && !adbConnected && settings.wirelessPort == 0) {
            say(if (settings.paired) "Turn on Wireless debugging, then tap Connect." else "Pair with Wireless debugging first.")
            navigate.tryEmit(TAB_CONNECTION)
            return
        }
        GamepadBridgeService.start(context)
    }

    fun selectProfile(value: EmulationProfile) {
        if (value == profile) return
        profile = value
        settings.profile = value
        if (GamepadBridgeService.isRunning) {
            say("Restarting the bridge as ${value.shortLabel}")
            GamepadBridgeService.restart(context)
        }
    }

    fun selectLayout(value: LayoutMode) {
        layoutMode = value
        settings.layoutMode = value
        GamepadBridgeService.reload(context)
    }

    fun mapButton(physical: Int, virtual: Int) {
        settings.setCustomMapping(physical, virtual)
        customMapping = settings.customMapping()
        captureTarget = null
        val names = profile.buttonNames
        say(
            "Physical ${Buttons.XBOX_NAMES[physical]} now presses ${names[virtual]}" +
                if (layoutMode != LayoutMode.CUSTOM) ". Select the Custom layout to use it." else "",
        )
        GamepadBridgeService.reload(context)
    }

    fun resetMapping() {
        settings.resetCustomMapping()
        customMapping = settings.customMapping()
        GamepadBridgeService.reload(context)
        say("Mapping reset")
    }

    fun testRumble(left: Int, right: Int) {
        if (!GamepadBridgeService.isRunning || BridgeState.usbDeviceName.value == null) {
            say("Start the bridge with the gamepad plugged in to test rumble.")
            return
        }
        GamepadBridgeService.testRumble(context, left, right)
    }

    // ---- Haptic settings -----------------------------------------------------------------

    fun updateGain(value: Float) { rumbleGain = value; settings.rumbleGain = value; GamepadBridgeService.reload(context) }
    fun updateTimeout(value: Int) { rumbleTimeoutMs = value; settings.rumbleTimeoutMs = value; GamepadBridgeService.reload(context) }
    fun updateLow(value: Int) { lowThreshold = value; settings.hapticLowThreshold = value; GamepadBridgeService.reload(context) }
    fun updateHigh(value: Int) { highThreshold = value; settings.hapticHighThreshold = value; GamepadBridgeService.reload(context) }
    fun updateSmoothing(value: Boolean) { smoothing = value; settings.hapticSmoothing = value; GamepadBridgeService.reload(context) }
    fun updateHomeCombo(value: Boolean) { homeCombo = value; settings.homeCombo = value; GamepadBridgeService.reload(context) }
    fun updateFallback(value: Boolean) {
        phoneFallback = value
        settings.phoneVibrationFallback = value
        if (GamepadBridgeService.isRunning) say("Applies the next time the bridge starts.")
    }

    // ---- Shell access --------------------------------------------------------------------

    fun selectAccessMode(mode: AccessMode) {
        if (mode == accessMode) return
        if (mode == AccessMode.ROOT) {
            busy = "Asking for root access"
            viewModelScope.launch {
                val ok = withContext(Dispatchers.IO) { Shell.hasRoot() }
                busy = null
                if (ok) applyAccessMode(mode) else say("Root access was not granted. Is this phone rooted?")
            }
        } else {
            applyAccessMode(mode)
        }
    }

    private fun applyAccessMode(mode: AccessMode) {
        if (BridgeState.status.value.isActive) GamepadBridgeService.stop(context)
        accessMode = mode
        settings.accessMode = mode
        if (mode != AccessMode.WIRELESS_DEBUGGING) {
            hotspotOffline = false
            settings.hotspotOffline = false
            stopDiscovery()
        } else {
            startDiscovery()
        }
        // The old connection belongs to the other mode's port.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AdbConnectionManager.get(context).disconnect()
            } catch (_: Throwable) {
            }
            BridgeState.adb.value = AdbStatus.Disconnected
        }
    }

    fun updateHotspotOffline(value: Boolean) {
        hotspotOffline = value
        settings.hotspotOffline = value
    }

    /** @param port 0 = use the port that belongs to the current mode. */
    fun connectAdb(port: Int = 0, quiet: Boolean = false, thenStart: Boolean = false) {
        val target = if (port > 0) port else settings.adbPort
        if (target <= 0) {
            say("The port is not known yet. Open Wireless debugging in Developer options, or enter the port manually.")
            return
        }
        if (BridgeState.adb.value is AdbStatus.Connecting) return
        if (!quiet) busy = "Connecting to ADB on port $target"
        BridgeState.adb.value = AdbStatus.Connecting
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    val manager = AdbConnectionManager.get(context)
                    try {
                        manager.disconnect()
                    } catch (_: Throwable) {
                    }
                    if (manager.connect(AdbConnectionManager.LOOPBACK, target)) null else "connection refused"
                } catch (e: Throwable) {
                    e.message ?: e.javaClass.simpleName
                }
            }
            busy = null
            if (error == null) {
                if (accessMode == AccessMode.WIRELESS_DEBUGGING) settings.wirelessPort = target
                BridgeState.adb.value = AdbStatus.Connected(target)
                if (!quiet) say("ADB connected")
                if (thenStart) GamepadBridgeService.start(context)
            } else {
                BridgeState.adb.value = AdbStatus.Failed(error)
                if (!quiet) say("Could not connect: $error")
            }
        }
    }

    fun startNotificationPairing() {
        AdbPairingService.start(context)
        openDeveloperOptions()
    }

    fun pairManually(port: Int, code: String) {
        busy = "Pairing"
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                AdbPairingService.pairNow(context, port, code, settings.wirelessPort)
            }
            busy = null
            paired = settings.paired
            say(error ?: "Paired with Wireless debugging")
        }
    }

    fun openDeveloperOptions() = launchFirst(
        "Open Developer options from the system settings.",
        Intent(SystemSettings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
    )

    fun openHotspotSettings() = launchFirst(
        "Enable the mobile hotspot from the system settings.",
        Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
        Intent("android.settings.PORTABLE_HOTSPOT_SETTINGS"),
        Intent(SystemSettings.ACTION_WIRELESS_SETTINGS),
    )

    fun requestBatteryExemption() {
        if (Build.VERSION.SDK_INT < 23) return
        launchFirst(
            "Open the battery settings and allow NeuroPad to run unrestricted.",
            Intent(SystemSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
            Intent(SystemSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
    }

    fun openUrl(url: String) = launchFirst("No browser found.", Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun launchFirst(failure: String, vararg intents: Intent) {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
        say(failure)
    }

    // ---- Sharing -------------------------------------------------------------------------

    /** Shares the script that re-opens ADB port 5555 from a Windows PC after a reboot. */
    fun shareActivatorScript() {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                File(context.cacheDir, "shared").apply { mkdirs() }
                    .resolve("NeuroPad_Activate_Port_5555.bat").apply { writeText(ACTIVATOR_BAT) }
            }
            share(file, "application/octet-stream", "Share the PC activator script")
        }
    }

    fun refreshChecks() {
        viewModelScope.launch(Dispatchers.IO) { refreshChecksBlocking() }
    }

    private fun refreshChecksBlocking() {
        checks = try {
            Diagnostics.quickChecks(context)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun buildReport() {
        busy = "Collecting diagnostics"
        viewModelScope.launch {
            report = withContext(Dispatchers.IO) {
                try {
                    Diagnostics.buildReport(context)
                } catch (e: Throwable) {
                    "Could not build the report: ${e.message}"
                }
            }
            busy = null
        }
    }

    fun shareReport() {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                File(context.cacheDir, "shared").apply { mkdirs() }
                    .resolve("neuropad_diagnostics.txt").apply { writeText(report ?: Diagnostics.buildReport(context)) }
            }
            share(file, "text/plain", "Share diagnostics")
        }
    }

    private fun share(file: File, mime: String, title: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).setType(mime)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, file.name)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            say("Could not share the file: ${e.message}")
        }
    }

    override fun onCleared() {
        stopDiscovery()
        super.onCleared()
    }

    companion object {
        const val TAB_HOME = 0
        const val TAB_CONTROLLER = 1
        const val TAB_CONNECTION = 2
        const val TAB_SETTINGS = 3

        const val REPO_URL = "https://github.com/abdullah-amr871/NeuroPad-Virtual-GamePad-Simulation"

        private val ACTIVATOR_BAT = """
            @echo off
            title NeuroPad - Port 5555 Activator
            echo =======================================================
            echo  NeuroPad - Port 5555 Activator
            echo =======================================================
            echo.
            echo  Connect the phone with a USB cable and allow USB debugging.
            echo.
            set "ADB=adb"
            if exist "%~dp0tools\adb.exe" set "ADB=%~dp0tools\adb.exe"
            if exist "%~dp0adb.exe" set "ADB=%~dp0adb.exe"
            "%ADB%" version >nul 2>&1
            if errorlevel 1 (
                echo  adb was not found. Put this file next to adb.exe, or use the
                echo  NeuroPad one-click installer from:
                echo  $REPO_URL/releases/latest
                echo.
                pause
                exit /b 1
            )
            "%ADB%" wait-for-device
            "%ADB%" tcpip 5555
            echo.
            echo  Done. Unplug the cable, open NeuroPad and tap Connect.
            echo.
            pause
        """.trimIndent().replace("\n", "\r\n")
    }
}
