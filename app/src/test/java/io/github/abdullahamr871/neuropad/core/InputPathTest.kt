package io.github.abdullahamr871.neuropad.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** USB packet -> pad state -> virtual HID report. */
class InputPathTest {

    private fun packet(
        b2: Int = 0, b3: Int = 0, lt: Int = 0, rt: Int = 0,
        lx: Int = 0, ly: Int = 0, rx: Int = 0, ry: Int = 0, length: Int = 20,
    ): ByteArray {
        val p = ByteArray(length)
        p[0] = 0x00
        p[1] = 0x14
        p[2] = b2.toByte()
        p[3] = b3.toByte()
        p[4] = lt.toByte()
        p[5] = rt.toByte()
        fun put(offset: Int, v: Int) {
            p[offset] = (v and 0xFF).toByte()
            p[offset + 1] = ((v shr 8) and 0xFF).toByte()
        }
        put(6, lx); put(8, ly); put(10, rx); put(12, ry)
        return p
    }

    @Test
    fun `short packets are ignored`() {
        assertNull(XInputParser.parse(ByteArray(8)))
        assertNull(XInputParser.parse(ByteArray(20), 13))
    }

    @Test
    fun `idle pad is centred with nothing pressed`() {
        val s = XInputParser.parse(packet())!!
        assertEquals(PadState(lx = 128, ly = 128, rx = 128, ry = 128, hat = 8), s)
    }

    @Test
    fun `sticks are scaled to 0-255 with Y pointing down`() {
        val s = XInputParser.parse(packet(lx = -32768, ly = 32767, rx = 32767, ry = -32768))!!
        assertEquals(0, s.lx)
        assertEquals(0, s.ly) // stick pushed up -> small Y
        assertEquals(255, s.rx)
        assertEquals(255, s.ry) // -32768 negated would overflow a short; must clamp, not wrap
    }

    @Test
    fun `dpad diagonals become hat directions`() {
        fun hat(bits: Int) = XInputParser.parse(packet(b2 = bits))!!.hat
        assertEquals(0, hat(0x01))
        assertEquals(1, hat(0x01 or 0x08))
        assertEquals(2, hat(0x08))
        assertEquals(3, hat(0x02 or 0x08))
        assertEquals(4, hat(0x02))
        assertEquals(5, hat(0x02 or 0x04))
        assertEquals(6, hat(0x04))
        assertEquals(7, hat(0x01 or 0x04))
    }

    @Test
    fun `face and shoulder buttons are decoded`() {
        val s = XInputParser.parse(packet(b2 = 0x10 or 0x40, b3 = 0x01 or 0x10 or 0x80, lt = 255, rt = 200))!!
        for (b in listOf(Buttons.START, Buttons.L3, Buttons.LB, Buttons.A, Buttons.Y, Buttons.LT)) {
            assertTrue("button $b", s.isPressed(b))
        }
        assertFalse("RT needs more than 200", s.isPressed(Buttons.RT))
        assertFalse(s.isPressed(Buttons.B))
    }

    @Test
    fun `pressing A does not report a phantom M2 paddle`() {
        // Regression: 1.0 derived M2 from the A bit of byte 3.
        val s = XInputParser.parse(packet(b3 = 0x10))!!
        assertTrue(s.isPressed(Buttons.A))
        assertFalse(s.isPressed(Buttons.M2))
        assertFalse(s.isPressed(Buttons.M1))
    }

    @Test
    fun `paddles come from the extended bytes`() {
        val p = packet(length = 21)
        p[15] = 0x02
        assertTrue(XInputParser.parse(p)!!.isPressed(Buttons.M2))
        val q = packet(length = 21)
        q[20] = 0x01
        assertTrue(XInputParser.parse(q)!!.isPressed(Buttons.M1))
    }

    @Test
    fun `rumble packet uses the xbox 360 wire format`() {
        assertArrayEquals(
            byteArrayOf(0x00, 0x08, 0x00, 0xFF.toByte(), 0x40, 0x00, 0x00, 0x00),
            XInputParser.rumblePacket(300, 64),
        )
    }

    // ---- Report building ----------------------------------------------------------------

    private val pressedA = PadState(lx = 10, ly = 20, rx = 30, ry = 40, lt = 50, rt = 60, hat = 2, buttons = 1 shl Buttons.A)

    @Test
    fun `xbox report keeps the 1_0 byte layout`() {
        val r = ReportBuilder(EmulationProfile.XBOX_WIRELESS, ButtonLayout.IDENTITY).build(
            pressedA.copy(buttons = (1 shl Buttons.A) or (1 shl Buttons.Y) or (1 shl Buttons.RB) or (1 shl Buttons.START) or (1 shl Buttons.SHARE)),
        )
        assertArrayEquals(intArrayOf(1, 10, 20, 30, 40, 50, 60, 2, 0x01 or 0x10 or 0x80, 0x08 or 0x80), r)
    }

    @Test
    fun `dualshock report uses the sony button order`() {
        val r = ReportBuilder(EmulationProfile.DUALSHOCK4, ButtonLayout.IDENTITY).build(
            pressedA.copy(buttons = (1 shl Buttons.A) or (1 shl Buttons.X) or (1 shl Buttons.GUIDE)),
        )
        assertEquals(10, r.size)
        assertEquals(0x02 or 0x01, r[8]) // cross = bit 1, square = bit 0
        assertEquals(0x10, r[9]) // PS
    }

    @Test
    fun `dualsense report is 64 bytes with a running counter`() {
        val builder = ReportBuilder(EmulationProfile.DUALSENSE, ButtonLayout.IDENTITY)
        val first = builder.build(pressedA)
        val second = builder.build(pressedA)
        assertEquals(64, first.size)
        assertEquals(0, first[7])
        assertEquals(1, second[7])
        assertEquals(2 or 0x20, first[8]) // hat east + cross
    }

    @Test
    fun `nintendo layout swaps the face buttons`() {
        val b = ReportBuilder(EmulationProfile.XBOX_360, ButtonLayout.NINTENDO)
        assertEquals(1 shl Buttons.B, b.mapButtons(1 shl Buttons.A))
        assertEquals(1 shl Buttons.X, b.mapButtons(1 shl Buttons.Y))
        assertEquals(1 shl Buttons.LB, b.mapButtons(1 shl Buttons.LB))
    }

    @Test
    fun `custom layout can route a paddle to a real button`() {
        val table = IntArray(Buttons.COUNT) { it }
        table[Buttons.M1] = Buttons.A
        val b = ReportBuilder(EmulationProfile.XBOX_360, ButtonLayout.custom(table))
        assertEquals(1 shl Buttons.A, b.mapButtons(1 shl Buttons.M1))
    }

    @Test
    fun `select plus start becomes home only when enabled`() {
        val both = (1 shl Buttons.SELECT) or (1 shl Buttons.START) or (1 shl Buttons.A)
        val on = ReportBuilder(EmulationProfile.XBOX_360, ButtonLayout.IDENTITY, homeCombo = true)
        assertEquals((1 shl Buttons.GUIDE) or (1 shl Buttons.A), on.mapButtons(both))
        assertEquals(1 shl Buttons.SELECT, on.mapButtons(1 shl Buttons.SELECT))
        val off = ReportBuilder(EmulationProfile.XBOX_360, ButtonLayout.IDENTITY, homeCombo = false)
        assertEquals(both, off.mapButtons(both))
    }
}
