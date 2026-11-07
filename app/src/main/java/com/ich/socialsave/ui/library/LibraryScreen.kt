package com.ich.socialsave.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ich.socialsave.R
import com.ich.socialsave.container
import com.ich.socialsave.core.SocialPlatform
import com.ich.socialsave.data.Category
import com.ich.socialsave.data.SavedPost
import com.ich.socialsave.ui.components.CategoryDot
import com.ich.socialsave.ui.components.CategoryMenuItems
import com.ich.socialsave.ui.components.FloatingAddButton
import com.ich.socialsave.ui.components.HeaderIconButton
import com.ich.socialsave.ui.components.PostGrid
import com.ich.socialsave.ui.components.PostGridActions
import com.ich.socialsave.ui.components.PrimaryButton
import com.ich.socialsave.ui.components.QuietEmpty
import com.ich.socialsave.ui.components.SearchField
import com.ich.socialsave.ui.components.TextTab
import com.ich.socialsave.ui.components.shareTextIntent
import com.ich.socialsave.ui.paste.PasteLinkSheet
import com.ich.socialsave.ui.theme.Brand
import com.ich.socialsave.util.displayTitle
import com.ich.socialsave.util.icon
import com.ich.socialsave.util.label
import com.ich.socialsave.util.shareText
import kotlinx.coroutines.launch

/** Library filter tabs: everything, favorites, or one network. */
sealed interface LibraryScope {
    data object All : LibraryScope
    data object Favorites : LibraryScope
    data class Platform(val platform: SocialPlatform) : LibraryScope

    fun matches(post: SavedPost): Boolean = when (this) {
        All -> true
        Favorites -> post.isFavorite
        is Platform -> post.socialPlatform == platform
    }
}

fun postMatches(post: SavedPost, query: String, title: String, category: Category?): Boolean {
    fun String?.has() = this?.contains(query, ignoreCase = true) == true
    return title.has() || post.url.has() || post.displayHost.has() || post.socialPlatform.displayName.has() ||
        post.notes.has() || post.authorName.has() || category?.name.has()
}

@Composable
fun LibraryScreen(
    onOpenPost: (String) -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val container = context.container
    val repository = container.repository
    val scope = rememberCoroutineScope()
    val c = Brand.colors

    val posts by remember { repository.observePosts() }.collectAsStateWithLifecycle(initialValue = null)
    val categories by remember { repository.observeCategories() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val newestFirst by container.settings.newestFirst.collectAsStateWithLifecycle()

    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var libraryScope by remember { mutableStateOf<LibraryScope>(LibraryScope.All) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf<Set<String>?>(null) }
    var pendingDelete by remember { mutableStateOf<List<SavedPost>>(emptyList()) }
    var pasteOpen by rememberSaveable { mutableStateOf(false) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }

    val allPosts = posts ?: emptyList()
    val lookup = remember(categories) { categories.associateBy { it.id } }

    LaunchedEffect(selection != null) { onSelectionModeChange(selection != null) }
    LaunchedEffect(categories) {
        if (categoryFilter != null && categories.none { it.id == categoryFilter }) categoryFilter = null
    }
    LaunchedEffect(allPosts) {
        selection = selection?.intersect(allPosts.map { it.id }.toSet())
    }
    BackHandler(enabled = selection != null) { selection = null }
    BackHandler(enabled = selection == null && searchVisible) {
        searchVisible = false
        searchText = ""
    }

    val query = searchText.trim()
    val visible = remember(allPosts, libraryScope, categoryFilter, query, newestFirst, lookup) {
        val filtered = allPosts.filter { post ->
            libraryScope.matches(post) &&
                (categoryFilter == null || post.categoryId == categoryFilter) &&
                (query.isEmpty() || postMatches(post, query, post.displayTitle(context), post.categoryId?.let(lookup::get)))
        }
        if (newestFirst) filtered else filtered.reversed()
    }

    val actions = remember(selection) {
        object : PostGridActions {
            override fun onTap(post: SavedPost) {
                val current = selection
                if (current != null) {
                    selection = if (post.id in current) current - post.id else current + post.id
                } else {
                    onOpenPost(post.id)
                }
            }
            override fun onDetails(post: SavedPost) = onOpenPost(post.id)
            override fun onDelete(post: SavedPost) { pendingDelete = listOf(post) }
            override fun onLongPressSelect(post: SavedPost) = onTap(post)
            override fun onToggleFavorite(post: SavedPost) {
                scope.launch { repository.setFavorite(listOf(post.id), !post.isFavorite) }
            }
            override fun onMove(post: SavedPost, categoryId: String?) {
                scope.launch { repository.move(listOf(post.id), categoryId) }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
                val current = selection
                if (current != null) {
                    val visibleIds = visible.map { it.id }.toSet()
                    val allSelected = visibleIds.isNotEmpty() && current.containsAll(visibleIds)
                    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { selection = null }) {
                            Text(stringResource(R.string.action_cancel), color = c.ink, fontSize = 16.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            stringResource(R.string.library_selected_count, current.size),
                            color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { selection = if (allSelected) emptySet() else visibleIds }) {
                            Text(
                                stringResource(if (allSelected) R.string.action_deselect_all else R.string.action_select_all),
                                color = c.ink, fontSize = 16.sp,
                            )
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "SocialSave", color = c.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.4).sp, maxLines = 1, modifier = Modifier.weight(1f),
                        )
                        if (allPosts.isNotEmpty()) {
                            HeaderIconButton(Icons.Outlined.CheckCircle, stringResource(R.string.action_select)) {
                                searchVisible = false
                                searchText = ""
                                selection = emptySet()
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        HeaderIconButton(Icons.Filled.FilterList, stringResource(R.string.a11y_filter), active = categoryFilter != null) {
                            filterOpen = true
                        }
                        Spacer(Modifier.width(8.dp))
                        HeaderIconButton(
                            if (searchVisible) Icons.Filled.Close else Icons.Filled.Search,
                            stringResource(R.string.a11y_search),
                            active = searchVisible,
                        ) {
                            searchVisible = !searchVisible
                            if (!searchVisible) searchText = ""
                        }
                    }
                }
                AnimatedVisibility(searchVisible && selection == null) {
                    SearchField(
                        placeholder = stringResource(R.string.search_placeholder_library),
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            if (allPosts.isNotEmpty()) {
                ScopeBar(allPosts, libraryScope) { libraryScope = it }
            }
            categoryFilter?.let(lookup::get)?.let { category ->
                ActiveFilterChip(category) { categoryFilter = null }
            }

            if (visible.isEmpty()) {
                if (posts != null) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                        EmptyState(
                            hasPosts = allPosts.isNotEmpty(),
                            query = query,
                            hasCategoryFilter = categoryFilter != null,
                            scope = libraryScope,
                            onPaste = { pasteOpen = true },
                            onClearFilter = { categoryFilter = null },
                        )
                    }
                }
            } else {
                PostGrid(
                    posts = visible,
                    categories = categories,
                    actions = actions,
                    selection = selection,
                    bottomPadding = if (selection != null) 110.dp else 96.dp,
                )
            }
        }

        if (allPosts.isNotEmpty() && selection == null) {
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 16.dp)) {
                FloatingAddButton(stringResource(R.string.a11y_add_link)) { pasteOpen = true }
            }
        }

        selection?.let { current ->
            SelectionActionBar(
                selected = allPosts.filter { it.id in current },
                categories = categories,
                modifier = Modifier.align(Alignment.BottomCenter),
                onFavorite = { list, favorite ->
                    scope.launch { repository.setFavorite(list.map { it.id }, favorite) }
                    selection = null
                },
                onMove = { list, id ->
                    scope.launch { repository.move(list.map { it.id }, id) }
                    selection = null
                },
                onShare = { list -> shareTextIntent(context, shareText(context, list)) },
                onDelete = { list -> pendingDelete = list },
            )
        }
    }

    if (pendingDelete.isNotEmpty()) {
        DeleteConfirmDialog(
            count = pendingDelete.size,
            onConfirm = {
                val ids = pendingDelete.map { it.id }
                pendingDelete = emptyList()
                selection = null
                scope.launch { repository.delete(ids) }
            },
            onDismiss = { pendingDelete = emptyList() },
        )
    }
    if (pasteOpen) {
        PasteLinkSheet(onDismiss = { pasteOpen = false })
    }
    if (filterOpen) {
        LibraryFilterSheet(
            categories = categories,
            selectedCategoryId = categoryFilter,
            newestFirst = newestFirst,
            onApply = { categoryFilter = it },
            onSortChange = container.settings::setNewestFirst,
            onClear = {
                categoryFilter = null
                container.settings.setNewestFirst(true)
            },
            onDismiss = { filterOpen = false },
        )
    }
}

@Composable
private fun ScopeBar(posts: List<SavedPost>, current: LibraryScope, onSelect: (LibraryScope) -> Unit) {
    val counts = posts.groupingBy { it.socialPlatform }.eachCount()
    val platforms = counts.entries
        .sortedWith(
            compareBy<Map.Entry<SocialPlatform, Int>> { it.key == SocialPlatform.OTHER }
                .thenByDescending { it.value }
                .thenBy { it.key.displayName },
        )
        .map { LibraryScope.Platform(it.key) }
    val scopes = buildList {
        add(LibraryScope.All)
        add(LibraryScope.Favorites)
        addAll(platforms)
        if (current is LibraryScope.Platform && current.platform !in counts) add(current)
    }
    LazyRow(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(scopes, key = { it.toString() }) { item ->
            val title = when (item) {
                LibraryScope.All -> stringResource(R.string.platform_all)
                LibraryScope.Favorites -> stringResource(R.string.library_favorites)
                is LibraryScope.Platform -> item.platform.label()
            }
            TextTab(title, selected = item == current) { onSelect(item) }
        }
    }
}

@Composable
private fun ActiveFilterChip(category: Category, onClear: () -> Unit) {
    val c = Brand.colors
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)) {
        Surface(shape = CircleShape, color = c.surface, border = BorderStroke(1.dp, c.line)) {
            Row(
                Modifier.height(Brand.chipHeight).padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryDot(category.colorHex)
                Spacer(Modifier.width(8.dp))
                Text(category.name, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(22.dp).background(c.line, CircleShape).clickable(onClick = onClear),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, stringResource(R.string.a11y_clear_filter), tint = c.gray, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    hasPosts: Boolean,
    query: String,
    hasCategoryFilter: Boolean,
    scope: LibraryScope,
    onPaste: () -> Unit,
    onClearFilter: () -> Unit,
) {
    when {
        !hasPosts -> Column {
            QuietEmpty(stringResource(R.string.home_empty_title), stringResource(R.string.home_empty_subtitle), Icons.Filled.Bookmark)
            Spacer(Modifier.height(20.dp))
            PrimaryButton(stringResource(R.string.action_paste_link), onPaste)
        }
        query.isNotEmpty() -> QuietEmpty(
            stringResource(R.string.search_no_results),
            stringResource(R.string.search_no_results_query, query),
            Icons.Filled.Search,
        )
        hasCategoryFilter -> Column {
            QuietEmpty(
                stringResource(R.string.home_category_empty_title),
                stringResource(R.string.home_category_empty_subtitle),
                Icons.Filled.Folder,
            )
            TextButton(onClick = onClearFilter) {
                Text(stringResource(R.string.action_clear_filter), color = Brand.colors.ink, fontWeight = FontWeight.Medium)
            }
        }
        else -> {
            val (title, subtitle, icon) = when (scope) {
                LibraryScope.All -> Triple(
                    stringResource(R.string.platform_empty_all_title),
                    stringResource(R.string.platform_empty_all_subtitle),
                    Icons.Filled.GridView,
                )
                LibraryScope.Favorites -> Triple(
                    stringResource(R.string.library_favorites_empty_title),
                    stringResource(R.string.library_favorites_empty_subtitle),
                    Icons.Outlined.Star,
                )
                is LibraryScope.Platform -> if (scope.platform == SocialPlatform.OTHER) {
                    Triple(
                        stringResource(R.string.platform_empty_other_title),
                        stringResource(R.string.platform_empty_other_subtitle),
                        scope.platform.icon,
                    )
                } else {
                    Triple(
                        stringResource(R.string.platform_empty_named_title, scope.platform.displayName),
                        stringResource(R.string.platform_empty_named_subtitle, scope.platform.displayName),
                        scope.platform.icon,
                    )
                }
            }
            QuietEmpty(title, subtitle, icon)
        }
    }
}

@Composable
private fun SelectionActionBar(
    selected: List<SavedPost>,
    categories: List<Category>,
    modifier: Modifier,
    onFavorite: (List<SavedPost>, Boolean) -> Unit,
    onMove: (List<SavedPost>, String?) -> Unit,
    onShare: (List<SavedPost>) -> Unit,
    onDelete: (List<SavedPost>) -> Unit,
) {
    val c = Brand.colors
    val allFavorite = selected.isNotEmpty() && selected.all { it.isFavorite }
    var moveOpen by remember { mutableStateOf(false) }
    val enabled = selected.isNotEmpty()

    Column(modifier.fillMaxWidth().background(c.surface).navigationBarsPadding()) {
        HorizontalDivider(color = c.line)
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
            BarButton(
                stringResource(if (allFavorite) R.string.action_remove_favorite else R.string.action_add_favorite),
                if (allFavorite) Icons.Outlined.StarOutline else Icons.Outlined.Star,
                enabled,
                Modifier.weight(1f),
            ) { onFavorite(selected, !allFavorite) }
            Box(Modifier.weight(1f)) {
                BarButton(
                    stringResource(R.string.action_move_to_category),
                    Icons.Filled.Folder,
                    enabled && categories.isNotEmpty(),
                    Modifier.fillMaxWidth(),
                ) { moveOpen = true }
                DropdownMenu(expanded = moveOpen, onDismissRequest = { moveOpen = false }) {
                    CategoryMenuItems(categories, null) { id ->
                        moveOpen = false
                        onMove(selected, id)
                    }
                }
            }
            BarButton(stringResource(R.string.action_share), Icons.Filled.Share, enabled, Modifier.weight(1f)) { onShare(selected) }
            BarButton(stringResource(R.string.action_delete), Icons.Filled.Delete, enabled, Modifier.weight(1f), tint = c.danger) { onDelete(selected) }
        }
    }
}

@Composable
private fun BarButton(
    title: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier,
    tint: androidx.compose.ui.graphics.Color = Brand.colors.ink,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(title, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun DeleteConfirmDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = Brand.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = {
            Text(
                if (count > 1) stringResource(R.string.library_delete_many_confirm, count)
                else stringResource(R.string.detail_delete_confirm),
                color = c.ink,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete), color = c.danger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = c.ink) }
        },
    )
}
