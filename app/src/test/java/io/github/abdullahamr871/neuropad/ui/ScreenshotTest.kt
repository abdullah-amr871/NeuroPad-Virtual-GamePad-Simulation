package io.github.abdullahamr871.neuropad.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.abdullahamr871.neuropad.bridge.AdbStatus
import io.github.abdullahamr871.neuropad.bridge.BridgeCounters
import io.github.abdullahamr871.neuropad.bridge.BridgeState
import io.github.abdullahamr871.neuropad.bridge.BridgeStatus
import io.github.abdullahamr871.neuropad.bridge.RumbleSource
import io.github.abdullahamr871.neuropad.bridge.RumbleState
import io.github.abdullahamr871.neuropad.core.Buttons
import io.github.abdullahamr871.neuropad.core.EmulationProfile
import io.github.abdullahamr871.neuropad.core.LayoutMode
import io.github.abdullahamr871.neuropad.core.PadState
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen on the JVM (no device needed). It doubles as a smoke test that the
 * screens compose without crashing and as the source of the screenshots in docs/.
 *
 *     ./gradlew :app:testDebugUnitTest -Pscreenshots
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF) // the app ships its own Conscrypt for Android only
@Config(sdk = [34], qualifiers = "w412dp-h1180dp-xhdpi")
class ScreenshotTest {
    private lateinit var vm: AppViewModel

    @Before
    fun setUp() {
        assumeTrue("pass -Pscreenshots to render", System.getProperty("neuropad.screenshots") == "true")
        vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.selectProfile(EmulationProfile.XBOX_WIRELESS)
        BridgeState.status.value = BridgeStatus.Running(EmulationProfile.XBOX_WIRELESS.deviceName)
        BridgeState.adb.value = AdbStatus.Connected(37215)
        BridgeState.usbDeviceName.value = "GameSir G8 Plus"
        BridgeState.rumble.value = RumbleState(212, 148, RumbleSource.CONTROLLER_RUMBLE)
        val buttons = (1 shl Buttons.A) or (1 shl Buttons.RB)
        BridgeState.physical.value = PadState(lx = 60, ly = 96, rx = 190, ry = 150, lt = 40, rt = 215, hat = 2, buttons = buttons)
        BridgeState.mappedButtons.value = buttons
        BridgeState.counters.value = BridgeCounters(48213, 48190, 372, "00 14 00 12 28 D7 20 BC 9A E0 1C 3E 10 16 00 00", "id 3: 03 0F 00 00 53 3A FF 00", 0.21f)
        BridgeState.log("Bridge", "Connecting to ADB")
        BridgeState.log("Bridge", "driver up to date")
        BridgeState.log("USB", "Connected to GameSir G8 Plus")
        BridgeState.log("Bridge", "Virtual Xbox Wireless Controller is live")
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("../docs/screenshots/$name.png") {
            NeuroPadTheme { Box(Modifier.fillMaxSize().background(Np.Background)) { content() } }
        }
    }

    @Test
    fun home() = shot("home") { HomeScreen(vm, onStart = {}, onNavigate = {}) }

    @Test
    @Config(qualifiers = "w412dp-h780dp-xhdpi")
    fun controller() {
        vm.selectLayout(LayoutMode.DEFAULT)
        shot("controller") { ControllerScreen(vm) }
    }

    @Test
    @Config(qualifiers = "w820dp-h370dp-land-xhdpi")
    fun homeLandscape() = shot("home_landscape") { HomeScreen(vm, onStart = {}, onNavigate = {}) }

    @Test
    @Config(qualifiers = "w820dp-h370dp-land-xhdpi")
    fun controllerLandscape() = shot("controller_landscape") { ControllerScreen(vm) }

    @Test
    @Config(qualifiers = "w820dp-h370dp-land-xhdpi")
    fun settingsLandscape() = shot("settings_landscape") { SettingsScreen(vm, onQuit = {}) }

    @Test
    @Config(qualifiers = "w412dp-h1150dp-xhdpi")
    fun connection() = shot("connection") { ConnectionScreen(vm) }

    @Test
    @Config(qualifiers = "w412dp-h1000dp-xhdpi")
    fun support() = shot("support") { SupportScreen(vm) }

    @Test
    @Config(qualifiers = "w820dp-h370dp-land-xhdpi")
    fun supportLandscape() = shot("support_landscape") { SupportScreen(vm) }

    @Test
    @Config(qualifiers = "w412dp-h1180dp-xhdpi")
    fun settings()= shot("settings") { SettingsScreen(vm, onQuit = {}) }
}
