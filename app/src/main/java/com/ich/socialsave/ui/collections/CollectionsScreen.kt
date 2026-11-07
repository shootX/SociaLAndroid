package com.ich.socialsave.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TextButton
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
            add(Tile(CollectionKind.Favorites, "", favorites, covers["favorites"], Icons.Filled.Star, c.ink))
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
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle(stringResource(R.string.categories_title), Modifier.weight(1f))
            if (categories.isNotEmpty()) {
                TextButton(onClick = { managerOpen = true }) {
                    Text(stringResource(R.string.action_edit), color = c.ink, fontSize = 16.sp)
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
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
                    CollectionTile(title, tile.count, tile.cover, tile.icon, tile.tint) { onOpen(tile.kind) }
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
private fun CollectionTile(title: String, count: Int, cover: String?, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    val c = Brand.colors
    val context = LocalContext.current
    val shape = RoundedCornerShape(Brand.cardRadius)
    val file = remember(cover) { context.container.thumbnails.file(cover) }
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
                .background(if (file == null) tint.copy(alpha = 0.12f) else c.surface)
                .border(1.dp, c.line.copy(alpha = 0.7f), shape),
            contentAlignment = Alignment.Center,
        ) {
            if (file != null) {
                AsyncImage(file, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(
                    Modifier.align(Alignment.BottomStart).padding(8.dp).size(28.dp)
                        .background(tint, CircleShape)
                        .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            } else {
                Icon(icon, null, tint = tint, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(stringResource(R.string.categories_count, count), color = c.gray, fontSize = 13.sp)
    }
}

@Composable
private fun NewCollectionTile(onClick: () -> Unit) {
    val c = Brand.colors
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = c.line,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(Brand.cardRadius.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, null, tint = c.gray, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.categories_new), color = c.gray, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(" ", fontSize = 13.sp)
    }
}
