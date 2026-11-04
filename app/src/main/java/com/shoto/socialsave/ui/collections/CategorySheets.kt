package com.shoto.socialsave.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shoto.socialsave.R
import com.shoto.socialsave.container
import com.shoto.socialsave.data.Category
import com.shoto.socialsave.data.CategoryError
import com.shoto.socialsave.ui.components.CategoryPalette
import com.shoto.socialsave.ui.components.PrimaryButton
import com.shoto.socialsave.ui.components.QuietEmpty
import com.shoto.socialsave.ui.components.SectionLabel
import com.shoto.socialsave.ui.components.archiveField
import com.shoto.socialsave.ui.theme.Brand
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditorSheet(existing: Category?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = context.container.repository
    val categories by repository.observeCategories().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val c = Brand.colors

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var colorHex by remember { mutableStateOf(existing?.colorHex) }
    var error by remember { mutableStateOf<String?>(null) }
    val chosenHex = colorHex ?: CategoryPalette.nextHex(categories.map { it.colorHex })

    fun save() {
        scope.launch {
            try {
                if (existing == null) {
                    repository.createCategory(name, chosenHex)
                } else {
                    repository.updateCategory(existing, name, chosenHex)
                }
                onDismiss()
            } catch (e: CategoryError) {
                error = context.getString(
                    if (e is CategoryError.EmptyName) R.string.error_category_empty else R.string.error_category_duplicate,
                )
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.canvas,
    ) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
            Text(
                stringResource(if (existing == null) R.string.categories_new else R.string.filter_category),
                color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.categories_name))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().archiveField().padding(horizontal = 14.dp, vertical = 13.dp)) {
                if (name.isEmpty()) Text(stringResource(R.string.categories_name_placeholder), color = c.gray, fontSize = 16.sp)
                BasicTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    singleLine = true,
                    textStyle = TextStyle(color = c.ink, fontSize = 16.sp),
                    cursorBrush = SolidColor(c.ink),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.categories_color))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CategoryPalette.presets.forEach { hex ->
                    val selected = hex.equals(chosenHex, ignoreCase = true)
                    Box(
                        Modifier.size(38.dp).clip(CircleShape)
                            .border(2.dp, if (selected) c.ink.copy(alpha = 0.6f) else Color.Transparent, CircleShape)
                            .padding(3.dp).clip(CircleShape)
                            .background(CategoryPalette.color(hex))
                            .clickable { colorHex = hex },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Filled.Check, hex, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = c.danger, fontSize = 14.sp)
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton(stringResource(if (existing == null) R.string.action_create else R.string.action_save), ::save)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Rename, recolor, reorder and delete categories. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesManagerSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = context.container.repository
    val categories by repository.observeCategories().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val c = Brand.colors

    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Category?>(null) }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in categories.indices) return
        val reordered = categories.toMutableList().apply { add(target, removeAt(index)) }
        scope.launch { repository.reorderCategories(reordered) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.canvas,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.categories_title), color = c.ink, fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { creating = true }) {
                    Text(stringResource(R.string.categories_new), color = c.ink)
                }
            }
            if (categories.isEmpty()) {
                Column(Modifier.padding(20.dp)) {
                    QuietEmpty(
                        stringResource(R.string.categories_empty_title),
                        stringResource(R.string.categories_empty_subtitle),
                        Icons.Filled.Folder,
                    )
                }
            } else {
                LazyColumn(Modifier.padding(top = 8.dp)) {
                    itemsIndexed(categories, key = { _, it -> it.id }) { index, category ->
                        Row(
                            Modifier.fillMaxWidth().clickable { editing = category }
                                .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val tint = CategoryPalette.color(category.colorHex)
                            Box(
                                Modifier.size(32.dp).background(tint.copy(alpha = 0.16f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Folder, null, tint = tint, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(category.name, color = c.ink, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f))
                            IconButton(onClick = { move(index, -1) }, enabled = index > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, null, tint = if (index > 0) c.ink else c.line)
                            }
                            IconButton(onClick = { move(index, 1) }, enabled = index < categories.lastIndex) {
                                Icon(Icons.Filled.KeyboardArrowDown, null, tint = if (index < categories.lastIndex) c.ink else c.line)
                            }
                            IconButton(onClick = { pendingDelete = category }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), tint = c.danger)
                            }
                        }
                        HorizontalDivider(color = c.line, modifier = Modifier.padding(start = 64.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editing?.let { CategoryEditorSheet(existing = it, onDismiss = { editing = null }) }
    if (creating) CategoryEditorSheet(existing = null, onDismiss = { creating = false })
    pendingDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = c.surface,
            title = { Text(category.name, color = c.ink) },
            text = { Text(stringResource(R.string.categories_delete_confirm), color = c.gray) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch { repository.deleteCategory(category.id) }
                }) { Text(stringResource(R.string.action_delete), color = c.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel), color = c.ink) }
            },
        )
    }
}
