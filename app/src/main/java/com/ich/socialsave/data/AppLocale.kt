package com.ich.socialsave.data

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * In-app language choice. The value is read synchronously from the same preferences file as [AppSettings],
 * so an Activity or Application can apply it in attachBaseContext before any UI is created.
 */
object AppLocale {
    const val SYSTEM = "system"
    const val GEORGIAN = "ka"
    const val ENGLISH = "en"
    const val RUSSIAN = "ru"
    val CHOICES = listOf(SYSTEM, GEORGIAN, ENGLISH, RUSSIAN)

    private const val PREFS = "settings"
    const val KEY_LANGUAGE = "language"

    fun saved(context: Context): String {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, SYSTEM)
        return if (value in CHOICES) value!! else SYSTEM
    }

    /** Returns a context whose resources use the chosen language, or [base] unchanged when the system language is used. */
    fun wrap(base: Context): Context {
        val tag = saved(base)
        if (tag == SYSTEM) return base
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
