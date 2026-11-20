package com.stepquest.core

import com.stepquest.core.steps.Clock
import com.stepquest.core.steps.StepSource
import java.time.ZoneId
import java.time.ZoneOffset

class FakeClock(var now: Long, offsetHours: Int = 0) : Clock {
    override val zone: ZoneId = ZoneOffset.ofHours(offsetHours)
    override fun nowMillis() = now
    fun advanceHours(h: Long) { now += h * 3_600_000L }
    fun advanceDays(d: Long) { now += d * 86_400_000L }
}

class FakeSteps : StepSource {
    val samples = mutableListOf<Pair<Long, Long>>()
    var available = true
    fun add(at: Long, steps: Long) { samples += at to steps }
    override fun readSteps(fromMillis: Long, toMillis: Long): Long =
        if (!available) -1 else samples.filter { it.first in fromMillis until toMillis }.sumOf { it.second }
}

/** 2026-03-02 10:00 UTC (a Monday). */
const val START = 1772445600000L
