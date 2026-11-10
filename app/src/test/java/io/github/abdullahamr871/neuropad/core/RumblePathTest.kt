package io.github.abdullahamr871.neuropad.core

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Output reports, the phone-vibration fallback and the daemon protocol. */
class RumblePathTest {

    private fun report(vararg bytes: Int) = bytes + IntArray(maxOf(0, 16 - bytes.size))

    @Test
    fun `dualsense output report carries right then left motor`() {
        assertEquals(Rumble(left = 200, right = 100), RumbleParser.parse(EmulationProfile.DUALSENSE, report(0x02, 0xFF, 0xF7, 100, 200)))
    }

    @Test
    fun `dualshock 4 bluetooth and usb output reports`() {
        assertEquals(Rumble(left = 7, right = 6), RumbleParser.parse(EmulationProfile.DUALSHOCK4, report(0x11, 0, 0, 0, 0, 0, 6, 7)))
        assertEquals(Rumble(left = 5, right = 4), RumbleParser.parse(EmulationProfile.DUALSHOCK4, report(0x05, 0, 0, 0, 4, 5)))
    }

    @Test
    fun `xbox bluetooth magnitudes are percentages and include triggers`() {
        val r = RumbleParser.parse(EmulationProfile.XBOX_SERIES, report(0x03, 0x0F, 10, 20, 100, 50))!!
        assertEquals(255, r.left)
        assertEquals(127, r.right)
        assertEquals(10, r.leftTrigger)
        assertEquals(20, r.rightTrigger)
    }

    @Test
    fun `xbox 360 accepts both the wire format and the plain two byte report`() {
        assertEquals(Rumble(9, 8), RumbleParser.parse(EmulationProfile.XBOX_360, report(0x00, 0x08, 0x00, 9, 8)))
        assertEquals(Rumble(9, 8), RumbleParser.parse(EmulationProfile.XBOX_360, report(0x01, 9, 8)))
    }

    @Test
    fun `boost applies gain adds triggers and saturates`() {
        assertEquals(0, RumbleParser.boost(0, 0, 4f))
        assertEquals(40, RumbleParser.boost(10, 0, 4f))
        assertEquals(80, RumbleParser.boost(10, 10, 4f))
        assertEquals(255, RumbleParser.boost(200, 0, 4f))
        assertEquals(100, RumbleParser.boost(100, 0, 1f))
    }

    // ---- Phone-vibration fallback -------------------------------------------------------

    @Test
    fun `splitter routes quiet effects to the light motor only`() {
        val s = HapticSplitter(gain = 1f, smoothing = false)
        assertEquals(0 to 0, s.split(0))
        assertEquals(0 to 80, s.split(40))
        val (left, right) = s.split(120)
        assertEquals(120, right)
        assertEquals((120 - 80) * 255 / 80, left)
        assertEquals(200 to 200, s.split(200))
    }

    @Test
    fun `splitter survives inverted thresholds`() {
        val s = HapticSplitter(gain = 1f, lowThreshold = 160, highThreshold = 80, smoothing = false)
        assertEquals(255 to 255, s.split(255)) // 1.0 divided by zero or a negative span here
    }

    @Test
    fun `smoothing blends with the previous value`() {
        val s = HapticSplitter(gain = 1f, smoothing = true)
        val (first, _) = s.split(200)
        assertEquals(140, first) // 70 % of 200
        val (second, _) = s.split(200)
        assertTrue(second in 181..183) // 0.7 * 200 + 0.3 * 140
    }

    @Test
    fun `one shot lines are parsed and default amplitude is full strength`() {
        val events = VibrationLogParser.parse("VibratorService: vibrate OneShot{mDuration=120, mAmplitude=-1}")
        assertEquals(listOf(VibrationEvent.OneShot(120, 255)), events)
    }

    @Test
    fun `waveform arrays of different length do not crash`() {
        val e = VibrationLogParser.parse("Waveform{mTimings=[0, 30, 40], mAmplitudes=[0, 255], mRepeat=-1}").single()
        e as VibrationEvent.Waveform
        assertArrayEquals(longArrayOf(0, 30), e.timings)
        assertArrayEquals(intArrayOf(0, 255), e.amplitudes)
        assertEquals(-1, e.repeat)
    }

    @Test
    fun `caller package is extracted and system callers are recognised`() {
        val events = VibrationLogParser.parse("NtVibratorManagerImpl: vibrate pkgName = com.android.systemui, effect = OneShot{mDuration=10, mAmplitude=80}")
        assertEquals(VibrationEvent.Caller("com.android.systemui"), events[0])
        assertEquals(VibrationEvent.OneShot(10, 80), events[1])
        assertTrue(VibrationLogParser.isSystemCaller("com.android.systemui", "x"))
        assertTrue(VibrationLogParser.isSystemCaller("com.google.android.inputmethod.latin", "x"))
        assertFalse(VibrationLogParser.isSystemCaller("com.activision.callofduty.shooter", "x"))
    }

    @Test
    fun `richtap and stop lines`() {
        assertEquals(listOf(VibrationEvent.RichTapDuration(35)), VibrationLogParser.parse("richTapVibratorOn duration : 35 ms"))
        assertEquals(listOf(VibrationEvent.RichTapStrength(180)), VibrationLogParser.parse("richTapVibratorSetAmplitude strength : 180"))
        assertEquals(listOf(VibrationEvent.Stop), VibrationLogParser.parse("VibratorHal: vibratorStop"))
        assertTrue(VibrationLogParser.parse("unrelated line").isEmpty())
    }

    // ---- Daemon protocol ----------------------------------------------------------------

    @Test
    fun `register command is valid json with the full descriptor`() {
        for (profile in EmulationProfile.entries) {
            val line = HidProtocol.register(profile, "aa:bb:cc:dd:ee:ff")
            assertTrue(line.endsWith("\n"))
            val json = JSONObject(line)
            assertEquals("register", json.getString("command"))
            assertEquals(profile.vendorId, json.getInt("vid"))
            assertEquals(profile.productId, json.getInt("pid"))
            assertEquals(profile.descriptor.size, json.getJSONArray("descriptor").length())
            assertEquals(profile.family == EmulationProfile.Family.PLAYSTATION, json.has("feature_reports"))
        }
    }

    @Test
    fun `descriptors are well formed`() {
        for (profile in EmulationProfile.entries) {
            val d = profile.descriptor
            assertArrayEquals("starts with Generic Desktop / Gamepad", intArrayOf(0x05, 0x01, 0x09, 0x05, 0xA1, 0x01), d.copyOf(6))
            assertEquals("ends the collection", 0xC0, d.last())
            assertTrue("has an output report for rumble", d.contains(0x91))
        }
        assertEquals(64, EmulationProfile.DUALSENSE.featureReports.getValue(0x20).size)
    }

    @Test
    fun `get report reply reports errors under both field names`() {
        val ok = JSONObject(HidProtocol.getReportReply(7, intArrayOf(5, 1, 2)))
        assertEquals(0, ok.getInt("err"))
        assertEquals(3, ok.getJSONArray("report").length())
        val failed = JSONObject(HidProtocol.getReportReply(8, null))
        assertEquals(1, failed.getInt("err"))
        assertEquals(1, failed.getInt("error"))
        assertFalse(failed.has("report"))
    }

    @Test
    fun `object splitter reassembles events across chunk borders`() {
        val seen = ArrayList<String>()
        val splitter = HidProtocol.ObjectSplitter(seen::add)
        val stream = "garbage\r\n{\"event\":\"ready\",\"version\":\"2.0.0\"}\r\n{\"event\":\"output\",\"note\":\"}{\",\"reportData\":[1,2]}\n"
        for (chunk in stream.chunked(7)) splitter.feed(chunk.toCharArray(), chunk.length)
        assertEquals(2, seen.size)
        assertEquals("ready", JSONObject(seen[0]).getString("event"))
        assertEquals(2, JSONObject(seen[1]).getJSONArray("reportData").length())
    }
}
