package com.ich.socialsave.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.android.gms.ads.nativead.NativeAd
import com.ich.socialsave.R
import com.ich.socialsave.ads.NativeAdCard
import com.ich.socialsave.ads.NativeAdsController
import com.ich.socialsave.container
import com.ich.socialsave.data.Category
import com.ich.socialsave.data.SavedPost
import com.ich.socialsave.ui.theme.Brand
import com.ich.socialsave.util.RelativeTime
import com.ich.socialsave.util.displayTitle
import com.ich.socialsave.util.label
import java.io.File

/** Actions a grid can ask its screen to perform. */
interface PostGridActions {
    fun onTap(post: SavedPost)
    fun onDetails(post: SavedPost)
    fun onDelete(post: SavedPost)
    fun onLongPressSelect(post: SavedPost) {}
    fun onToggleFavorite(post: SavedPost)
    fun onMove(post: SavedPost, categoryId: String?)
}

private sealed interface GridEntry {
    data class Post(val post: SavedPost) : GridEntry
    data class Ad(val slot: Int, val ad: NativeAd) : GridEntry
}

/**
 * Two-column grid of posts shared by the library and collection screens. Outside selection mode a native ad
 * appears after every [NativeAdsController.POSTS_PER_AD] posts, once one has loaded.
 */
@Composable
fun PostGrid(
    posts: List<SavedPost>,
    categories: List<Category>,
    actions: PostGridActions,
    modifier: Modifier = Modifier,
    selection: Set<String>? = null,
    libraryChrome: Boolean = false,
    bottomPadding: Dp = 32.dp,
) {
    val context = LocalContext.current
    val adsController = context.container.ads
    val adsReady by adsController.ready.collectAsStateWithLifecycle()
    val ads by adsController.ads.collectAsStateWithLifecycle()
    val wantedAds = if (selection == null) posts.size / NativeAdsController.POSTS_PER_AD else 0

    LaunchedEffect(wantedAds, adsReady) { adsController.ensureLoaded(wantedAds) }

    val lookup = remember(categories) { categories.associateBy { it.id } }
    val entries = remember(posts, ads, wantedAds) {
        buildList {
            posts.forEachIndexed { index, post ->
                add(GridEntry.Post(post))
                val slot = (index + 1) / NativeAdsController.POSTS_PER_AD - 1
                if ((index + 1) % NativeAdsController.POSTS_PER_AD == 0 && slot < wantedAds) {
                    ads.getOrNull(slot)?.let { add(GridEntry.Ad(slot, it)) }
                }
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(
            entries,
            key = { entry ->
                when (entry) {
                    is GridEntry.Post -> entry.post.id
                    is GridEntry.Ad -> "ad-${entry.slot}"
                }
            },
            contentType = { if (it is GridEntry.Ad) "ad" else "post" },
        ) { entry ->
            when (entry) {
                is GridEntry.Ad -> NativeAdCard(entry.ad, Modifier.fillMaxWidth())
                is GridEntry.Post -> PostCardWithMenu(
                    post = entry.post,
                    category = entry.post.categoryId?.let(lookup::get),
                    categories = categories,
                    libraryChrome = libraryChrome,
                    selected = selection?.contains(entry.post.id),
                    actions = actions,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostCardWithMenu(
    post: SavedPost,
    category: Category?,
    categories: List<Category>,
    libraryChrome: Boolean,
    selected: Boolean?,
    actions: PostGridActions,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        PostCard(
            post = post,
            category = category,
            libraryChrome = libraryChrome,
            selected = selected,
            modifier = Modifier.combinedClickable(
                onClick = { actions.onTap(post) },
                onLongClick = {
                    if (selected == null) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    } else {
                        actions.onLongPressSelect(post)
                    }
                },
            ),
        )
        PostMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            post = post,
            categories = categories,
            actions = actions,
        )
    }
}

@Composable
fun PostCard(
    post: SavedPost,
    category: Category?,
    libraryChrome: Boolean,
    selected: Boolean?,
    modifier: Modifier = Modifier,
) {
    val c = Brand.colors
    val context = LocalContext.current
    val shape = RoundedCornerShape(Brand.cardRadius)
    val file = remember(post.thumbnailFile) { context.container.thumbnails.file(post.thumbnailFile) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(3.dp, shape, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.06f))
                .clip(shape)
                .background(c.surface)
                .border(
                    width = if (selected == true) 2.dp else 1.dp,
                    color = if (selected == true) c.ink else c.line.copy(alpha = if (file == null) 1f else 0.5f),
                    shape = shape,
                ),
        ) {
            PostThumbnail(post, file)
            if (file != null && !libraryChrome) {
                PlatformBadge(post.socialPlatform, Modifier.align(Alignment.TopStart).padding(8.dp))
            }
            if (selected == false) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
            }
            when {
                selected != null -> Icon(
                    if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    null,
                    tint = if (selected) c.ink else Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (selected) c.canvas else Color.Black.copy(alpha = 0.25f)),
                )
                post.isFavorite -> Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Star, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        if (libraryChrome) {
            val meta = if (category != null) "${post.socialPlatform.label()} · ${category.name}" else post.socialPlatform.label()
            Text(meta, color = c.gray, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
        }
        Text(
            post.displayTitle(),
            color = c.ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 19.sp,
        )
        if (!libraryChrome && category != null) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryDot(category.colorHex, 7.dp)
                Spacer(Modifier.width(5.dp))
                Text(category.name, color = c.gray, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (libraryChrome) RelativeTime.detailed(context, post.createdAt) else RelativeTime.compact(context, post.createdAt),
            color = c.gray,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun PostThumbnail(post: SavedPost, file: File?, iconSize: Dp = 26.dp) {
    if (file != null) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        PlatformPlaceholder(post.socialPlatform, iconSize)
    }
}

@Composable
private fun PostMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    post: SavedPost,
    categories: List<Category>,
    actions: PostGridActions,
) {
    val context = LocalContext.current
    var showCategories by remember(expanded) { mutableStateOf(false) }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (showCategories) {
            CategoryMenuItems(categories, post.categoryId) { id ->
                actions.onMove(post, id)
                onDismiss()
            }
        } else {
        MenuItem(stringResource(R.string.action_open), Icons.AutoMirrored.Filled.OpenInNew) {
            openLink(context, post.url); onDismiss()
        }
        MenuItem(stringResource(R.string.action_details), Icons.Filled.Info) {
            actions.onDetails(post); onDismiss()
        }
        MenuItem(
            stringResource(if (post.isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite),
            if (post.isFavorite) Icons.Outlined.StarOutline else Icons.Filled.Star,
        ) {
            actions.onToggleFavorite(post); onDismiss()
        }
        if (categories.isNotEmpty()) {
            MenuItem(stringResource(R.string.action_move_to_category), Icons.Filled.Folder) { showCategories = true }
        }
        MenuItem(stringResource(R.string.action_copy_link), Icons.Filled.ContentCopy) {
            copyLink(context, post.url); onDismiss()
        }
        MenuItem(stringResource(R.string.action_share), Icons.Filled.Share) {
            shareTextIntent(context, post.url); onDismiss()
        }
        HorizontalDivider(color = Brand.colors.line)
        MenuItem(stringResource(R.string.action_delete), Icons.Filled.Delete, tint = Brand.colors.danger) {
            actions.onDelete(post); onDismiss()
        }
        }
    }
}

@Composable
fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, tint: Color = Brand.colors.ink, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = tint) },
        leadingIcon = icon?.let { { Icon(it, null, tint = tint) } },
        onClick = onClick,
    )
}

/** "No category" plus every category, with a check on the current one. */
@Composable
fun CategoryMenuItems(categories: List<Category>, currentId: String?, onSelect: (String?) -> Unit) {
    val c = Brand.colors
    DropdownMenuItem(
        text = { Text(stringResource(R.string.categories_none), color = c.ink) },
        leadingIcon = { if (currentId == null) Icon(Icons.Outlined.Check, null, tint = c.ink) else Spacer(Modifier.size(24.dp)) },
        onClick = { onSelect(null) },
    )
    categories.forEach { category ->
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryDot(category.colorHex)
                    Spacer(Modifier.width(8.dp))
                    Text(category.name, color = c.ink)
                }
            },
            leadingIcon = {
                if (currentId == category.id) Icon(Icons.Outlined.Check, null, tint = c.ink) else Spacer(Modifier.size(24.dp))
            },
            onClick = { onSelect(category.id) },
        )
    }
}

fun openLink(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun copyLink(context: Context, url: String) {
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("link", url))
}

fun shareTextIntent(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
