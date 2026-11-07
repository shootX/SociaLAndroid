package com.ich.socialsave.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ich.socialsave.R
import com.ich.socialsave.account.AccountState
import com.ich.socialsave.account.SignInStatus
import com.ich.socialsave.container
import com.ich.socialsave.ui.account.NotNowLabel
import com.ich.socialsave.ui.account.SignInHeadline
import com.ich.socialsave.ui.account.SignInOptions
import com.ich.socialsave.ui.components.CategoryPalette
import com.ich.socialsave.ui.components.PrimaryButton
import com.ich.socialsave.ui.theme.Brand
import com.ich.socialsave.work.ReminderScheduler
import kotlinx.coroutines.launch

private enum class Illustration { SHARE, COLLECTIONS, REMINDER, ACCOUNT }

private data class Page(val title: Int, val body: Int, val illustration: Illustration)

private val basePages = listOf(
    Page(R.string.onboarding_share_title, R.string.onboarding_share_body, Illustration.SHARE),
    Page(R.string.onboarding_organize_title, R.string.onboarding_organize_body, Illustration.COLLECTIONS),
    Page(R.string.onboarding_remind_title, R.string.onboarding_remind_body, Illustration.REMINDER),
)

/** Optional sign-in, last; shown only while signed out. */
private val accountPage = Page(R.string.sign_in_title, R.string.sign_in_subtitle, Illustration.ACCOUNT)

/** First-launch walkthrough: saving from the share sheet, organizing, the weekly reminder, then sign-in. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val c = Brand.colors
    // Fixed for this run, so signing in on the last page doesn't change the page count under the pager.
    val pages = remember {
        if (container.account.state.value == AccountState.SignedOut) basePages + accountPage else basePages
    }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val isLast = pager.currentPage == pages.lastIndex
    val page = pages[pager.currentPage]

    val signInStatus by container.account.signInStatus.collectAsStateWithLifecycle()

    fun next() {
        if (isLast) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
    }

    // With the sign-in page in the pager, Skip jumps to it (it has its own "Not now"), like iOS.
    fun skip() {
        if (pages.last() == accountPage) scope.launch { pager.animateScrollToPage(pages.lastIndex) } else onFinish()
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        container.settings.setRemindersEnabled(granted)
        container.rescheduleReminder()
        next()
    }

    fun turnOnReminders() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !ReminderScheduler.canNotify(context)) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            container.settings.setRemindersEnabled(true)
            container.rescheduleReminder()
            next()
        }
    }

    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (!isLast) {
                TextButton(onClick = ::skip) {
                    Text(stringResource(R.string.onboarding_skip), color = c.gray, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            val shown = pages[index]
            Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
                Spacer(Modifier.weight(0.2f))
                val illustrationHeight = if (shown.illustration == Illustration.ACCOUNT) 160.dp else 300.dp
                Box(Modifier.fillMaxWidth().height(illustrationHeight), contentAlignment = Alignment.Center) {
                    when (shown.illustration) {
                        Illustration.SHARE -> ShareIllustration()
                        Illustration.COLLECTIONS -> CollectionsIllustration()
                        Illustration.REMINDER -> ReminderIllustration()
                        Illustration.ACCOUNT -> AccountIllustration()
                    }
                }
                Spacer(Modifier.weight(0.2f))
                if (shown.illustration == Illustration.ACCOUNT) {
                    SignInHeadline(TextAlign.Start, titleSize = 30)
                } else {
                    Text(stringResource(shown.title), color = c.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, lineHeight = 36.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(shown.body), color = c.gray, fontSize = 17.sp, lineHeight = 23.sp)
                }
                Spacer(Modifier.weight(0.2f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.Center) {
            pages.indices.forEach { index ->
                val width by animateDpAsState(if (index == pager.currentPage) 22.dp else 7.dp, label = "dot")
                Box(
                    Modifier.padding(horizontal = 3.dp).size(width, 7.dp).clip(CircleShape)
                        .background(if (index == pager.currentPage) c.ink else c.line),
                )
            }
        }
        if (page.illustration == Illustration.ACCOUNT) {
            SignInOptions(onSignedIn = onFinish, modifier = Modifier.padding(horizontal = 24.dp))
            val notNowEnabled = signInStatus != SignInStatus.WORKING
            TextButton(onClick = onFinish, enabled = notNowEnabled, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                NotNowLabel(notNowEnabled)
            }
        } else {
            val reminderPage = page.illustration == Illustration.REMINDER
            PrimaryButton(
                stringResource(if (reminderPage) R.string.onboarding_start else R.string.onboarding_next),
                onClick = { if (reminderPage) turnOnReminders() else next() },
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            TextButton(
                onClick = ::next,
                enabled = reminderPage,
                modifier = Modifier.fillMaxWidth().alpha(if (reminderPage) 1f else 0f).padding(bottom = 4.dp),
            ) {
                Text(stringResource(R.string.onboarding_no_reminders), color = c.gray, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val c = Brand.colors
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
            .background(c.surface, shape).border(1.dp, c.line, shape),
    ) { content() }
}

@Composable
private fun ShareIllustration() {
    val c = Brand.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 22.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                FakeApp(Icons.Filled.Message)
                FakeApp(Icons.Filled.Email)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painterResource(R.drawable.brand_mark),
                        null,
                        modifier = Modifier.size(58.dp).border(2.5.dp, c.ink, RoundedCornerShape(18.dp)).padding(5.dp).clip(RoundedCornerShape(14.dp)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("SocialSave", color = c.ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                FakeApp(Icons.Filled.Description)
            }
        }
        Row(
            Modifier.height(44.dp).background(c.surface, CircleShape).border(1.dp, c.line, CircleShape).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Share, null, tint = c.ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.onboarding_share_hint), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun FakeApp(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val c = Brand.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).background(c.gray.copy(alpha = 0.35f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(" ", fontSize = 11.sp)
    }
}

@Composable
private fun CollectionsIllustration() {
    val c = Brand.colors
    val colors = CategoryPalette.presets.take(4).map(CategoryPalette::color)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(0, 2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(row, row + 1).forEach { index ->
                    Box(
                        Modifier.size(118.dp).background(colors[index].copy(alpha = 0.16f), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (index == 0) Icons.Filled.Star else Icons.Filled.Folder,
                            null,
                            tint = if (index == 0) c.ink else colors[index],
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderIllustration() {
    val c = Brand.colors
    Card {
        Row(Modifier.widthIn(max = 320.dp).padding(16.dp)) {
            Image(painterResource(R.drawable.brand_mark), null, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text("SocialSave", color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.time_just_now), color = c.gray, fontSize = 12.sp)
                }
                Spacer(Modifier.height(3.dp))
                Text(stringResource(R.string.onboarding_remind_sample), color = c.ink, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun AccountIllustration() {
    val c = Brand.colors
    Card {
        Row(
            Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Device(Icons.Filled.PhoneAndroid)
            Icon(Icons.Filled.Sync, null, tint = c.gray, modifier = Modifier.size(22.dp))
            Image(painterResource(R.drawable.brand_mark), null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(15.dp)))
            Icon(Icons.Filled.Sync, null, tint = c.gray, modifier = Modifier.size(22.dp))
            Device(Icons.Filled.PhoneIphone)
        }
    }
}

@Composable
private fun Device(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val c = Brand.colors
    Box(Modifier.size(48.dp).background(c.line, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = c.ink, modifier = Modifier.size(24.dp))
    }
}
