package com.shoto.socialsave.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shoto.socialsave.R
import com.shoto.socialsave.container
import com.shoto.socialsave.data.SavedPost
import com.shoto.socialsave.ui.components.PostGrid
import com.shoto.socialsave.ui.components.PostGridActions
import com.shoto.socialsave.ui.components.QuietEmpty
import com.shoto.socialsave.ui.components.shareTextIntent
import com.shoto.socialsave.ui.library.DeleteConfirmDialog
import com.shoto.socialsave.ui.theme.Brand
import com.shoto.socialsave.util.shareText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(kind: CollectionKind, onBack: () -> Unit, onOpenPost: (String) -> Unit) {
    val context = LocalContext.current
    val repository = context.container.repository
    val scope = rememberCoroutineScope()
    val c = Brand.colors

    val posts by repository.observePosts().collectAsStateWithLifecycle(initialValue = emptyList())
    val categoriesState = repository.observeCategories().collectAsStateWithLifecycle(initialValue = null)
    val categories = categoriesState.value.orEmpty()
    val category = (kind as? CollectionKind.OfCategory)?.let { k -> categories.firstOrNull { it.id == k.id } }

    var pendingDelete by remember { mutableStateOf<SavedPost?>(null) }
    var editing by remember { mutableStateOf(false) }

    // A deleted category closes its screen.
    LaunchedEffect(categoriesState.value, category) {
        if (kind is CollectionKind.OfCategory && categoriesState.value != null && category == null) onBack()
    }

    val title = when (kind) {
        CollectionKind.Favorites -> stringResource(R.string.library_favorites)
        CollectionKind.Uncategorized -> stringResource(R.string.categories_none)
        is CollectionKind.OfCategory -> category?.name.orEmpty()
    }
    val visible = remember(posts, kind) { posts.filter(kind::matches) }

    val actions = remember {
        object : PostGridActions {
            override fun onTap(post: SavedPost) = onOpenPost(post.id)
            override fun onDetails(post: SavedPost) = onOpenPost(post.id)
            override fun onDelete(post: SavedPost) { pendingDelete = post }
            override fun onToggleFavorite(post: SavedPost) {
                scope.launch { repository.setFavorite(listOf(post.id), !post.isFavorite) }
            }
            override fun onMove(post: SavedPost, categoryId: String?) {
                scope.launch { repository.move(listOf(post.id), categoryId) }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        CenterAlignedTopAppBar(
            title = { Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.a11y_back))
                }
            },
            actions = {
                if (visible.isNotEmpty()) {
                    IconButton(onClick = { shareTextIntent(context, shareText(context, visible, title)) }) {
                        Icon(Icons.Filled.Share, stringResource(R.string.action_share))
                    }
                }
                if (category != null) {
                    IconButton(onClick = { editing = true }) {
                        Icon(Icons.Filled.Edit, stringResource(R.string.action_edit))
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = c.canvas,
                titleContentColor = c.ink,
                navigationIconContentColor = c.ink,
                actionIconContentColor = c.ink,
            ),
        )
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                if (kind == CollectionKind.Favorites) {
                    QuietEmpty(
                        stringResource(R.string.library_favorites_empty_title),
                        stringResource(R.string.library_favorites_empty_subtitle),
                        Icons.Outlined.Star,
                    )
                } else {
                    QuietEmpty(
                        stringResource(R.string.saved_category_empty_title),
                        stringResource(R.string.saved_category_empty_subtitle),
                        Icons.Filled.Folder,
                    )
                }
            }
        } else {
            PostGrid(posts = visible, categories = categories, actions = actions, libraryChrome = true)
        }
    }

    pendingDelete?.let { post ->
        DeleteConfirmDialog(
            count = 1,
            onConfirm = {
                pendingDelete = null
                scope.launch { repository.delete(listOf(post.id)) }
            },
            onDismiss = { pendingDelete = null },
        )
    }
    if (editing && category != null) {
        CategoryEditorSheet(existing = category, onDismiss = { editing = false })
    }
}
