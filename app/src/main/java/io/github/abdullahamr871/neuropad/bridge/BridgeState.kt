package io.github.abdullahamr871.neuropad.bridge

import android.util.Log
import io.github.abdullahamr871.neuropad.core.PadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface BridgeStatus {
    data object Stopped : BridgeStatus
    data class Starting(val step: String) : BridgeStatus
    data class Running(val deviceName: String) : BridgeStatus
    data class Failed(val message: String) : BridgeStatus

    val isActive: Boolean get() = this is Starting || this is Running
}

sealed interface AdbStatus {
    data object Disconnected : AdbStatus
    data object Connecting : AdbStatus
    data class Connected(val port: Int) : AdbStatus
    data class Failed(val message: String) : AdbStatus
}

/** Where the last rumble came from. */
enum class RumbleSource { NONE, CONTROLLER_RUMBLE, PHONE_VIBRATION, TEST }

data class RumbleState(val left: Int = 0, val right: Int = 0, val source: RumbleSource = RumbleSource.NONE)

data class BridgeCounters(
    val usbPackets: Long = 0,
    val reportsSent: Long = 0,
    val rumbleReports: Long = 0,
    val lastRawPacket: String = "",
    val lastOutputReport: String = "",
    /** Time from reading a USB packet to handing the HID report to the daemon pipe. */
    val bridgeLatencyMs: Float = 0f,
)

/**
 * Process-wide observable state of the bridge. The service writes, the UI collects.
 * (NeuroPad 1.0 pushed this through seven different broadcasts.)
 */
object BridgeState {
    val status = MutableStateFlow<BridgeStatus>(BridgeStatus.Stopped)
    val adb = MutableStateFlow<AdbStatus>(AdbStatus.Disconnected)
    val usbDeviceName = MutableStateFlow<String?>(null)
    val physical = MutableStateFlow(PadState())
    val mappedButtons = MutableStateFlow(0)
    val rumble = MutableStateFlow(RumbleState())
    val counters = MutableStateFlow(BridgeCounters())
    val log = MutableStateFlow<List<String>>(emptyList())
    val daemonVersion = MutableStateFlow<String?>(null)

    private const val MAX_LOG_LINES = 300
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(tag: String, message: String) {
        Log.i("NeuroPad/$tag", message)
        val line = synchronized(timeFormat) { timeFormat.format(Date()) } + "  [$tag] $message"
        log.update { (it + line).takeLast(MAX_LOG_LINES) }
    }

    fun resetSession() {
        physical.value = PadState()
        mappedButtons.value = 0
        rumble.value = RumbleState()
        counters.value = BridgeCounters()
        daemonVersion.value = null
    }
}
