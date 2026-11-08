package com.ich.socialsave.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
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
import com.ich.socialsave.ui.components.AccentHeadline
import com.ich.socialsave.ui.components.BrandLogo
import com.ich.socialsave.ui.components.BrandMark
import com.ich.socialsave.ui.components.CategoryPalette
import com.ich.socialsave.ui.components.PillTextButton
import com.ich.socialsave.ui.components.PrimaryButton
import com.ich.socialsave.ui.components.StepCounter
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
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp).height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandLogo(Modifier.weight(1f))
            if (!isLast) PillTextButton(stringResource(R.string.onboarding_skip), onClick = ::skip)
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            OnboardingPage(pages[index], index, pages.size) {
                SignInOptions(onSignedIn = onFinish)
                val notNowEnabled = signInStatus != SignInStatus.WORKING
                TextButton(onClick = onFinish, enabled = notNowEnabled, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    NotNowLabel(notNowEnabled)
                }
            }
        }
        if (page.illustration != Illustration.ACCOUNT) {
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
                Text(stringResource(R.string.onboarding_no_reminders), color = c.gray, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Text at the top, the illustration centered in what's left. Scrolls on phones too short to fit it all. */
@Composable
private fun OnboardingPage(page: Page, index: Int, total: Int, accountOptions: @Composable () -> Unit) {
    val c = Brand.colors
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 560.dp
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.padding(top = if (compact) 16.dp else 40.dp)) {
                if (page.illustration == Illustration.ACCOUNT) {
                    SignInHeadline(TextAlign.Start, titleSize = if (compact) 28 else 36)
                } else {
                    AccentHeadline(
                        stringResource(page.title),
                        fontSize = if (compact) 28.sp else 36.sp,
                        lineHeight = if (compact) 36.sp else 44.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(page.body), color = c.gray, fontSize = 16.sp, lineHeight = 24.sp)
                }
                Spacer(Modifier.height(20.dp))
                StepCounter(index, total)
            }
            if (page.illustration == Illustration.ACCOUNT) {
                Column(Modifier.padding(top = 16.dp)) { accountOptions() }
            } else {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    when (page.illustration) {
                        Illustration.SHARE -> ShareIllustration()
                        Illustration.COLLECTIONS -> CollectionsIllustration(compact)
                        else -> ReminderIllustration()
                    }
                }
                Spacer(Modifier.height(0.dp))
            }
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Brand.colors
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.background(c.surface, shape).border(1.dp, c.line, shape)) { content() }
}

@Composable
private fun ShareIllustration() {
    val c = Brand.colors
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FakeApp(Icons.Outlined.ChatBubbleOutline)
                FakeApp(Icons.Outlined.Email)
                Box {
                    Box(
                        Modifier.size(68.dp).border(2.dp, c.accent, RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        BrandMark(60.dp)
                    }
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).size(22.dp).background(c.mint, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, null, tint = c.onMint, modifier = Modifier.size(14.dp))
                    }
                }
                FakeApp(Icons.Outlined.Description)
            }
        }
        Surface(shape = CircleShape, color = c.accentSoft, border = BorderStroke(1.5.dp, c.accent)) {
            Row(Modifier.height(44.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Share, null, tint = c.ink, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_share_hint), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun FakeApp(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val c = Brand.colors
    Box(Modifier.size(60.dp).background(c.muted, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = c.ink, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun CollectionsIllustration(compact: Boolean) {
    val c = Brand.colors
    val colors = CategoryPalette.presets.take(4).map(CategoryPalette::color)
    val shape = RoundedCornerShape(Brand.cardRadius)
    val tileSize = if (compact) 72.dp else 104.dp
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(0, 2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(row, row + 1).forEach { index ->
                    val favorites = index == 0
                    Box(
                        Modifier
                            .size(tileSize)
                            .background(c.surface, shape)
                            .border(if (favorites) 2.dp else 1.dp, if (favorites) c.accent else c.line, shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (favorites) Icons.Filled.Star else Icons.Filled.Folder,
                            null,
                            tint = if (favorites) c.mint else colors[index],
                            modifier = Modifier.size(tileSize * 0.4f),
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
    Card(Modifier.widthIn(max = 342.dp)) {
        Row(Modifier.padding(16.dp)) {
            BrandMark(40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text("SocialSave", color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.time_just_now), color = c.gray, fontSize = 13.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.onboarding_remind_sample), color = c.ink, fontSize = 14.sp)
            }
        }
    }
}
