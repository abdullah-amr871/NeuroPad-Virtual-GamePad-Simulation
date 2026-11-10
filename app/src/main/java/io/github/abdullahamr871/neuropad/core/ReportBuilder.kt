package io.github.abdullahamr871.neuropad.core

/**
 * Builds the HID input reports of the virtual controller from the physical pad state.
 * Report layouts match the descriptors in [HidDescriptors] byte for byte.
 */
class ReportBuilder(
    private val profile: EmulationProfile,
    private val layout: ButtonLayout,
    /** Select + Start pressed together acts as the Home / Guide / PS button. */
    private val homeCombo: Boolean = true,
) {
    private var sequence = 0

    /** Virtual button mask after layout mapping and the Home combo. */
    fun mapButtons(physical: Int): Int {
        var mapped = layout.map(physical)
        if (homeCombo) {
            val combo = (1 shl Buttons.SELECT) or (1 shl Buttons.START)
            if (mapped and combo == combo) {
                mapped = (mapped and combo.inv()) or (1 shl Buttons.GUIDE)
            }
        }
        return mapped
    }

    fun build(state: PadState): IntArray = build(state, mapButtons(state.buttons))

    fun build(state: PadState, mapped: Int): IntArray {
        fun bit(button: Int, shift: Int) = if (mapped and (1 shl button) != 0) 1 shl shift else 0

        if (profile == EmulationProfile.DUALSENSE) {
            val r = IntArray(64)
            r[0] = 0x01
            r[1] = state.lx
            r[2] = state.ly
            r[3] = state.rx
            r[4] = state.ry
            r[5] = state.lt
            r[6] = state.rt
            r[7] = sequence
            sequence = (sequence + 1) and 0xFF
            r[8] = (state.hat and 0x0F) or
                bit(Buttons.X, 4) or bit(Buttons.A, 5) or bit(Buttons.B, 6) or bit(Buttons.Y, 7)
            r[9] = bit(Buttons.LB, 0) or bit(Buttons.RB, 1) or bit(Buttons.LT, 2) or bit(Buttons.RT, 3) or
                bit(Buttons.SELECT, 4) or bit(Buttons.START, 5) or bit(Buttons.L3, 6) or bit(Buttons.R3, 7)
            r[10] = bit(Buttons.GUIDE, 0) or bit(Buttons.SHARE, 1)
            return r
        }

        val r = IntArray(10)
        r[0] = 0x01
        r[1] = state.lx
        r[2] = state.ly
        r[3] = state.rx
        r[4] = state.ry
        r[5] = state.lt
        r[6] = state.rt
        r[7] = state.hat and 0x0F
        if (profile == EmulationProfile.DUALSHOCK4) {
            r[8] = bit(Buttons.X, 0) or bit(Buttons.A, 1) or bit(Buttons.B, 2) or bit(Buttons.Y, 3) or
                bit(Buttons.LB, 4) or bit(Buttons.RB, 5) or bit(Buttons.LT, 6) or bit(Buttons.RT, 7)
            r[9] = bit(Buttons.SELECT, 0) or bit(Buttons.START, 1) or bit(Buttons.L3, 2) or
                bit(Buttons.R3, 3) or bit(Buttons.GUIDE, 4) or bit(Buttons.SHARE, 5)
        } else {
            // HID buttons 1..16 = A, B, C, X, Y, Z, TL, TR, TL2, TR2, Select, Start, Mode, ThumbL, ThumbR.
            r[8] = bit(Buttons.A, 0) or bit(Buttons.B, 1) or bit(Buttons.X, 3) or bit(Buttons.Y, 4) or
                bit(Buttons.LB, 6) or bit(Buttons.RB, 7)
            r[9] = bit(Buttons.LT, 0) or bit(Buttons.RT, 1) or bit(Buttons.SELECT, 2) or
                bit(Buttons.START, 3) or bit(Buttons.GUIDE, 4) or bit(Buttons.L3, 5) or
                bit(Buttons.R3, 6) or bit(Buttons.SHARE, 7)
        }
        return r
    }
}
