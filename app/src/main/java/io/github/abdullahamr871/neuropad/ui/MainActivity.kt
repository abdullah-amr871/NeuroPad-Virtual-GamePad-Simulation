package io.github.abdullahamr871.neuropad.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.abdullahamr871.neuropad.bridge.AccessMode
import io.github.abdullahamr871.neuropad.bridge.BridgeState
import io.github.abdullahamr871.neuropad.bridge.GamepadBridgeService
import io.github.abdullahamr871.neuropad.core.Buttons

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeuroPadTheme {
                var tab by rememberSaveable { mutableIntStateOf(AppViewModel.TAB_HOME) }
                LaunchedEffect(Unit) { vm.navigate.collect { tab = it } }
                LaunchedEffect(Unit) {
                    vm.messages.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() }
                }
                // A phone sitting in a gamepad grip is held sideways, so the app turns with it
                // for as long as a gamepad is plugged in, whatever the auto-rotate setting says.
                val bridgedPad by BridgeState.usbDeviceName.collectAsStateWithLifecycle()
                val padPresent = bridgedPad != null || vm.detectedPad != null
                LaunchedEffect(padPresent) {
                    requestedOrientation = if (padPresent) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }

                val items = listOf(
                    "Home" to Icons.Rounded.Home,
                    "Controller" to Icons.Rounded.SportsEsports,
                    "Connection" to Icons.Rounded.Link,
                    "Settings" to Icons.Rounded.Tune,
                    "Support" to Icons.Rounded.Favorite,
                )
                val screen: @Composable () -> Unit = {
                    when (tab) {
                        AppViewModel.TAB_HOME -> HomeScreen(vm, onStart = ::startBridge) { tab = it }
                        AppViewModel.TAB_CONTROLLER -> ControllerScreen(vm)
                        AppViewModel.TAB_CONNECTION -> ConnectionScreen(vm)
                        AppViewModel.TAB_SUPPORT -> SupportScreen(vm)
                        else -> SettingsScreen(vm, onQuit = ::quit)
                    }
                }
                val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                if (landscape) {
                    // Sideways there is little height: navigation moves to a rail on the side.
                    Row(Modifier.fillMaxSize().background(Np.Background).safeDrawingPadding()) {
                        NavigationRail(containerColor = Np.Surface) {
                            Spacer(Modifier.weight(1f))
                            items.forEachIndexed { index, (label, icon) ->
                                NavigationRailItem(
                                    selected = tab == index,
                                    onClick = { tab = index },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                    colors = NavigationRailItemDefaults.colors(
                                        indicatorColor = Np.Violet.copy(alpha = 0.25f),
                                        selectedIconColor = Np.Text,
                                        selectedTextColor = Np.Text,
                                        unselectedIconColor = Np.TextDim,
                                        unselectedTextColor = Np.TextDim,
                                    ),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }
                        Box(Modifier.fillMaxSize()) { screen() }
                    }
                } else {
                    Scaffold(
                        containerColor = Np.Background,
                        bottomBar = {
                            NavigationBar(containerColor = Np.Surface) {
                                items.forEachIndexed { index, (label, icon) ->
                                    NavigationBarItem(
                                        selected = tab == index,
                                        onClick = { tab = index },
                                        icon = { Icon(icon, contentDescription = label) },
                                        label = { Text(label, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                        colors = NavigationBarItemDefaults.colors(
                                            indicatorColor = Np.Violet.copy(alpha = 0.25f),
                                            selectedIconColor = Np.Text,
                                            selectedTextColor = Np.Text,
                                            unselectedIconColor = Np.TextDim,
                                            unselectedTextColor = Np.TextDim,
                                        ),
                                    )
                                }
                            }
                        },
                    ) { padding ->
                        Box(Modifier.fillMaxSize().background(Np.Background).padding(padding)) { screen() }
                    }
                }
                vm.busy?.let { text ->
                    AlertDialog(
                        onDismissRequest = {},
                        confirmButton = {},
                        containerColor = Np.SurfaceHigh,
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(strokeWidth = 3.dp)
                                Spacer(Modifier.width(16.dp))
                                Text(text)
                            }
                        },
                    )
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) vm.onGamepadAttached()
        // The PC installer opens port 5555 and then launches the app with this hint, so the
        // matching access mode is already selected when the user first sees the app.
        if (intent?.getStringExtra(EXTRA_MODE) == "port5555") {
            intent.removeExtra(EXTRA_MODE)
            vm.selectAccessMode(AccessMode.PORT_5555)
        }
    }

    /** Keeps "is a gamepad plugged in" current while the app is on screen. */
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // The device list is updated slightly after the broadcast on some phones.
            val attached = intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED
            window.decorView.postDelayed({ if (attached) vm.onGamepadAttached() else vm.refreshUsb() }, 300)
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(this, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        try {
            unregisterReceiver(usbReceiver)
        } catch (_: Exception) {
        }
        vm.onPause()
        super.onPause()
    }

    /** Starting needs a visible notification on Android 13+, so ask for it the first time. */
    private fun startBridge() {
        if (Build.VERSION.SDK_INT >= 33 && !BridgeState.status.value.isActive &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.toggleBridge()
    }

    private companion object {
        const val EXTRA_MODE = "neuropad_mode"
    }

    private fun quit() {
        GamepadBridgeService.stop(this)
        finishAndRemoveTask()
    }

    /**
     * While the mapping editor waits for a button and the bridge is not running, the pad is
     * still an ordinary Android controller, so its key events are used for the capture.
     */
    @SuppressLint("RestrictedApi") // lint false positive for ComponentActivity.dispatchKeyEvent
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val target = vm.captureTarget
        if (target != null && GamepadBridgeService.isRunning && KeyEvent.isGamepadButton(event.keyCode)) {
            return true // the capture reads the raw pad; keep the virtual pad from clicking the UI
        }
        if (target != null) {
            val physical = when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> Buttons.A
                KeyEvent.KEYCODE_BUTTON_B -> Buttons.B
                KeyEvent.KEYCODE_BUTTON_X -> Buttons.X
                KeyEvent.KEYCODE_BUTTON_Y -> Buttons.Y
                KeyEvent.KEYCODE_BUTTON_L1 -> Buttons.LB
                KeyEvent.KEYCODE_BUTTON_R1 -> Buttons.RB
                KeyEvent.KEYCODE_BUTTON_L2 -> Buttons.LT
                KeyEvent.KEYCODE_BUTTON_R2 -> Buttons.RT
                KeyEvent.KEYCODE_BUTTON_SELECT -> Buttons.SELECT
                KeyEvent.KEYCODE_BUTTON_START -> Buttons.START
                KeyEvent.KEYCODE_BUTTON_THUMBL -> Buttons.L3
                KeyEvent.KEYCODE_BUTTON_THUMBR -> Buttons.R3
                KeyEvent.KEYCODE_BUTTON_MODE -> Buttons.GUIDE
                else -> -1
            }
            if (physical >= 0) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) vm.mapButton(physical, target)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
