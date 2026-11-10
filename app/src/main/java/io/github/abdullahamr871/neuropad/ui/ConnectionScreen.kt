package io.github.abdullahamr871.neuropad.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.abdullahamr871.neuropad.adb.AdbPairingService
import io.github.abdullahamr871.neuropad.bridge.AccessMode
import io.github.abdullahamr871.neuropad.bridge.AdbStatus
import io.github.abdullahamr871.neuropad.bridge.BridgeState

@Composable
fun ConnectionScreen(vm: AppViewModel) {
    val adb by BridgeState.adb.collectAsStateWithLifecycle()
    val pairing by AdbPairingService.state.collectAsStateWithLifecycle()
    val pairingMessage by AdbPairingService.message.collectAsStateWithLifecycle()
    var showPairDialog by remember { mutableStateOf(false) }
    var showPortDialog by remember { mutableStateOf(false) }

    ScreenColumn {
        Panel(title = "How NeuroPad gets system access") {
            Hint(
                "Creating a virtual controller needs the rights of the Android shell user. " +
                    "Pick the way that suits your phone. Nothing leaves the device: the app only talks to 127.0.0.1.",
            )
            ModeOption(
                "Wireless debugging", "Android 11 or newer. Pair once, no computer needed.",
                vm.accessMode == AccessMode.WIRELESS_DEBUGGING,
            ) { vm.selectAccessMode(AccessMode.WIRELESS_DEBUGGING) }
            ModeOption(
                "PC port 5555", "Any Android version. A computer opens the port after every reboot.",
                vm.accessMode == AccessMode.PORT_5555,
            ) { vm.selectAccessMode(AccessMode.PORT_5555) }
            ModeOption(
                "Root", "Rooted phones. No Wi-Fi, pairing or computer required.",
                vm.accessMode == AccessMode.ROOT,
            ) { vm.selectAccessMode(AccessMode.ROOT) }
        }

        if (vm.accessMode != AccessMode.ROOT) {
            Panel(
                title = "ADB link",
                trailing = {
                    when (val s = adb) {
                        is AdbStatus.Connected -> Pill("connected, port ${s.port}", Tone.GOOD)
                        AdbStatus.Connecting -> Pill("connecting", Tone.WARN)
                        is AdbStatus.Failed -> Pill("failed", Tone.BAD)
                        AdbStatus.Disconnected -> Pill("not connected", Tone.IDLE)
                    }
                },
            ) {
                (adb as? AdbStatus.Failed)?.let { Text(it.message, color = Np.Red, fontSize = 13.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.connectAdb() }, modifier = Modifier.weight(1f)) { Text("Connect") }
                    if (vm.accessMode == AccessMode.WIRELESS_DEBUGGING) {
                        OutlinedButton(onClick = { showPortDialog = true }, modifier = Modifier.weight(1f)) {
                            Text("Enter port")
                        }
                    }
                }
            }
        }

        when (vm.accessMode) {
            AccessMode.WIRELESS_DEBUGGING -> {
                Panel(
                    title = "Pair with Wireless debugging",
                    trailing = { if (vm.paired) Pill("paired", Tone.GOOD) },
                ) {
                    if (Build.VERSION.SDK_INT < 30) {
                        Text("Wireless debugging needs Android 11. Use PC port 5555 or Root on this phone.", color = Np.Amber, fontSize = 13.sp)
                    }
                    Step(1, "Connect to Wi-Fi, open Developer options and turn on Wireless debugging.")
                    Step(2, "Tap the Wireless debugging text itself, then \"Pair device with pairing code\".")
                    Step(3, "A NeuroPad notification appears. Type the six digit code into it.")
                    Step(4, "Come back here. The connection is made automatically.")
                    when (pairing) {
                        AdbPairingService.State.SEARCHING -> Pill("waiting for the pairing dialog", Tone.WARN)
                        AdbPairingService.State.FOUND -> Pill("enter the code in the notification", Tone.INFO)
                        AdbPairingService.State.PAIRING -> Pill("pairing", Tone.WARN)
                        AdbPairingService.State.FAILED -> Text(pairingMessage, color = Np.Red, fontSize = 13.sp)
                        else -> Unit
                    }
                    Button(onClick = vm::startNotificationPairing, modifier = Modifier.fillMaxWidth()) {
                        Text("Start pairing")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showPairDialog = true }, modifier = Modifier.weight(1f)) {
                            Text("Pair manually")
                        }
                        OutlinedButton(onClick = vm::openDeveloperOptions, modifier = Modifier.weight(1f)) {
                            Text("Developer options")
                        }
                    }
                    Hint("If pairing hangs on MIUI / HyperOS, disable battery optimisation for NeuroPad in Settings.")
                }
                Panel(title = "No Wi-Fi around?") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Offline mode (mobile hotspot)", fontWeight = FontWeight.Medium)
                            Hint("Wireless debugging also works on the network of your own hotspot.")
                        }
                        Switch(checked = vm.hotspotOffline, onCheckedChange = vm::updateHotspotOffline)
                    }
                    if (vm.hotspotOffline) {
                        Hint("Turn the hotspot on first, then enable Wireless debugging and connect.")
                        OutlinedButton(onClick = vm::openHotspotSettings, modifier = Modifier.fillMaxWidth()) {
                            Text("Open hotspot settings")
                        }
                    }
                }
            }
            AccessMode.PORT_5555 -> Panel(title = "Set up with a computer") {
                Step(1, "Enable USB debugging and connect the phone to a computer.")
                Step(2, "Run the one-click installer, or the activator script, on the computer.")
                Step(3, "Unplug the cable and tap Connect above.")
                Hint("Android closes port 5555 on every reboot. Run the activator again afterwards; the app itself stays installed.")
                OutlinedButton(onClick = vm::shareActivatorScript, modifier = Modifier.fillMaxWidth()) {
                    Text("Share PC activator script")
                }
                TextButton(onClick = { vm.openUrl(AppViewModel.REPO_URL + "/releases/latest") }) {
                    Text("Get the one-click installer")
                }
            }
            AccessMode.ROOT -> Panel(title = "Root mode") {
                StatusRow("Superuser access granted", "The driver runs through su. No ADB link is needed.", Tone.GOOD)
                Hint("Keep NeuroPad allowed in your root manager (Magisk, KernelSU, APatch).")
            }
        }
    }

    if (showPairDialog) {
        var port by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPairDialog = false },
            containerColor = Np.SurfaceHigh,
            title = { Text("Pair manually") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Hint("Both values are shown under \"Pair device with pairing code\". Use split screen so the dialog stays open.")
                    NumberField(port, "Pairing port (after the colon)") { port = it }
                    NumberField(code, "Six digit pairing code") { code = it }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = (port.toIntOrNull() ?: 0) in 1024..65535 && code.length >= 6,
                    onClick = {
                        showPairDialog = false
                        vm.pairManually(port.toInt(), code)
                    },
                ) { Text("Pair") }
            },
            dismissButton = { TextButton(onClick = { showPairDialog = false }) { Text("Cancel") } },
        )
    }

    if (showPortDialog) {
        var port by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPortDialog = false },
            containerColor = Np.SurfaceHigh,
            title = { Text("Connection port") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Hint("Shown on the Wireless debugging screen as \"IP address & Port\", for example 192.168.1.20:39423. Enter the number after the colon.")
                    NumberField(port, "Port, e.g. 39423") { port = it }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = (port.toIntOrNull() ?: 0) in 1024..65535,
                    onClick = {
                        showPortDialog = false
                        vm.connectAdb(port.toInt())
                    },
                ) { Text("Connect") }
            },
            dismissButton = { TextButton(onClick = { showPortDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NumberField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter(Char::isDigit).take(6)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ModeOption(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) Np.Violet.copy(alpha = 0.14f) else Np.SurfaceHigh)
            .border(1.dp, if (selected) Np.Violet else Np.Outline, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Np.TextDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row {
        Text(
            "$number",
            color = Np.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.size(22.dp).clip(CircleShape).background(Np.Violet.copy(alpha = 0.35f)).padding(top = 3.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp, lineHeight = 19.sp)
    }
}
