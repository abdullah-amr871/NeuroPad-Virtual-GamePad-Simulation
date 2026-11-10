package io.github.abdullahamr871.neuropad.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** NeuroPad palette: deep navy surfaces with a violet-to-cyan accent. */
object Np {
    val Background = Color(0xFF0A0E1A)
    val Surface = Color(0xFF131A2B)
    val SurfaceHigh = Color(0xFF1B2438)
    val Outline = Color(0xFF2A3550)
    val Violet = Color(0xFF7C5CFF)
    val Cyan = Color(0xFF22D3EE)
    val Green = Color(0xFF34D399)
    val Amber = Color(0xFFFBBF24)
    val Red = Color(0xFFF87171)
    val Text = Color(0xFFE8ECF6)
    val TextDim = Color(0xFF94A0BC)
    val Accent = Brush.linearGradient(listOf(Violet, Cyan))
    val AccentSoft = Brush.linearGradient(listOf(Violet.copy(alpha = 0.28f), Cyan.copy(alpha = 0.18f)))
}

private val Scheme = darkColorScheme(
    primary = Np.Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2B2466),
    onPrimaryContainer = Color(0xFFE4DEFF),
    secondary = Np.Cyan,
    onSecondary = Color(0xFF00232B),
    secondaryContainer = Color(0xFF123B4A),
    onSecondaryContainer = Color(0xFFC9F5FF),
    tertiary = Np.Green,
    background = Np.Background,
    onBackground = Np.Text,
    surface = Np.Surface,
    onSurface = Np.Text,
    surfaceVariant = Np.SurfaceHigh,
    onSurfaceVariant = Np.TextDim,
    surfaceContainer = Np.Surface,
    surfaceContainerHigh = Np.SurfaceHigh,
    surfaceContainerHighest = Np.SurfaceHigh,
    surfaceContainerLow = Np.Surface,
    outline = Np.Outline,
    outlineVariant = Np.Outline,
    error = Np.Red,
)

@Composable
fun NeuroPadTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme) {
        CompositionLocalProvider(LocalContentColor provides Np.Text, content = content)
    }
}

/** The standard container of every section. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Np.Surface),
        border = BorderStroke(1.dp, Np.Outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title.uppercase(),
                        color = Np.TextDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.weight(1f),
                    )
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

enum class Tone(val color: Color) { GOOD(Np.Green), WARN(Np.Amber), BAD(Np.Red), IDLE(Np.TextDim), INFO(Np.Cyan) }

@Composable
fun StatusDot(tone: Tone, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).clip(CircleShape).background(tone.color))
}

@Composable
fun Pill(text: String, tone: Tone) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(tone.color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(tone, Modifier.size(7.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = tone.color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** A labelled status line: coloured dot, title, detail underneath. */
@Composable
fun StatusRow(title: String, detail: String, tone: Tone, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(tone)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(detail, color = Np.TextDim, fontSize = 13.sp)
        }
        trailing?.invoke()
    }
}

@Composable
fun Hint(text: String) {
    Text(text, color = Np.TextDim, fontSize = 13.sp, lineHeight = 18.sp)
}
