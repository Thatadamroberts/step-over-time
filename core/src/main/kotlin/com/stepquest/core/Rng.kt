package com.stepquest.core

import kotlinx.serialization.Serializable

/** Deterministic, serializable xorshift RNG so battles can be saved mid-fight and tested. */
@Serializable
class Rng(var state: Long = System.nanoTime()) {
    init { if (state == 0L) state = -0x61c8864680b583ebL }

    fun nextLong(): Long {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        state = x
        return x
    }

    /** [0, 1) */
    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))

    /** [min, maxExclusive) */
    fun range(min: Int, maxExclusive: Int): Int {
        if (maxExclusive <= min) return min
        return min + java.lang.Long.remainderUnsigned(nextLong(), (maxExclusive - min).toLong()).toInt()
    }

    fun chance(p: Double): Boolean = nextDouble() < p
}
