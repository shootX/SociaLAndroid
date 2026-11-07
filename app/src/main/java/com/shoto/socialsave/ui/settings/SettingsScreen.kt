package com.shoto.socialsave.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shoto.socialsave.BuildConfig
import com.shoto.socialsave.R
import com.shoto.socialsave.container
import com.shoto.socialsave.data.Appearance
import com.shoto.socialsave.ui.components.ScreenTitle
import com.shoto.socialsave.ui.components.SectionLabel
import com.shoto.socialsave.ui.components.openLink
import com.shoto.socialsave.ui.theme.Brand
import com.shoto.socialsave.work.ReminderScheduler
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JTextStyle
import java.time.temporal.WeekFields

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenGuide: () -> Unit, onShowOnboarding: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val settings = container.settings
    val c = Brand.colors
    val locale = context.resources.configuration.locales[0]

    val remindersEnabled by settings.remindersEnabled.collectAsStateWithLifecycle()
    val reminderDay by settings.reminderDay.collectAsStateWithLifecycle()
    val reminderMinutes by settings.reminderMinutes.collectAsStateWithLifecycle()
    val appearance by settings.appearance.collectAsStateWithLifecycle()
    val privacyRequired by container.ads.privacyOptionsRequired.collectAsStateWithLifecycle()

    var canNotify by remember { mutableStateOf(ReminderScheduler.canNotify(context)) }
    var dayMenu by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canNotify = ReminderScheduler.canNotify(context) }

    // Keep reminders on even when denied, so the "blocked" footer and the open-settings row explain how to fix it.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = ReminderScheduler.canNotify(context)
        settings.setRemindersEnabled(true)
        container.rescheduleReminder()
    }
    val blocked = remindersEnabled && !canNotify

    fun setReminders(on: Boolean) {
        if (on && !ReminderScheduler.canNotify(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            settings.setRemindersEnabled(on)
            container.rescheduleReminder()
        }
    }

    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding()) {
        ScreenTitle(stringResource(R.string.tab_settings), Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Group(stringResource(R.string.settings_reminders), footer = stringResource(if (blocked) R.string.settings_reminders_blocked else R.string.settings_reminders_footer)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Notifications, null, tint = c.ink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.settings_reminders_toggle), color = c.ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = remindersEnabled && canNotify,
                        onCheckedChange = ::setReminders,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = c.canvas,
                            checkedTrackColor = c.ink,
                            uncheckedThumbColor = c.gray,
                            uncheckedTrackColor = c.line,
                            uncheckedBorderColor = c.line,
                        ),
                    )
                }
                if (remindersEnabled && canNotify) {
                    Divider()
                    Box {
                        ValueRow(stringResource(R.string.settings_reminders_day), reminderDay.getDisplayName(JTextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }) { dayMenu = true }
                        DropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                            val first = WeekFields.of(locale).firstDayOfWeek
                            (0L until 7L).map { first.plus(it) }.forEach { day ->
                                DropdownMenuItem(
                                    text = { Text(day.getDisplayName(JTextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }) },
                                    onClick = {
                                        dayMenu = false
                                        settings.setReminderDay(day)
                                        container.rescheduleReminder()
                                    },
                                )
                            }
                        }
                    }
                    Divider()
                    val time = LocalTime.of(reminderMinutes / 60, reminderMinutes % 60)
                    ValueRow(
                        stringResource(R.string.settings_reminders_time),
                        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(time),
                    ) { timeDialog = true }
                }
                if (blocked) {
                    Divider()
                    LinkRow(stringResource(R.string.settings_reminders_open_settings)) {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                }
            }

            Group(stringResource(R.string.settings_appearance)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(12.dp)) {
                    val options = listOf(
                        Appearance.SYSTEM to R.string.appearance_system,
                        Appearance.LIGHT to R.string.appearance_light,
                        Appearance.DARK to R.string.appearance_dark,
                    )
                    options.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = appearance == value,
                            onClick = { settings.setAppearance(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = c.ink,
                                activeContentColor = c.canvas,
                                inactiveContainerColor = c.surface,
                                inactiveContentColor = c.ink,
                                activeBorderColor = c.line,
                                inactiveBorderColor = c.line,
                            ),
                            icon = {},
                        ) { Text(stringResource(label), maxLines = 1) }
                    }
                }
            }

            Group(null) {
                NavRow(Icons.Filled.TouchApp, stringResource(R.string.settings_one_tap_save), onOpenGuide)
                Divider()
                NavRow(Icons.AutoMirrored.Filled.HelpOutline, stringResource(R.string.settings_how_to_save), onShowOnboarding)
            }

            Group(stringResource(R.string.settings_about)) {
                ValueRow(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME, null)
                Divider()
                ValueRow(stringResource(R.string.settings_build), BuildConfig.VERSION_CODE.toString(), null)
                Divider()
                LinkRow(stringResource(R.string.settings_privacy)) { openLink(context, legalUrl("privacy", locale.language)) }
                Divider()
                LinkRow(stringResource(R.string.settings_terms)) { openLink(context, legalUrl("terms", locale.language)) }
                if (privacyRequired) {
                    Divider()
                    LinkRow(stringResource(R.string.settings_privacy_choices)) {
                        (context as? Activity)?.let(container.ads::showPrivacyOptions)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (timeDialog) {
        val state = rememberTimePickerState(reminderMinutes / 60, reminderMinutes % 60, is24Hour = android.text.format.DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { timeDialog = false },
            containerColor = c.surface,
            confirmButton = {
                TextButton(onClick = {
                    timeDialog = false
                    settings.setReminderMinutes(state.hour * 60 + state.minute)
                    container.rescheduleReminder()
                }) { Text(stringResource(R.string.action_done), color = c.ink) }
            },
            dismissButton = {
                TextButton(onClick = { timeDialog = false }) { Text(stringResource(R.string.action_cancel), color = c.ink) }
            },
            text = {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = c.canvas,
                        selectorColor = c.ink,
                        timeSelectorSelectedContainerColor = c.ink,
                        timeSelectorSelectedContentColor = c.canvas,
                        timeSelectorUnselectedContainerColor = c.canvas,
                        timeSelectorUnselectedContentColor = c.ink,
                        periodSelectorSelectedContainerColor = c.ink,
                        periodSelectorSelectedContentColor = c.canvas,
                    ),
                )
            },
        )
    }
}

private fun legalUrl(path: String, language: String): String {
    val lang = when (language) {
        "ka", "ru" -> language
        else -> "en"
    }
    return "${BuildConfig.WEBSITE_URL}/$path?lang=$lang"
}

@Composable
private fun Group(title: String?, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Brand.colors
    Column(Modifier.padding(top = 20.dp)) {
        if (title != null) SectionLabel(title, Modifier.padding(start = 16.dp, bottom = 8.dp))
        Column(
            Modifier.fillMaxWidth()
                .background(c.surface, RoundedCornerShape(Brand.controlRadius))
                .border(1.dp, c.line, RoundedCornerShape(Brand.controlRadius)),
            content = content,
        )
        if (footer != null) {
            Text(footer, color = c.gray, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
        }
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = Brand.colors.line, modifier = Modifier.padding(start = 16.dp))

@Composable
private fun ValueRow(title: String, value: String, onClick: (() -> Unit)?) {
    val c = Brand.colors
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = c.ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = c.gray, fontSize = 16.sp)
    }
}

@Composable
private fun LinkRow(title: String, onClick: () -> Unit) {
    Text(
        title,
        color = Brand.colors.ink,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
private fun NavRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    val c = Brand.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = c.ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.gray)
    }
}
