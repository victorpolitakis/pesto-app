package com.victorpolitakis.pesto.util

/** Wall-clock time in epoch milliseconds, injectable for tests. */
fun interface WallClock {
    fun now(): Long
}
