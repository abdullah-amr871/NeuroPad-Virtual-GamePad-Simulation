package io.github.abdullahamr871.neuropad.bridge

import org.junit.Assert.assertEquals
import org.junit.Test

class DaemonInstallerTest {
    @Test
    fun phonesWith64BitAndroidGetThe64BitDriver() =
        assertEquals("uinput_proto", DaemonInstaller.assetName(arrayOf("arm64-v8a", "armeabi-v7a", "armeabi")))

    @Test
    fun tvsRunning32BitAndroidGetThe32BitDriver() =
        assertEquals("uinput_proto_armv7", DaemonInstaller.assetName(arrayOf("armeabi-v7a", "armeabi")))
}
