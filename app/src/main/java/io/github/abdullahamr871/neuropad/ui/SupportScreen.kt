package io.github.abdullahamr871.neuropad.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val KofiRed = Color(0xFFFF5E5B)
private val PayPalBlue = Color(0xFF0070E0)
private val Gold = Color(0xFFFFC94D)

/** The Support tab: donation links, what they pay for, and free ways to help. */
@Composable
fun SupportScreen(vm: AppViewModel) {
    TwoPane(first = {
        SupportHero()
        DonateButton(
            icon = Icons.Rounded.LocalCafe,
            title = "Buy me a coffee on Ko-fi",
            subtitle = "One-time or monthly, any amount",
            brand = KofiRed,
        ) { vm.openUrl(AppViewModel.KOFI_URL) }
        DonateButton(
            icon = Icons.Rounded.Payments,
            title = "Donate with PayPal",
            subtitle = "paypal.me/abdullahamr871",
            brand = PayPalBlue,
        ) { vm.openUrl(AppViewModel.PAYPAL_URL) }
    }, second = {
        Panel(title = "Where your support goes") {
            GoalRow(Icons.Rounded.SportsEsports, Np.Cyan, "More controllers", "Test and confirm rumble on more gamepads")
            GoalRow(Icons.Rounded.Memory, Np.Green, "RTX 5060 Ti", "Faster builds and AI research")
            GoalRow(Icons.Rounded.DeveloperBoard, Np.Amber, "Raspberry Pi", "Hardware and controller experiments")
            GoalRow(Icons.Rounded.School, Np.Violet, "PhD in the USA", "The long-term goal behind all of this")
        }

        Panel(title = "Free ways to help") {
            LinkRow(Icons.Rounded.Star, Gold, "Star NeuroPad on GitHub", "Helps other gamers find it") {
                vm.openUrl(AppViewModel.REPO_URL)
            }
            LinkRow(Icons.Rounded.Forum, Np.Cyan, "Tell us your controller", "Add it to the tested list in Discussions") {
                vm.openUrl(AppViewModel.REPO_URL + "/discussions")
            }
        }

        Text(
            "NeuroPad stays free and open source, with or without a donation. Thank you for using it!",
            color = Np.TextDim,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    })
}

@Composable
private fun SupportHero() {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Np.Accent).padding(20.dp),
    ) {
        Column {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("Support NeuroPad", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Built by one developer in free time. If NeuroPad brought rumble back to your controller, " +
                    "a small donation keeps it growing.",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun DonateButton(icon: ImageVector, title: String, subtitle: String, brand: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(brand)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = brand, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun GoalRow(icon: ImageVector, tint: Color, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(detail, color = Np.TextDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, tint: Color, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(detail, color = Np.TextDim, fontSize = 13.sp)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Np.TextDim)
    }
}

/** A small card on the Home screen that leads to the Support tab. */
@Composable
fun SupportBanner(onOpen: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Np.AccentSoft)
            .border(BorderStroke(1.dp, Np.Violet.copy(alpha = 0.5f)), shape)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(KofiRed),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Enjoying NeuroPad?", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text("Support development with Ko-fi or PayPal", color = Np.TextDim, fontSize = 13.sp)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = "Open support", tint = Np.Text)
    }
}
