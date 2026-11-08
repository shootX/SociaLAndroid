package com.ich.socialsave.ui.collections

import com.ich.socialsave.ui.components.HeaderIconButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ich.socialsave.R
import com.ich.socialsave.container
import com.ich.socialsave.data.SavedPost
import com.ich.socialsave.ui.components.CategoryPalette
import com.ich.socialsave.ui.components.ScreenTitle
import com.ich.socialsave.ui.theme.Brand

/** What a collection screen shows: favorites, posts without a category, or one category. */
sealed interface CollectionKind {
    data object Favorites : CollectionKind
    data object Uncategorized : CollectionKind
    data class OfCategory(val id: String) : CollectionKind

    fun matches(post: SavedPost): Boolean = when (this) {
        Favorites -> post.isFavorite
        Uncategorized -> post.categoryId == null
        is OfCategory -> post.categoryId == id
    }

    /** Route argument form, e.g. "favorites" or "category:<id>". */
    val route: String
        get() = when (this) {
            Favorites -> "favorites"
            Uncategorized -> "uncategorized"
            is OfCategory -> "category:$id"
        }

    companion object {
        fun fromRoute(value: String): CollectionKind = when {
            value == "favorites" -> Favorites
            value == "uncategorized" -> Uncategorized
            else -> OfCategory(value.removePrefix("category:"))
        }
    }
}

private data class Tile(
    val kind: CollectionKind?,
    val title: String,
    val count: Int,
    val cover: String?,
    val icon: ImageVector,
    val tint: Color,
)

@Composable
fun CollectionsScreen(onOpen: (CollectionKind) -> Unit) {
    val context = LocalContext.current
    val repository = context.container.repository
    val posts by remember { repository.observePosts() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by remember { repository.observeCategories() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val c = Brand.colors

    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var managerOpen by rememberSaveable { mutableStateOf(false) }

    val tiles = remember(posts, categories, c) {
        // Posts arrive newest first, so the first thumbnail seen is the cover.
        val counts = mutableMapOf<String, Int>()
        val covers = mutableMapOf<String, String>()
        var favorites = 0
        var uncategorized = 0
        for (post in posts) {
            val key = post.categoryId ?: "uncategorized"
            if (post.categoryId != null) counts[key] = (counts[key] ?: 0) + 1 else uncategorized++
            if (post.isFavorite) favorites++
            post.thumbnailFile?.let { file ->
                covers.putIfAbsent(key, file)
                if (post.isFavorite) covers.putIfAbsent("favorites", file)
            }
        }
        buildList {
            add(Tile(CollectionKind.Favorites, "", favorites, covers["favorites"], Icons.Filled.Star, c.mint))
            categories.forEach {
                add(
                    Tile(
                        CollectionKind.OfCategory(it.id), it.name, counts[it.id] ?: 0, covers[it.id],
                        Icons.Filled.Folder, CategoryPalette.color(it.colorHex),
                    ),
                )
            }
            if (uncategorized > 0) {
                add(Tile(CollectionKind.Uncategorized, "", uncategorized, covers["uncategorized"], Icons.Filled.Inbox, c.gray))
            }
            add(Tile(null, "", 0, null, Icons.Filled.Add, c.gray))
        }
    }

    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding()) {
        Row(
            Modifier.heightIn(min = 44.dp).padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScreenTitle(stringResource(R.string.categories_title), Modifier.weight(1f))
            if (categories.isNotEmpty()) {
                HeaderIconButton(Icons.Outlined.Edit, stringResource(R.string.action_edit)) { managerOpen = true }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tiles, key = { it.kind?.route ?: "new" }) { tile ->
                if (tile.kind == null) {
                    NewCollectionTile { editorOpen = true }
                } else {
                    val title = when (tile.kind) {
                        CollectionKind.Favorites -> stringResource(R.string.library_favorites)
                        CollectionKind.Uncategorized -> stringResource(R.string.categories_none)
                        else -> tile.title
                    }
                    CollectionTile(title, tile.count, tile.cover, tile.icon, tile.tint, tile.kind == CollectionKind.Favorites) {
                        onOpen(tile.kind)
                    }
                }
            }
        }
    }

    if (editorOpen) {
        CategoryEditorSheet(existing = null, onDismiss = { editorOpen = false })
    }
    if (managerOpen) {
        CategoriesManagerSheet(onDismiss = { managerOpen = false })
    }
}

@Composable
private fun CollectionTile(
    title: String,
    count: Int,
    cover: String?,
    icon: ImageVector,
    tint: Color,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val c = Brand.colors
    val context = LocalContext.current
    val shape = RoundedCornerShape(Brand.cardRadius)
    val file = remember(cover) { context.container.thumbnails.file(cover) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(TileHeight)
            .clip(shape)
            .background(c.surface)
            .border(if (highlighted) 2.dp else 1.dp, if (highlighted) c.accent else c.line, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(
            stringResource(R.string.categories_count, count),
            color = c.gray,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.align(Alignment.TopStart).background(c.muted, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
        // Sits between the count and a title that may take two lines.
        Box(Modifier.align(Alignment.TopEnd).padding(top = 30.dp).size(84.dp), contentAlignment = Alignment.Center) {
            if (file != null) {
                AsyncImage(file, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)))
            } else {
                Icon(icon, null, tint = tint, modifier = Modifier.size(64.dp))
            }
        }
        Text(
            title,
            color = c.ink,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun NewCollectionTile(onClick: () -> Unit) {
    val c = Brand.colors
    Column(
        Modifier
            .fillMaxWidth()
            .height(TileHeight)
            .clip(RoundedCornerShape(Brand.cardRadius))
            .clickable(onClick = onClick)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = c.iconMuted,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(Brand.cardRadius.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(48.dp).background(c.mint, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, null, tint = c.onMint, modifier = Modifier.size(24.dp))
        }
        Text(
            stringResource(R.string.categories_new),
            color = c.ink,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val TileHeight = 184.dp
