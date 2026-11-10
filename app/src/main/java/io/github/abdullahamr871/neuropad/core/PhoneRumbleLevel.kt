package io.github.abdullahamr871.neuropad.core

/**
 * Turns the two controller motors (0..255, already boosted) into one amplitude for the
 * phone's single vibration motor, and decides when a new vibration request is worth sending.
 */
object PhoneRumbleLevel {
    /** Changes smaller than this are not re-sent; every request restarts the motor briefly. */
    const val MIN_STEP = 12

    /** Phones without amplitude control only switch on above this level. */
    const val ON_OFF_THRESHOLD = 40

    /** 0 means off. The heavy motor dominates, the light one adds a little on top. */
    fun of(left: Int, right: Int): Int {
        val l = left.coerceIn(0, 255)
        val r = right.coerceIn(0, 255)
        if (l == 0 && r == 0) return 0
        return (maxOf(l, r) + minOf(l, r) / 4).coerceIn(1, 255)
    }

    /** True when the motor should be (re)started at [next] while it currently plays [current]. */
    fun shouldUpdate(current: Int, next: Int): Boolean = when {
        next == current -> false
        next == 0 || current == 0 -> true
        else -> kotlin.math.abs(next - current) >= MIN_STEP
    }
}
