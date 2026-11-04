package com.shoto.socialsave.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shoto.socialsave.R
import com.shoto.socialsave.data.Category
import com.shoto.socialsave.ui.components.CategoryDot
import com.shoto.socialsave.ui.components.PrimaryButton
import com.shoto.socialsave.ui.components.SectionLabel
import com.shoto.socialsave.ui.theme.Brand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFilterSheet(
    categories: List<Category>,
    selectedCategoryId: String?,
    newestFirst: Boolean,
    onApply: (String?) -> Unit,
    onSortChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Brand.colors
    var draft by remember { mutableStateOf(selectedCategoryId) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.canvas,
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.filter_title), color = c.ink, fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onClear(); onDismiss() }) {
                    Text(stringResource(R.string.action_clear), color = c.ink)
                }
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SectionLabel(stringResource(R.string.saved_sort), Modifier.padding(top = 8.dp, bottom = 6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(true to R.string.saved_newest, false to R.string.saved_oldest).forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = newestFirst == value,
                            onClick = { onSortChange(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = c.ink,
                                activeContentColor = c.canvas,
                                inactiveContainerColor = c.surface,
                                inactiveContentColor = c.ink,
                                activeBorderColor = c.line,
                                inactiveBorderColor = c.line,
                            ),
                            icon = {},
                        ) { Text(stringResource(label)) }
                    }
                }
                if (categories.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.filter_category), Modifier.padding(top = 20.dp, bottom = 6.dp))
                    CategoryRow(stringResource(R.string.filter_all_categories), null, draft == null) { draft = null }
                    categories.forEach { category ->
                        CategoryRow(category.name, category.colorHex, draft == category.id) { draft = category.id }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(stringResource(R.string.action_apply), onClick = { onApply(draft); onDismiss() })
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CategoryRow(title: String, colorHex: String?, selected: Boolean, onClick: () -> Unit) {
    val c = Brand.colors
    Surface(
        shape = RoundedCornerShape(Brand.controlRadius),
        color = if (selected) c.surface else c.canvas,
        border = if (selected) BorderStroke(1.dp, c.line) else null,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            if (colorHex != null) {
                CategoryDot(colorHex)
                Spacer(Modifier.width(10.dp))
            }
            Text(title, color = c.ink, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Filled.CheckCircle, null, tint = c.ink, modifier = Modifier.size(20.dp))
        }
    }
}
