package io.github.abdullahamr871.neuropad.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.abdullahamr871.neuropad.R
import io.github.abdullahamr871.neuropad.bridge.AccessMode
import io.github.abdullahamr871.neuropad.bridge.AdbStatus
import io.github.abdullahamr871.neuropad.bridge.BridgeState
import io.github.abdullahamr871.neuropad.bridge.BridgeStatus
import io.github.abdullahamr871.neuropad.bridge.RumbleSource
import io.github.abdullahamr871.neuropad.core.EmulationProfile

@Composable
fun ScreenColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

@Composable
fun HomeScreen(vm: AppViewModel, onStart: () -> Unit, onNavigate: (Int) -> Unit) {
    val status by BridgeState.status.collectAsStateWithLifecycle()
    val adb by BridgeState.adb.collectAsStateWithLifecycle()
    val connectedPad by BridgeState.usbDeviceName.collectAsStateWithLifecycle()
    val rumble by BridgeState.rumble.collectAsStateWithLifecycle()

    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Np.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("NeuroPad", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Rumble for USB gamepads", color = Np.TextDim, fontSize = 13.sp)
            }
        }

        PowerCard(status, onStart)

        Panel(title = "Checklist") {
            val shellTone: Tone
            val shellDetail: String
            when {
                vm.accessMode == AccessMode.ROOT -> {
                    shellTone = Tone.GOOD
                    shellDetail = "Root mode"
                }
                adb is AdbStatus.Connected -> {
                    shellTone = Tone.GOOD
                    shellDetail = "${vm.accessMode.label}, connected"
                }
                adb is AdbStatus.Connecting -> {
                    shellTone = Tone.WARN
                    shellDetail = "Connecting"
                }
                adb is AdbStatus.Failed -> {
                    shellTone = Tone.BAD
                    shellDetail = "${vm.accessMode.label}: ${(adb as AdbStatus.Failed).message}"
                }
                else -> {
                    shellTone = Tone.IDLE
                    shellDetail = "${vm.accessMode.label}, not connected yet"
                }
            }
            Box(Modifier.clickable { onNavigate(AppViewModel.TAB_CONNECTION) }) {
                StatusRow("System access", shellDetail, shellTone) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Open connection", tint = Np.TextDim)
                }
            }
            val pad = connectedPad ?: vm.detectedPad
            StatusRow(
                "USB gamepad",
                when {
                    connectedPad != null -> "$connectedPad, bridged"
                    pad != null -> "$pad, detected"
                    else -> "Plug your gamepad into the phone"
                },
                when {
                    connectedPad != null -> Tone.GOOD
                    pad != null -> Tone.INFO
                    else -> Tone.IDLE
                },
            )
            StatusRow(
                "Virtual controller",
                if (status is BridgeStatus.Running) "${vm.profile.label}, visible to games" else vm.profile.label,
                if (status is BridgeStatus.Running) Tone.GOOD else Tone.IDLE,
            )
        }

        Panel(title = "Appear to games as") {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EmulationProfile.entries.forEach { profile ->
                    ProfileChip(profile, selected = profile == vm.profile) { vm.selectProfile(profile) }
                }
            }
            Hint(
                when (vm.profile) {
                    EmulationProfile.XBOX_360 -> "Most compatible layout. Try a wireless profile if a game shows no rumble."
                    EmulationProfile.XBOX_WIRELESS, EmulationProfile.XBOX_SERIES ->
                        "Seen as a Bluetooth Xbox pad. Trigger rumble is blended into the main motors."
                    EmulationProfile.DUALSHOCK4 -> "PlayStation button prompts. Rumble depends on the kernel driver of your phone."
                    EmulationProfile.DUALSENSE -> "PlayStation 5 pad over the hid-playstation driver, where the kernel has it."
                },
            )
        }

        Panel(
            title = "Rumble",
            trailing = {
                when (rumble.source) {
                    RumbleSource.CONTROLLER_RUMBLE -> Pill("from game", Tone.GOOD)
                    RumbleSource.PHONE_VIBRATION -> Pill("phone vibration", Tone.INFO)
                    RumbleSource.TEST -> Pill("test", Tone.WARN)
                    RumbleSource.NONE -> Unit
                }
            },
        ) {
            Meter("Heavy motor (left)", rumble.left)
            Meter("Light motor (right)", rumble.right)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { vm.testRumble(255, 0) }, modifier = Modifier.weight(1f)) { Text("Heavy") }
                FilledTonalButton(onClick = { vm.testRumble(0, 255) }, modifier = Modifier.weight(1f)) { Text("Light") }
                FilledTonalButton(onClick = { vm.testRumble(255, 255) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Vibration, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Both")
                }
            }
        }
    }
}

@Composable
private fun PowerCard(status: BridgeStatus, onToggle: () -> Unit) {
    val active = status is BridgeStatus.Running
    val starting = status is BridgeStatus.Starting
    // The halo grows once when the bridge comes up. (A never-ending pulse would keep the
    // screen redrawing, and the GPU busy, for as long as the app is open during a game.)
    val halo by animateFloatAsState(if (active) 1.06f else 0.9f, tween(500), label = "halo")
    Panel {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(156.dp).scale(halo).clip(CircleShape)
                        .background(if (active) Np.AccentSoft else Brush.linearGradient(listOf(Np.SurfaceHigh, Np.SurfaceHigh))),
                )
                Box(
                    Modifier.size(124.dp).clip(CircleShape)
                        .background(if (active) Np.Accent else Brush.linearGradient(listOf(Np.SurfaceHigh, Np.Surface)))
                        .border(2.dp, if (active) Color.Transparent else Np.Outline, CircleShape)
                        .clickable(role = Role.Switch, onClickLabel = if (active) "Stop the bridge" else "Start the bridge", onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    if (starting) {
                        CircularProgressIndicator(color = Np.Cyan, strokeWidth = 4.dp, modifier = Modifier.size(54.dp))
                    } else {
                        Icon(
                            Icons.Rounded.PowerSettingsNew,
                            contentDescription = null,
                            tint = if (active) Color.White else Np.TextDim,
                            modifier = Modifier.size(58.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                when (status) {
                    is BridgeStatus.Running -> "Bridge is active"
                    is BridgeStatus.Starting -> "Starting"
                    is BridgeStatus.Failed -> "Could not start"
                    BridgeStatus.Stopped -> "Bridge is off"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (status is BridgeStatus.Failed) Np.Red else Np.Text,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when (status) {
                    is BridgeStatus.Running -> "Games now see a ${status.deviceName} with working rumble."
                    is BridgeStatus.Starting -> status.step
                    is BridgeStatus.Failed -> status.message
                    BridgeStatus.Stopped -> "Tap the button to give your USB gamepad rumble."
                },
                color = Np.TextDim,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
private fun ProfileChip(profile: EmulationProfile, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.clip(shape)
            .background(if (selected) Np.AccentSoft else Brush.linearGradient(listOf(Np.SurfaceHigh, Np.SurfaceHigh)))
            .border(1.dp, if (selected) Np.Violet else Np.Outline, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(profile.shortLabel, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(
            if (profile.bus == "usb") "USB" else "Wireless",
            color = if (selected) Np.Cyan else Np.TextDim,
            fontSize = 11.sp,
        )
    }
}

@Composable
fun Meter(label: String, value: Int, max: Int = 255) {
    val fraction by animateFloatAsState((value.toFloat() / max).coerceIn(0f, 1f), tween(90), label = "meter")
    Column {
        Row {
            Text(label, color = Np.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("$value", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Np.SurfaceHigh)) {
            Box(Modifier.fillMaxWidth(fraction).height(10.dp).clip(RoundedCornerShape(50)).background(Np.Accent))
        }
    }
}
