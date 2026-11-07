package com.shoto.socialsave.ui.detail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shoto.socialsave.R
import com.shoto.socialsave.container
import com.shoto.socialsave.data.SavedPost
import com.shoto.socialsave.ui.components.CategoryDot
import com.shoto.socialsave.ui.components.CategoryMenuItems
import com.shoto.socialsave.ui.components.PlatformBadge
import com.shoto.socialsave.ui.components.PostThumbnail
import com.shoto.socialsave.ui.components.PrimaryButton
import com.shoto.socialsave.ui.components.SecondaryButton
import com.shoto.socialsave.ui.components.archiveField
import com.shoto.socialsave.ui.components.copyLink
import com.shoto.socialsave.ui.components.openLink
import com.shoto.socialsave.ui.components.shareTextIntent
import com.shoto.socialsave.ui.library.DeleteConfirmDialog
import com.shoto.socialsave.ui.theme.Brand
import com.shoto.socialsave.util.RelativeTime
import com.shoto.socialsave.util.displayTitle
import com.shoto.socialsave.util.label
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(postId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = context.container.repository
    val postState = remember(postId) { repository.observePost(postId) }.collectAsStateWithLifecycle(initialValue = null)
    val c = Brand.colors

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.detail_title), fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.a11y_back))
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = c.canvas,
                titleContentColor = c.ink,
                navigationIconContentColor = c.ink,
            ),
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
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val shape = RoundedCornerShape(Brand.cardRadius + 4.dp)
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                .clip(shape).background(c.surface)
                .border(1.dp, c.line.copy(alpha = if (file == null) 1f else 0.5f), shape),
        ) {
            PostThumbnail(post, file, iconSize = 40.dp)
            if (file != null) PlatformBadge(post.socialPlatform, Modifier.align(Alignment.TopStart).padding(12.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                post.socialPlatform.label().uppercase(), color = c.gray, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp,
            )
            Text(post.displayTitle(), color = c.ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Text("${post.displayHost} · ${RelativeTime.compact(context, post.createdAt)}", color = c.gray, fontSize = 14.sp)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.filter_category), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Box {
                val selected = categories.firstOrNull { it.id == post.categoryId }
                Row(
                    Modifier.fillMaxWidth().archiveField().clickable { categoryMenu = true }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selected != null) {
                        CategoryDot(selected.colorHex)
                        Spacer(Modifier.width(8.dp))
                        Text(selected.name, color = c.ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    } else {
                        Text(stringResource(R.string.categories_choose), color = c.gray, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    }
                    Icon(Icons.Filled.UnfoldMore, null, tint = c.gray, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    CategoryMenuItems(categories, post.categoryId) { id ->
                        categoryMenu = false
                        scope.launch { repository.move(listOf(post.id), id) }
                    }
                }
            }
        }

        PrimaryButton(stringResource(R.string.action_open), onClick = { openLink(context, post.url) })

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                stringResource(if (post.isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite),
                onClick = { scope.launch { repository.setFavorite(listOf(post.id), !post.isFavorite) } },
                icon = if (post.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                stringResource(R.string.action_copy_link),
                onClick = {
                    copyLink(context, post.url)
                    Toast.makeText(context, R.string.action_copy_link, Toast.LENGTH_SHORT).show()
                },
                icon = Icons.Filled.ContentCopy,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                null,
                onClick = { shareTextIntent(context, post.url) },
                icon = Icons.Filled.Share,
                modifier = Modifier.width(56.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.detail_note), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Box(Modifier.fillMaxWidth().heightIn(min = 84.dp).archiveField().padding(12.dp)) {
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

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.detail_link), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            SelectionContainer {
                Text(post.url, color = c.gray, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }

        SecondaryButton(
            stringResource(R.string.action_delete),
            onClick = { confirmDelete = true },
            icon = Icons.Filled.Delete,
            destructive = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
