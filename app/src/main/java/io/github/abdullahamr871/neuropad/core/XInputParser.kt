package io.github.abdullahamr871.neuropad.core

/**
 * Snapshot of the physical controller, decoded from one XInput (Xbox 360 protocol) packet.
 *
 * Sticks and triggers are 0..255 (128 = centred stick). [hat] is 0..7 clockwise from
 * north, 8 = released. [buttons] is a bit mask indexed by [Buttons].
 */
data class PadState(
    val lx: Int = 128,
    val ly: Int = 128,
    val rx: Int = 128,
    val ry: Int = 128,
    val lt: Int = 0,
    val rt: Int = 0,
    val hat: Int = HAT_CENTERED,
    val buttons: Int = 0,
) {
    fun isPressed(button: Int): Boolean = buttons and (1 shl button) != 0

    companion object {
        const val HAT_CENTERED = 8
    }
}

/**
 * Decoder for the 20-byte Xbox 360 wired input report that XInput gamepads
 * (including the GameSir G8 Plus) send on their interrupt endpoint.
 */
object XInputParser {
    const val MIN_PACKET_LENGTH = 14

    /** Analog trigger travel above which the digital LT/RT buttons count as pressed. */
    private const val TRIGGER_DIGITAL_THRESHOLD = 200

    fun parse(packet: ByteArray, length: Int = packet.size): PadState? {
        if (length < MIN_PACKET_LENGTH || packet.size < length) return null
        val b2 = packet[2].toInt() and 0xFF
        val b3 = packet[3].toInt() and 0xFF
        val lt = packet[4].toInt() and 0xFF
        val rt = packet[5].toInt() and 0xFF

        val up = b2 and 0x01 != 0
        val down = b2 and 0x02 != 0
        val left = b2 and 0x04 != 0
        val right = b2 and 0x08 != 0
        val hat = when {
            up && right -> 1
            down && right -> 3
            down && left -> 5
            up && left -> 7
            up -> 0
            right -> 2
            down -> 4
            left -> 6
            else -> PadState.HAT_CENTERED
        }

        var buttons = 0
        fun set(index: Int, pressed: Boolean) {
            if (pressed) buttons = buttons or (1 shl index)
        }
        set(Buttons.START, b2 and 0x10 != 0)
        set(Buttons.SELECT, b2 and 0x20 != 0)
        set(Buttons.L3, b2 and 0x40 != 0)
        set(Buttons.R3, b2 and 0x80 != 0)
        set(Buttons.LB, b3 and 0x01 != 0)
        set(Buttons.RB, b3 and 0x02 != 0)
        set(Buttons.GUIDE, b3 and 0x04 != 0)
        set(Buttons.A, b3 and 0x10 != 0)
        set(Buttons.B, b3 and 0x20 != 0)
        set(Buttons.X, b3 and 0x40 != 0)
        set(Buttons.Y, b3 and 0x80 != 0)
        set(Buttons.LT, lt > TRIGGER_DIGITAL_THRESHOLD)
        set(Buttons.RT, rt > TRIGGER_DIGITAL_THRESHOLD)

        // Vendor extensions. Bit 3 of byte 3 is unused by the Xbox 360 protocol; some pads
        // use it (or byte 14 bit 0) for the capture button.
        val extra = b3 and 0x08 != 0
        set(Buttons.SHARE, extra || (length > 14 && packet[14].toInt() and 0x01 != 0))

        // Back paddles: byte 15, then the spare bit, then byte 20.
        var m1 = length > 15 && packet[15].toInt() and 0x01 != 0
        var m2 = length > 15 && packet[15].toInt() and 0x02 != 0
        if (!m1 && !m2 && extra) m1 = true
        // NeuroPad 1.0 also read "M2" from bit 0x10 of byte 3 here, which is the A button,
        // so every press of A registered as a phantom M2. That fallback is gone.
        if (!m1 && !m2 && length > 20) {
            m1 = packet[20].toInt() and 0x01 != 0
            m2 = packet[20].toInt() and 0x02 != 0
        }
        set(Buttons.M1, m1)
        set(Buttons.M2, m2)

        return PadState(
            lx = scaleStick(readShortLe(packet, 6)),
            ly = scaleStick(-readShortLe(packet, 8)),
            rx = scaleStick(readShortLe(packet, 10)),
            ry = scaleStick(-readShortLe(packet, 12)),
            lt = lt,
            rt = rt,
            hat = hat,
            buttons = buttons,
        )
    }

    private fun readShortLe(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) or (data[offset + 1].toInt() shl 8)).toShort().toInt()

    /** Signed 16-bit stick value (Y already flipped by the caller) to 0..255. */
    internal fun scaleStick(value: Int): Int = ((32768 + value) / 256).coerceIn(0, 255)

    /** The rumble command of the Xbox 360 wired protocol. */
    fun rumblePacket(left: Int, right: Int): ByteArray = byteArrayOf(
        0x00, 0x08, 0x00,
        left.coerceIn(0, 255).toByte(),
        right.coerceIn(0, 255).toByte(),
        0x00, 0x00, 0x00,
    )
}
