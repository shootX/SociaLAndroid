package com.ich.socialsave.util

import android.content.Context
import com.ich.socialsave.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object RelativeTime {
    /** "5 min ago", "3 days ago", then a short date. */
    fun compact(context: Context, millis: Long, now: Long = System.currentTimeMillis()): String {
        val seconds = (now - millis) / 1000
        if (seconds < 45) return context.getString(R.string.time_just_now)
        val minutes = seconds / 60
        if (minutes < 60) return context.getString(R.string.time_minutes_ago, maxOf(minutes, 1).toInt())
        val hours = seconds / 3600
        if (hours < 24) return context.getString(R.string.time_hours_ago, maxOf(hours, 1).toInt())
        val days = seconds / 86_400
        if (days < 7) return context.getString(R.string.time_days_ago, maxOf(days, 1).toInt())
        val weeks = days / 7
        if (weeks < 5) return context.getString(R.string.time_weeks_ago, maxOf(weeks, 1).toInt())
        return DateTimeFormatter.ofPattern("d MMM", locale(context)).format(zoned(millis))
    }

    /** "Today, 14:20", "Yesterday", "3 days ago", then a full date. */
    fun detailed(context: Context, millis: Long, now: Long = System.currentTimeMillis()): String {
        val seconds = (now - millis) / 1000
        if (seconds < 45) return context.getString(R.string.time_just_now)
        val minutes = seconds / 60
        if (minutes < 60) return context.getString(R.string.time_library_minutes, maxOf(minutes, 1).toInt())

        val date = zoned(millis)
        val today = LocalDate.now(ZoneId.systemDefault())
        val day = date.toLocalDate()
        if (day == today) {
            val hours = seconds / 3600
            if (hours < 6) return context.getString(R.string.time_library_hours, maxOf(hours, 1).toInt())
            val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale(context)).format(date)
            return context.getString(R.string.time_today_at, time)
        }
        if (day == today.minusDays(1)) return context.getString(R.string.time_yesterday)
        val days = ChronoUnit.DAYS.between(day, today)
        if (days in 1..6) return context.getString(R.string.time_days_ago, days.toInt())
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale(context)).format(date)
    }

    private fun zoned(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())

    private fun locale(context: Context): Locale = context.resources.configuration.locales[0]
}
