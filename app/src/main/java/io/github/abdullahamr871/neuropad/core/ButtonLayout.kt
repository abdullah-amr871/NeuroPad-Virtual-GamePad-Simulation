package io.github.abdullahamr871.neuropad.core

/** How physical buttons are routed to the virtual controller's buttons. */
enum class LayoutMode(val id: Int, val label: String) {
    DEFAULT(0, "Default"),
    NINTENDO(1, "Nintendo (swap A/B, X/Y)"),
    CUSTOM(2, "Custom");

    companion object {
        fun fromId(id: Int): LayoutMode = entries[id.coerceIn(0, 2)]
    }
}

/**
 * Immutable button routing table: `table[physical] = virtual`.
 * Several physical buttons may point at the same virtual button.
 */
class ButtonLayout private constructor(private val table: IntArray) {

    fun map(physicalButtons: Int): Int {
        var out = 0
        for (i in 0 until Buttons.COUNT) {
            if (physicalButtons and (1 shl i) != 0) {
                val target = table[i]
                if (target in 0 until Buttons.COUNT) out = out or (1 shl target)
            }
        }
        return out
    }

    fun target(physical: Int): Int = table[physical]

    companion object {
        val IDENTITY = ButtonLayout(IntArray(Buttons.COUNT) { it })

        val NINTENDO = ButtonLayout(
            IntArray(Buttons.COUNT) {
                when (it) {
                    Buttons.A -> Buttons.B
                    Buttons.B -> Buttons.A
                    Buttons.X -> Buttons.Y
                    Buttons.Y -> Buttons.X
                    else -> it
                }
            },
        )

        fun custom(table: IntArray): ButtonLayout {
            require(table.size == Buttons.COUNT)
            return ButtonLayout(table.copyOf())
        }

        fun of(mode: LayoutMode, customTable: IntArray): ButtonLayout = when (mode) {
            LayoutMode.DEFAULT -> IDENTITY
            LayoutMode.NINTENDO -> NINTENDO
            LayoutMode.CUSTOM -> custom(customTable)
        }
    }
}
