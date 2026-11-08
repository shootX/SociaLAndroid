package com.ich.socialsave.ui.settings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ich.socialsave.BuildConfig
import com.ich.socialsave.R
import com.ich.socialsave.account.AccountState
import com.ich.socialsave.account.AccountUser
import com.ich.socialsave.account.SignOutResult
import com.ich.socialsave.account.SyncStatus
import com.ich.socialsave.container
import com.ich.socialsave.data.AppLocale
import com.ich.socialsave.data.Appearance
import com.ich.socialsave.ui.account.SignInSheet
import com.ich.socialsave.ui.components.ScreenTitle
import com.ich.socialsave.ui.components.SectionLabel
import com.ich.socialsave.ui.components.openLink
import com.ich.socialsave.ui.theme.Brand
import com.ich.socialsave.util.RelativeTime
import com.ich.socialsave.work.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val language by settings.language.collectAsStateWithLifecycle()
    val privacyRequired by container.ads.privacyOptionsRequired.collectAsStateWithLifecycle()

    val account by container.account.state.collectAsStateWithLifecycle()
    val syncStatus by container.account.syncStatus.collectAsStateWithLifecycle()
    var signInSheet by remember { mutableStateOf(false) }
    var accountDialog by remember { mutableStateOf<AccountDialog?>(null) }
    var accountBusy by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    // Runs in the app scope so leaving Settings doesn't cancel a sign-out halfway.
    fun signOut(force: Boolean) {
        accountBusy = true
        container.scope.launch {
            val result = container.account.signOut(force)
            withContext(Dispatchers.Main) {
                accountBusy = false
                if (result == SignOutResult.UNSYNCED) accountDialog = AccountDialog.UNSYNCED
            }
        }
    }

    // On failure the account and the library stay; the dialog says so and offers another try. Not after a 401:
    // the session is expired then, the Delete row is gone and a retry can't succeed.
    fun deleteAccount() {
        accountBusy = true
        deleting = true
        container.scope.launch {
            val deleted = container.account.deleteAccount()
            withContext(Dispatchers.Main) {
                accountBusy = false
                deleting = false
                val usable = (container.account.state.value as? AccountState.SignedIn)?.expired == false
                if (!deleted && usable) accountDialog = AccountDialog.DELETE_FAILED
            }
        }
    }

    var canNotify by remember { mutableStateOf(ReminderScheduler.canNotify(context)) }
    var dayMenu by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
    var languageDialog by remember { mutableStateOf(false) }
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
            Group(stringResource(R.string.account_section)) {
                when (val state = account) {
                    AccountState.SignedOut -> NavRow(Icons.Filled.Sync, stringResource(R.string.account_sign_in_row)) {
                        signInSheet = true
                    }
                    is AccountState.SignedIn -> {
                        AccountHeader(
                            state.user,
                            accountStatusText(state, syncStatus),
                            warning = state.expired || (syncStatus.failed && !syncStatus.running),
                        )
                        Divider()
                        if (state.expired) {
                            NavRow(Icons.Filled.Sync, stringResource(R.string.account_sign_in_row)) { signInSheet = true }
                        } else {
                            LinkRow(stringResource(R.string.account_sync_now), enabled = !accountBusy && !syncStatus.running) {
                                container.sync.syncNow()
                            }
                        }
                        Divider()
                        LinkRow(stringResource(R.string.account_sign_out), enabled = !accountBusy) {
                            accountDialog = AccountDialog.SIGN_OUT
                        }
                        if (!state.expired) {
                            Divider()
                            LinkRow(stringResource(R.string.account_delete), enabled = !accountBusy, color = c.danger, progress = deleting) {
                                accountDialog = AccountDialog.DELETE
                            }
                        }
                    }
                }
            }

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

            Group(stringResource(R.string.settings_language)) {
                ValueRow(
                    stringResource(R.string.settings_language),
                    languageLabel(language),
                ) { languageDialog = true }
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

    if (signInSheet) SignInSheet(onDismiss = { signInSheet = false })

    accountDialog?.let { dialog ->
        val dismiss = { accountDialog = null }
        when (dialog) {
            AccountDialog.SIGN_OUT -> ConfirmDialog(
                title = stringResource(R.string.account_sign_out_confirm_title),
                body = stringResource(R.string.account_sign_out_confirm_body),
                confirm = stringResource(R.string.account_sign_out),
                destructive = true,
                onDismiss = dismiss,
            ) { signOut(force = false) }
            AccountDialog.UNSYNCED -> ConfirmDialog(
                title = stringResource(R.string.account_sign_out_confirm_title),
                body = stringResource(R.string.account_sign_out_unsynced),
                confirm = stringResource(R.string.account_sign_out),
                destructive = true,
                onDismiss = dismiss,
            ) { signOut(force = true) }
            AccountDialog.DELETE -> ConfirmDialog(
                title = stringResource(R.string.account_delete_confirm_title),
                body = stringResource(R.string.account_delete_confirm_body),
                confirm = stringResource(R.string.account_delete_confirm_action),
                destructive = true,
                onDismiss = dismiss,
            ) { deleteAccount() }
            AccountDialog.DELETE_FAILED -> ConfirmDialog(
                title = stringResource(R.string.account_delete_confirm_title),
                body = stringResource(R.string.account_delete_failed),
                confirm = stringResource(R.string.account_delete_confirm_action),
                destructive = true,
                onDismiss = dismiss,
            ) { deleteAccount() }
        }
    }

    if (languageDialog) {
        AlertDialog(
            onDismissRequest = { languageDialog = false },
            containerColor = c.surface,
            confirmButton = {
                TextButton(onClick = { languageDialog = false }) { Text(stringResource(R.string.action_cancel), color = c.ink) }
            },
            title = { Text(stringResource(R.string.settings_language), color = c.ink) },
            text = {
                Column {
                    AppLocale.CHOICES.forEach { tag ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    languageDialog = false
                                    if (tag != language) {
                                        settings.setLanguage(tag)
                                        // Recreating the activity re-runs attachBaseContext with the new language.
                                        (context as? Activity)?.recreate()
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = tag == language,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = c.ink, unselectedColor = c.gray),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(languageLabel(tag), color = c.ink, fontSize = 16.sp)
                        }
                    }
                }
            },
        )
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

/** `https://socialsave.cc/{terms|privacy}?lang=…`, shared with the sign-in sheet. */
fun legalUrl(path: String, language: String): String {
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
private fun languageLabel(tag: String): String = when (tag) {
    AppLocale.GEORGIAN -> stringResource(R.string.language_georgian)
    AppLocale.ENGLISH -> stringResource(R.string.language_english)
    AppLocale.RUSSIAN -> stringResource(R.string.language_russian)
    else -> stringResource(R.string.language_system)
}

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
private fun LinkRow(
    title: String,
    enabled: Boolean = true,
    color: Color = Brand.colors.ink,
    progress: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = if (enabled) color else color.copy(alpha = 0.4f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (progress) CircularProgressIndicator(color = color, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
    }
}

private enum class AccountDialog { SIGN_OUT, UNSYNCED, DELETE, DELETE_FAILED }

@Composable
private fun accountStatusText(state: AccountState.SignedIn, sync: SyncStatus): String {
    val context = LocalContext.current
    return when {
        state.expired -> stringResource(R.string.account_session_expired)
        sync.running -> stringResource(R.string.account_syncing)
        sync.failed -> stringResource(R.string.account_sync_failed)
        sync.lastSyncedAt != null -> stringResource(R.string.account_last_synced, lastSyncedText(context, sync.lastSyncedAt))
        else -> stringResource(R.string.account_never_synced)
    }
}

/** "just now", "5 min ago", "today, 14:20" — lower-cased to read inside "Last synced %s"; dates stay as they are. */
private fun lastSyncedText(context: android.content.Context, millis: Long): String {
    val now = System.currentTimeMillis()
    val text = RelativeTime.detailed(context, millis, now)
    val locale = context.resources.configuration.locales[0]
    return if (now - millis < 6L * 24 * 60 * 60 * 1000) text.replaceFirstChar { it.lowercase(locale) } else text
}

@Composable
private fun AccountHeader(user: AccountUser, status: String, warning: Boolean) {
    val c = Brand.colors
    val title = user.name?.takeIf { it.isNotBlank() } ?: user.email.orEmpty()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        // The letter stays underneath: it shows while the photo loads and when there is none or it fails.
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) {
            Text(
                title.firstOrNull()?.uppercase() ?: "?",
                color = c.canvas,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!user.avatarUrl.isNullOrBlank()) {
                AsyncImage(user.avatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            if (!user.email.isNullOrBlank() && user.email != title) {
                Text(user.email, color = c.gray, fontSize = 14.sp, maxLines = 1)
            }
            Spacer(Modifier.height(2.dp))
            Text(status, color = if (warning) c.danger else c.gray, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    destructive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val c = Brand.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, color = c.ink) },
        text = { Text(body, color = c.gray) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(confirm, color = if (destructive) c.danger else c.ink, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = c.ink) }
        },
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
