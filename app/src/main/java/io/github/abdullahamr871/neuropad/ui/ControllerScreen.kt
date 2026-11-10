package io.github.abdullahamr871.neuropad.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.abdullahamr871.neuropad.bridge.BridgeState
import io.github.abdullahamr871.neuropad.bridge.BridgeStatus
import io.github.abdullahamr871.neuropad.core.Buttons
import io.github.abdullahamr871.neuropad.core.LayoutMode
import io.github.abdullahamr871.neuropad.core.PadState

@Composable
fun ControllerScreen(vm: AppViewModel) {
    val state by BridgeState.physical.collectAsStateWithLifecycle()
    val mapped by BridgeState.mappedButtons.collectAsStateWithLifecycle()
    val counters by BridgeState.counters.collectAsStateWithLifecycle()
    val status by BridgeState.status.collectAsStateWithLifecycle()
    val names = vm.profile.buttonNames

    // Mapping capture while the bridge runs: the first physical button that goes down wins.
    val target = vm.captureTarget
    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        var previous = BridgeState.physical.value.buttons
        BridgeState.physical.collect { now ->
            val pressed = now.buttons and previous.inv()
            previous = now.buttons
            if (pressed != 0) vm.mapButton(Integer.numberOfTrailingZeros(pressed), target)
        }
    }

    ScreenColumn {
        Panel(
            title = "Live input",
            trailing = {
                if (status is BridgeStatus.Running) Pill("live", Tone.GOOD) else Pill("bridge off", Tone.IDLE)
            },
        ) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1.9f)) { drawPad(state, mapped) }
            val pressed = names.filterIndexed { i, _ -> mapped and (1 shl i) != 0 }
            Text(
                if (pressed.isEmpty()) "No buttons pressed" else pressed.joinToString("  +  "),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Hint("Sticks L %d,%d  R %d,%d   Triggers %d / %d   D-pad %s".format(
                state.lx, state.ly, state.rx, state.ry, state.lt, state.rt, hatName(state.hat),
            ))
            if (status !is BridgeStatus.Running) Hint("Start the bridge on the Home tab to see your gamepad here.")
        }

        Panel(title = "Button layout") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LayoutMode.entries.forEach { mode ->
                    FilterChip(
                        selected = vm.layoutMode == mode,
                        onClick = { vm.selectLayout(mode) },
                        label = { Text(mode.label.substringBefore(" (")) },
                    )
                }
            }
            Hint(
                when (vm.layoutMode) {
                    LayoutMode.DEFAULT -> "Buttons are passed through unchanged."
                    LayoutMode.NINTENDO -> "A/B and X/Y are swapped, for pads with the Nintendo arrangement."
                    LayoutMode.CUSTOM -> "Tap a virtual button below, then press the physical button that should trigger it."
                },
            )
        }

        if (vm.layoutMode == LayoutMode.CUSTOM) {
            Panel(
                title = "Custom mapping",
                trailing = { TextButton(onClick = vm::resetMapping) { Text("Reset") } },
            ) {
                for (virtual in 0 until Buttons.COUNT) {
                    val sources = (0 until Buttons.COUNT).filter { vm.customMapping[it] == virtual }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Np.SurfaceHigh)
                            .clickable { vm.captureTarget = virtual }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(names[virtual], fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(
                            if (sources.isEmpty()) "not mapped" else sources.joinToString { Buttons.XBOX_NAMES[it] },
                            color = if (sources.isEmpty()) Np.Amber else Np.Cyan,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        Panel(title = "Bridge statistics") {
            StatLine("USB packets received", counters.usbPackets.toString())
            StatLine("Reports sent to Android", counters.reportsSent.toString())
            StatLine("Rumble commands received", counters.rumbleReports.toString())
            StatLine("Processing time per packet", "%.2f ms".format(counters.bridgeLatencyMs))
            if (counters.lastRawPacket.isNotEmpty()) {
                Text(
                    counters.lastRawPacket,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Np.TextDim,
                    modifier = Modifier.fillMaxWidth().border(1.dp, Np.Outline, RoundedCornerShape(8.dp)).padding(8.dp),
                )
            }
        }
    }

    if (target != null) {
        AlertDialog(
            onDismissRequest = { vm.captureTarget = null },
            containerColor = Np.SurfaceHigh,
            title = { Text("Map ${names[target]}") },
            text = { Text("Press the button on your gamepad that should act as ${names[target]}.") },
            confirmButton = { TextButton(onClick = { vm.captureTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row {
        Text(label, color = Np.TextDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

private fun hatName(hat: Int) =
    listOf("up", "up-right", "right", "down-right", "down", "down-left", "left", "up-left").getOrElse(hat) { "centre" }

/** Schematic gamepad that lights up with the current input. */
private fun DrawScope.drawPad(state: PadState, mapped: Int) {
    val w = size.width
    val h = size.height
    val on = Np.Cyan
    val off = Np.Outline
    fun pressed(button: Int) = mapped and (1 shl button) != 0
    fun color(button: Int) = if (pressed(button)) on else off

    // Triggers (fill follows the analog travel) and bumpers
    val barW = w * 0.2f
    val barH = h * 0.07f
    for ((x, travel, bumper) in listOf(
        Triple(w * 0.1f, state.lt, Buttons.LB),
        Triple(w * 0.7f, state.rt, Buttons.RB),
    )) {
        drawRoundRect(off, Offset(x, 0f), Size(barW, barH), CornerRadius(barH / 2))
        drawRoundRect(Np.Violet, Offset(x, 0f), Size(barW * travel / 255f, barH), CornerRadius(barH / 2))
        drawRoundRect(color(bumper), Offset(x, barH * 1.5f), Size(barW, barH), CornerRadius(barH / 2))
    }

    // Body
    val top = h * 0.26f
    drawRoundRect(Np.SurfaceHigh, Offset(0f, top), Size(w, h - top), CornerRadius(h * 0.3f))
    drawRoundRect(off, Offset(0f, top), Size(w, h - top), CornerRadius(h * 0.3f), style = Stroke(2f))

    // Sticks
    val stickR = h * 0.15f
    fun stick(cx: Float, cy: Float, x: Int, y: Int, click: Int) {
        drawCircle(if (pressed(click)) on.copy(alpha = 0.35f) else Np.Background, stickR, Offset(cx, cy))
        drawCircle(off, stickR, Offset(cx, cy), style = Stroke(2f))
        val dx = (x - 128) / 128f * stickR * 0.6f
        val dy = (y - 128) / 128f * stickR * 0.6f
        drawCircle(Np.Violet, stickR * 0.45f, Offset(cx + dx, cy + dy))
    }
    stick(w * 0.2f, h * 0.52f, state.lx, state.ly, Buttons.L3)
    stick(w * 0.62f, h * 0.76f, state.rx, state.ry, Buttons.R3)

    // D-pad
    val dc = Offset(w * 0.36f, h * 0.76f)
    val arm = h * 0.075f
    val up = state.hat in intArrayOf(7, 0, 1)
    val right = state.hat in 1..3
    val down = state.hat in 3..5
    val left = state.hat in 5..7
    fun arm(dx: Float, dy: Float, active: Boolean) = drawRoundRect(
        if (active) on else off,
        Offset(dc.x + dx * arm * 1.1f - arm / 2, dc.y + dy * arm * 1.1f - arm / 2),
        Size(arm, arm), CornerRadius(arm * 0.2f),
    )
    arm(0f, -1f, up); arm(1f, 0f, right); arm(0f, 1f, down); arm(-1f, 0f, left)
    drawRoundRect(off, Offset(dc.x - arm / 2, dc.y - arm / 2), Size(arm, arm))

    // Face buttons (A bottom, B right, X left, Y top)
    val fc = Offset(w * 0.8f, h * 0.52f)
    val gap = h * 0.11f
    val r = h * 0.05f
    drawCircle(color(Buttons.A), r, Offset(fc.x, fc.y + gap))
    drawCircle(color(Buttons.B), r, Offset(fc.x + gap, fc.y))
    drawCircle(color(Buttons.X), r, Offset(fc.x - gap, fc.y))
    drawCircle(color(Buttons.Y), r, Offset(fc.x, fc.y - gap))

    // Centre buttons: Select, Guide, Start, Share
    val cy = h * 0.46f
    val small = Size(w * 0.05f, h * 0.045f)
    drawRoundRect(color(Buttons.SELECT), Offset(w * 0.40f, cy), small, CornerRadius(small.height / 2))
    drawRoundRect(color(Buttons.START), Offset(w * 0.55f, cy), small, CornerRadius(small.height / 2))
    drawCircle(if (pressed(Buttons.GUIDE)) on else Color(0xFF3A4668), h * 0.045f, Offset(w * 0.5f, cy - h * 0.06f))
    drawCircle(color(Buttons.SHARE), h * 0.028f, Offset(w * 0.5f, cy + h * 0.07f))
}
