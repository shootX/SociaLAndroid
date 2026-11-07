package com.ich.socialsave.work

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ReminderScheduleTest {
    // 2026-11-04 is a Wednesday.
    private val wednesdayMorning = LocalDateTime.of(2026, 11, 4, 9, 0)

    @Test fun laterThisWeek() {
        assertEquals(
            LocalDateTime.of(2026, 11, 8, 11, 0),
            ReminderScheduler.nextFireTime(wednesdayMorning, DayOfWeek.SUNDAY, 11 * 60),
        )
    }

    @Test fun laterToday() {
        assertEquals(
            LocalDateTime.of(2026, 11, 4, 11, 30),
            ReminderScheduler.nextFireTime(wednesdayMorning, DayOfWeek.WEDNESDAY, 11 * 60 + 30),
        )
    }

    @Test fun alreadyPassedTodayMovesToNextWeek() {
        assertEquals(
            LocalDateTime.of(2026, 11, 11, 8, 0),
            ReminderScheduler.nextFireTime(wednesdayMorning, DayOfWeek.WEDNESDAY, 8 * 60),
        )
    }
}
