package com.shoto.socialsave.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek

enum class Appearance { SYSTEM, LIGHT, DARK }

/** Small preferences store; every value is also a [StateFlow] so Compose screens update immediately. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _appearance = MutableStateFlow(
        Appearance.entries.firstOrNull { it.name == prefs.getString(KEY_APPEARANCE, null) } ?: Appearance.SYSTEM,
    )
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    private val _newestFirst = MutableStateFlow(prefs.getBoolean(KEY_NEWEST_FIRST, true))
    val newestFirst: StateFlow<Boolean> = _newestFirst.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING, false))
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private val _remindersEnabled = MutableStateFlow(prefs.getBoolean(KEY_REMINDERS, false))
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled.asStateFlow()

    private val _reminderDay = MutableStateFlow(
        DayOfWeek.of(prefs.getInt(KEY_REMINDER_DAY, DayOfWeek.SUNDAY.value).coerceIn(1, 7)),
    )
    val reminderDay: StateFlow<DayOfWeek> = _reminderDay.asStateFlow()

    private val _reminderMinutes = MutableStateFlow(prefs.getInt(KEY_REMINDER_MINUTES, 11 * 60))
    val reminderMinutes: StateFlow<Int> = _reminderMinutes.asStateFlow()

    fun setAppearance(value: Appearance) {
        prefs.edit { putString(KEY_APPEARANCE, value.name) }
        _appearance.value = value
    }

    fun setNewestFirst(value: Boolean) {
        prefs.edit { putBoolean(KEY_NEWEST_FIRST, value) }
        _newestFirst.value = value
    }

    fun setOnboardingCompleted(value: Boolean) {
        prefs.edit { putBoolean(KEY_ONBOARDING, value) }
        _onboardingCompleted.value = value
    }

    fun setRemindersEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_REMINDERS, value) }
        _remindersEnabled.value = value
    }

    fun setReminderDay(value: DayOfWeek) {
        prefs.edit { putInt(KEY_REMINDER_DAY, value.value) }
        _reminderDay.value = value
    }

    fun setReminderMinutes(value: Int) {
        prefs.edit { putInt(KEY_REMINDER_MINUTES, value.coerceIn(0, 24 * 60 - 1)) }
        _reminderMinutes.value = value
    }

    private companion object {
        const val KEY_APPEARANCE = "appearance"
        const val KEY_NEWEST_FIRST = "library.newestFirst"
        const val KEY_ONBOARDING = "onboarding.completed"
        const val KEY_REMINDERS = "reminders.enabled"
        const val KEY_REMINDER_DAY = "reminders.day"
        const val KEY_REMINDER_MINUTES = "reminders.minutes"
    }
}
