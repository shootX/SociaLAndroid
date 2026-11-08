package com.ich.socialsave.ui.detail

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ich.socialsave.R
import com.ich.socialsave.container
import com.ich.socialsave.data.SavedPost
import com.ich.socialsave.ui.components.CategoryMenuItems
import com.ich.socialsave.ui.components.CenteredTopBar
import com.ich.socialsave.ui.components.PillRow
import com.ich.socialsave.ui.components.PlatformBadge
import com.ich.socialsave.ui.components.PostThumbnail
import com.ich.socialsave.ui.components.PrimaryButton
import com.ich.socialsave.ui.components.SecondaryButton
import com.ich.socialsave.ui.components.SectionLabel
import com.ich.socialsave.ui.components.archiveField
import com.ich.socialsave.ui.components.copyLink
import com.ich.socialsave.ui.components.openLink
import com.ich.socialsave.ui.components.shareTextIntent
import com.ich.socialsave.ui.library.DeleteConfirmDialog
import com.ich.socialsave.ui.theme.Brand
import com.ich.socialsave.util.RelativeTime
import com.ich.socialsave.util.displayTitle
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PostDetailScreen(postId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = context.container.repository
    val postState = remember(postId) { repository.observePost(postId) }.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val c = Brand.colors

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        CenteredTopBar(
            title = stringResource(R.string.detail_title),
            onBack = onBack,
            actions = {
                postState.value?.let { post ->
                    Surface(
                        onClick = { scope.launch { repository.setFavorite(listOf(post.id), !post.isFavorite) } },
                        shape = CircleShape,
                        color = if (post.isFavorite) c.mint else c.surface,
                        border = if (post.isFavorite) null else BorderStroke(1.dp, c.line),
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (post.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                stringResource(if (post.isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite),
                                tint = if (post.isFavorite) c.onMint else c.ink,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            },
        )
        postState.value?.let { PostDetailContent(it, onBack) }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun PostDetailContent(post: SavedPost, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val repository = container.repository
    val categories by remember { repository.observeCategories() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val c = Brand.colors

    var notes by remember(post.id) { mutableStateOf(post.notes.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    val file = remember(post.thumbnailFile) { container.thumbnails.file(post.thumbnailFile) }
    val latestNotes by rememberUpdatedState(notes)

    // Save notes 400 ms after typing stops, and once more when leaving the screen.
    LaunchedEffect(post.id) {
        snapshotFlow { notes }.drop(1).debounce(400).collect { repository.setNotes(post.id, it) }
    }
    DisposableEffect(post.id) {
        onDispose {
            container.scope.launch { withContext(NonCancellable) { repository.setNotes(post.id, latestNotes) } }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(24.dp)).background(c.muted),
        ) {
            PostThumbnail(post, file, iconSize = 40.dp)
            PlatformBadge(post.socialPlatform, Modifier.align(Alignment.TopStart).padding(12.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                post.displayTitle(),
                color = c.ink,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Text("${post.displayHost} · ${RelativeTime.compact(context, post.createdAt)}", color = c.gray, fontSize = 13.sp)
        }

        Box {
            val selected = categories.firstOrNull { it.id == post.categoryId }
            PillRow(
                icon = Icons.Outlined.Folder,
                title = selected?.name ?: stringResource(R.string.categories_choose),
                subtitle = stringResource(R.string.filter_category),
                onClick = { categoryMenu = true },
            )
            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                CategoryMenuItems(categories, post.categoryId) { id ->
                    categoryMenu = false
                    scope.launch { repository.move(listOf(post.id), id) }
                }
            }
        }

        PrimaryButton(stringResource(R.string.action_open), onClick = { openLink(context, post.url) })

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(
                stringResource(R.string.detail_copy),
                onClick = {
                    copyLink(context, post.url)
                    Toast.makeText(context, R.string.action_copy_link, Toast.LENGTH_SHORT).show()
                },
                icon = Icons.Outlined.ContentCopy,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                stringResource(R.string.action_share),
                onClick = { shareTextIntent(context, post.url) },
                icon = Icons.Outlined.Share,
                modifier = Modifier.weight(1f),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(R.string.detail_note))
            Box(
                Modifier.fillMaxWidth().heightIn(min = 84.dp)
                    .archiveField(RoundedCornerShape(Brand.cardRadius))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (notes.isEmpty()) Text(stringResource(R.string.detail_note_placeholder), color = c.gray, fontSize = 16.sp)
                BasicTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    textStyle = TextStyle(color = c.ink, fontSize = 16.sp),
                    cursorBrush = SolidColor(c.ink),
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(R.string.detail_link))
            SelectionContainer {
                Text(post.url, color = c.gray, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }

        SecondaryButton(
            stringResource(R.string.action_delete),
            onClick = { confirmDelete = true },
            icon = Icons.Outlined.Delete,
            destructive = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
    }

    if (confirmDelete) {
        DeleteConfirmDialog(
            count = 1,
            onConfirm = {
                confirmDelete = false
                onBack()
                container.scope.launch { repository.delete(listOf(post.id)) }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
