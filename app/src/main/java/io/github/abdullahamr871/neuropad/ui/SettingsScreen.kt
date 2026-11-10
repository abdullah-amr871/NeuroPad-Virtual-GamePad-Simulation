package io.github.abdullahamr871.neuropad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.abdullahamr871.neuropad.BuildConfig
import io.github.abdullahamr871.neuropad.bridge.BridgeState
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: AppViewModel, onQuit: () -> Unit) {
    val log by BridgeState.log.collectAsStateWithLifecycle()
    val status by BridgeState.status.collectAsStateWithLifecycle()
    val counters by BridgeState.counters.collectAsStateWithLifecycle()
    LaunchedEffect(status, counters.rumbleReports > 0) { vm.refreshChecks() }

    TwoPane(first = {
        Panel(title = "Rumble") {
            SliderRow(
                "Strength", "%.1fx".format(vm.rumbleGain),
                vm.rumbleGain, 0.5f..8f, steps = 14,
            ) { vm.updateGain((it * 2).roundToInt() / 2f) }
            Hint("Games usually ask for gentle rumble that small motors barely show. 4x is the default.")

            Text("Shortest rumble", fontWeight = FontWeight.Medium)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0 to "Off", 40 to "40 ms", 80 to "80 ms", 120 to "120 ms", 200 to "200 ms").forEach { (ms, label) ->
                    FilterChip(selected = vm.minPulseMs == ms, onClick = { vm.updateMinPulse(ms) }, label = { Text(label) })
                }
            }
            Hint("Games often send ticks of 10-20 ms that a phone can play but gamepad motors cannot. Shorter pulses are stretched to this length.")

            SwitchRow(
                "Ignore system vibration",
                "Android also forwards taps, keyboard clicks and notifications to the controller. This keeps them off the gamepad.",
                vm.muteSystemHaptics, vm::updateMuteSystem,
            )

            Text("Stuck-motor guard", fontWeight = FontWeight.Medium)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0 to "Off", 500 to "0.5 s", 2000 to "2 s", 5000 to "5 s", 10000 to "10 s").forEach { (ms, label) ->
                    FilterChip(selected = vm.rumbleTimeoutMs == ms, onClick = { vm.updateTimeout(ms) }, label = { Text(label) })
                }
            }
            Hint("Stops the motors if a game goes silent without switching rumble off.")
        }

        Panel(title = "Phone-vibration fallback") {
            SwitchRow(
                "Mirror phone vibration",
                "For games that only vibrate the phone. Pauses itself once real controller rumble arrives.",
                vm.phoneFallback, vm::updateFallback,
            )
            if (vm.phoneFallback) {
                SliderRow("Light motor only below", vm.lowThreshold.toString(), vm.lowThreshold.toFloat(), 10f..150f) {
                    vm.updateLow(it.roundToInt().coerceAtMost(vm.highThreshold - 10))
                }
                SliderRow("Both motors at full above", vm.highThreshold.toString(), vm.highThreshold.toFloat(), 60f..250f) {
                    vm.updateHigh(it.roundToInt().coerceAtLeast(vm.lowThreshold + 10))
                }
                SwitchRow("Smooth transitions", "Softens abrupt jumps between vibration steps.", vm.smoothing, vm::updateSmoothing)
            }
        }

        Panel(title = "Controls") {
            SwitchRow(
                "Start when the gamepad is plugged in",
                "The bridge always stops by itself when the gamepad is unplugged. With this on it also starts by itself.",
                vm.autoStart, vm::updateAutoStart,
            )
            SwitchRow(
                "Select + Start = Home",
                "Pressing both together sends the Home / Guide / PS button.",
                vm.homeCombo, vm::updateHomeCombo,
            )
        }

    }, second = {
        Panel(title = "Support NeuroPad 💙") {
            Text("NeuroPad is free and open source.", fontWeight = FontWeight.SemiBold)
            Hint(
                "If it brought rumble back to your controller, a small donation helps me buy more " +
                    "controllers to test and keep improving the app."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.openUrl(AppViewModel.KOFI_URL) },
                    colors = ButtonDefaults.buttonColors(containerColor = Np.Cyan, contentColor = Np.Background),
                    modifier = Modifier.weight(1f),
                ) { Text("☕ Ko-fi") }
                Button(
                    onClick = { vm.openUrl(AppViewModel.PAYPAL_URL) },
                    modifier = Modifier.weight(1f),
                ) { Text("PayPal") }
            }
            OutlinedButton(onClick = { vm.openUrl(AppViewModel.REPO_URL) }, modifier = Modifier.fillMaxWidth()) {
                Text("⭐ Star on GitHub (free)")
            }
        }

        Panel(title = "Background") {
            StatusRow(
                "Battery optimisation",
                if (vm.batteryExempt) "NeuroPad may keep running during long sessions." else "Android may stop the bridge while you play.",
                if (vm.batteryExempt) Tone.GOOD else Tone.WARN,
            )
            if (!vm.batteryExempt) {
                OutlinedButton(onClick = vm::requestBatteryExemption, modifier = Modifier.fillMaxWidth()) {
                    Text("Allow background activity")
                }
            }
            Hint("Tip: add the NeuroPad tile to Quick Settings to switch the bridge from inside a game.")
        }

        Panel(
            title = "Diagnostics",
            trailing = { TextButton(onClick = vm::refreshChecks) { Text("Refresh") } },
        ) {
            vm.checks.forEach { check ->
                StatusRow(
                    check.title, check.detail,
                    when (check.ok) {
                        true -> Tone.GOOD
                        false -> Tone.BAD
                        null -> Tone.IDLE
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::buildReport, modifier = Modifier.weight(1f)) { Text("Full report") }
                OutlinedButton(onClick = vm::shareReport, modifier = Modifier.weight(1f)) { Text("Share report") }
            }
        }

        Panel(title = "Activity log") {
            SelectionContainer {
                Text(
                    if (log.isEmpty()) "Nothing yet." else log.takeLast(40).joinToString("\n"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = Np.TextDim,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(10.dp))
                        .background(Np.Background).verticalScroll(rememberScrollState()).padding(10.dp),
                )
            }
        }

        Panel(title = "About") {
            Text("NeuroPad ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Hint(
                "Brings rumble to USB gamepads on Android by presenting them as a wireless controller. " +
                    "Built for and tested on the GameSir G8 Plus (MFi)."
            )
            Hint("Designed and programmed by Abdullah Amr Maged. Open source under the MIT licence.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.openUrl(AppViewModel.REPO_URL) }, modifier = Modifier.weight(1f)) {
                    Text("GitHub")
                }
                OutlinedButton(onClick = { vm.openUrl(AppViewModel.REPO_URL + "/issues") }, modifier = Modifier.weight(1f)) {
                    Text("Report a problem")
                }
            }
            Button(
                onClick = onQuit,
                colors = ButtonDefaults.buttonColors(containerColor = Np.Red.copy(alpha = 0.18f), contentColor = Np.Red),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Stop the bridge and quit") }
        }
    })

    vm.report?.let { text ->
        AlertDialog(
            onDismissRequest = { vm.report = null },
            containerColor = Np.SurfaceHigh,
            title = { Text("Diagnostics report") },
            text = {
                SelectionContainer {
                    Text(
                        text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = { TextButton(onClick = vm::shareReport) { Text("Share") } },
            dismissButton = { TextButton(onClick = { vm.report = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Hint(detail)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(valueText, color = Np.Cyan, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps)
    }
}
