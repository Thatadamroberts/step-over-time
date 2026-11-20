package com.stepquest.core.steps

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Converts real-world steps into banked combat actions.
 * Every [STEPS_PER_ACTION] steps grants one action; the remainder carries over.
 */
@Serializable
class StepLedger {
    var totalSteps: Long = 0
    var stepRemainder: Int = 0
    var bankedActions: Int = 0
    var totalActionsEarned: Long = 0
    var lastSyncMillis: Long = 0
    /** Last raw value from the cumulative-since-boot hardware counter (-1 = none). */
    var lastRawSensorValue: Long = -1
    /** Steps per local day, key = yyyy-MM-dd. */
    var dailySteps: MutableMap<String, Int> = mutableMapOf()

    /**
     * Adds steps walked in [fromMillis, toMillis]. Returns actions earned.
     * Ignores ranges ending before the last sync (idempotent) and caps implausible bursts.
     */
    fun addSteps(steps: Long, fromMillis: Long, toMillis: Long, localDay: LocalDate): Int {
        if (steps <= 0 || toMillis <= lastSyncMillis) return 0
        val minutes = max(1.0, (toMillis - fromMillis) / 60_000.0)
        val capped = min(steps, (minutes * MAX_STEPS_PER_MINUTE).toLong())
        lastSyncMillis = toMillis
        return credit(capped, localDay)
    }

    /** A reading from TYPE_STEP_COUNTER; a drop in value means the device rebooted. */
    fun addRawSensorReading(raw: Long, nowMillis: Long, localDay: LocalDate): Int {
        if (raw < 0) return 0
        val delta = when {
            lastRawSensorValue < 0 -> 0L
            raw < lastRawSensorValue -> raw
            else -> raw - lastRawSensorValue
        }
        lastRawSensorValue = raw
        if (delta == 0L) return 0
        lastSyncMillis = max(lastSyncMillis, nowMillis)
        return credit(delta, localDay)
    }

    val stepsToNextAction: Int get() = STEPS_PER_ACTION - stepRemainder

    fun stepsOn(day: LocalDate): Int = dailySteps[dayKey(day)] ?: 0

    fun trySpendAction(): Boolean {
        if (bankedActions <= 0) return false
        bankedActions--
        return true
    }

    private fun credit(steps: Long, day: LocalDate): Int {
        totalSteps += steps
        val key = dayKey(day)
        dailySteps[key] = min(Int.MAX_VALUE.toLong(), (dailySteps[key] ?: 0) + steps).toInt()
        val pool = stepRemainder + steps
        val earned = (pool / STEPS_PER_ACTION).toInt()
        stepRemainder = (pool % STEPS_PER_ACTION).toInt()
        bankedActions += earned
        totalActionsEarned += earned
        return earned
    }

    companion object {
        const val STEPS_PER_ACTION = 1000
        /** Humans don't sustain more than ~250 steps/minute. */
        const val MAX_STEPS_PER_MINUTE = 250
        fun dayKey(day: LocalDate): String = day.toString()
    }
}

object DistanceCalc {
    const val DEFAULT_STRIDE_METERS = 0.76
    const val STRIDE_TO_HEIGHT = 0.415
    const val STAIR_RISE_METERS = 0.17

    fun strideFromHeightCm(cm: Int): Double =
        if (cm < 50 || cm > 260) DEFAULT_STRIDE_METERS else round(cm / 100.0 * STRIDE_TO_HEIGHT * 1000) / 1000

    fun kilometers(steps: Long, stride: Double) = steps * stride / 1000.0
    fun climbMeters(steps: Long) = steps * STAIR_RISE_METERS
}

interface Clock {
    fun nowMillis(): Long
    val zone: ZoneId
    fun localDate(millis: Long = nowMillis()): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun startOfDayMillis(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val today: LocalDate get() = localDate()
}

object SystemClock : Clock {
    override fun nowMillis() = System.currentTimeMillis()
    override val zone: ZoneId get() = ZoneId.systemDefault()
}

/** A source of steps (hardware history, debug). Returns -1 if unavailable. */
fun interface StepSource {
    fun readSteps(fromMillis: Long, toMillis: Long): Long
}
