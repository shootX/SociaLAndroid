package com.ich.socialsave.ui.paste

import android.content.ClipboardManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ich.socialsave.R
import com.ich.socialsave.container
import com.ich.socialsave.core.UrlNormalizer
import com.ich.socialsave.data.SaveResult
import com.ich.socialsave.ui.components.AccentHeadline
import com.ich.socialsave.ui.components.FieldDivider
import com.ich.socialsave.ui.components.PillRow
import com.ich.socialsave.ui.components.PrimaryButton
import com.ich.socialsave.ui.components.SectionLabel
import com.ich.socialsave.ui.components.archiveField
import com.ich.socialsave.ui.theme.Brand
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasteLinkSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = context.container.repository
    val categories by remember { repository.observeCategories() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val c = Brand.colors

    var url by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Prefill when the clipboard holds a web link (Android only lets a focused app read it).
        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        UrlNormalizer.firstHttpUrl(text.orEmpty())?.let { url = it }
    }

    fun save() {
        if (url.isBlank() || saving) return
        saving = true
        scope.launch {
            when (repository.saveLink(url, categoryId)) {
                is SaveResult.Saved -> saved = true
                SaveResult.AlreadyExists -> error = context.getString(R.string.error_already_saved)
                SaveResult.InvalidUrl -> error = context.getString(R.string.error_invalid_url)
                SaveResult.Failure -> error = context.getString(R.string.error_save_failed)
            }
            saving = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.canvas,
    ) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 24.dp)) {
            if (saved) {
                SavedSuccess(onDone = onDismiss)
            } else {
                AccentHeadline(stringResource(R.string.paste_title), fontSize = 28.sp, lineHeight = 36.sp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.paste_subtitle), color = c.gray, fontSize = 16.sp, lineHeight = 24.sp)
                Spacer(Modifier.height(24.dp))

                SectionLabel(stringResource(R.string.detail_link))
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().height(56.dp).archiveField(highlighted = url.isNotBlank()).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Link, null, tint = c.ink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    FieldDivider()
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (url.isEmpty()) Text("https://…", color = c.gray, fontSize = 16.sp)
                        BasicTextField(
                            value = url,
                            onValueChange = { url = it; error = null },
                            singleLine = true,
                            textStyle = TextStyle(color = c.ink, fontSize = 16.sp),
                            cursorBrush = SolidColor(c.ink),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                            keyboardActions = KeyboardActions(onDone = { save() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (url.isNotEmpty()) {
                        IconButton(onClick = { url = ""; error = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Cancel, null, tint = c.gray, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (categories.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SectionLabel(stringResource(R.string.filter_category))
                    Spacer(Modifier.height(8.dp))
                    Box {
                        val selected = categories.firstOrNull { it.id == categoryId }
                        PillRow(
                            icon = Icons.Outlined.Folder,
                            title = selected?.name ?: stringResource(R.string.categories_choose),
                            subtitle = null,
                            onClick = { categoryMenu = true },
                        )
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.categories_none)) },
                                onClick = { categoryId = null; categoryMenu = false },
                            )
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = { categoryId = category.id; categoryMenu = false },
                                )
                            }
                        }
                    }
                }

                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = c.danger, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(it, color = c.danger, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(24.dp))
                PrimaryButton(stringResource(R.string.action_save), onClick = { save() }, enabled = url.isNotBlank() && !saving)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.paste_supports),
                    color = c.gray, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SavedSuccess(onDone: () -> Unit) {
    val c = Brand.colors
    val haptics = LocalHapticFeedback.current
    var appeared by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (appeared) 1f else 0.6f,
        spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow),
        label = "check",
    )
    LaunchedEffect(Unit) {
        appeared = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Box(
            Modifier.size(72.dp).scale(scale).background(c.mint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, null, tint = c.onMint, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.success_title), color = c.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.success_subtitle), color = c.gray, fontSize = 16.sp, lineHeight = 24.sp)
        Spacer(Modifier.height(40.dp))
        PrimaryButton(stringResource(R.string.success_view_saved), onClick = onDone)
        Spacer(Modifier.height(24.dp))
    }
}
