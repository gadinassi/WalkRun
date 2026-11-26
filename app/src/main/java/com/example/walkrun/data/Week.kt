package com.example.walkrun.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** The week runs Sunday to Saturday, as in Israel. Used by the weekly goal and the weekly report. */
object Week {
    fun zone(): ZoneId = ZoneId.systemDefault()

    fun startOf(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    fun ms(day: LocalDate): Long = day.atStartOfDay(zone()).toInstant().toEpochMilli()

    /** Start (inclusive) and end (exclusive) of the current week, in epoch ms. */
    fun currentRange(): Pair<Long, Long> {
        val start = startOf(LocalDate.now(zone()))
        return ms(start) to ms(start.plusDays(7))
    }

    /** Start (inclusive) and end (exclusive) of the current calendar month, in epoch ms. */
    fun monthRange(): Pair<Long, Long> {
        val start = LocalDate.now(zone()).withDayOfMonth(1)
        return ms(start) to ms(start.plusMonths(1))
    }

    /** Days left in the current month, today included. */
    fun monthDaysLeft(): Int = LocalDate.now(zone()).let { it.lengthOfMonth() - it.dayOfMonth + 1 }

    /** Days left in the current week, today included. */
    fun daysLeft(): Int = 7 - (LocalDate.now(zone()).dayOfWeek.value % 7)
}
