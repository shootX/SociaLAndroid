package com.ich.socialsave.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ich.socialsave.core.SocialPlatform
import com.ich.socialsave.ui.theme.Brand
import com.ich.socialsave.util.badgeTitle
import com.ich.socialsave.util.icon
import com.ich.socialsave.util.label

/** Category color presets, the same muted set as iOS. */
object CategoryPalette {
    val presets = listOf("#5E6A72", "#8C6248", "#4E6A56", "#8A5346", "#6A5E74", "#3F5C6A", "#7A6842", "#6E5648")

    fun color(hex: String): Color = runCatching {
        Color(android.graphics.Color.parseColor(hex))
    }.getOrDefault(Color(0xFF5E6A72))

    fun nextHex(used: List<String>): String {
        val usedSet = used.map { it.uppercase() }.toSet()
        return presets.firstOrNull { it.uppercase() !in usedSet } ?: presets[used.size % presets.size]
    }
}

@Composable
fun TextTab(title: String, selected: Boolean, onClick: () -> Unit) {
    val c = Brand.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) c.ink else c.surface,
        border = if (selected) null else BorderStroke(1.dp, c.line),
        modifier = Modifier.height(Brand.chipHeight),
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(
                title,
                color = if (selected) c.canvas else c.ink,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun HeaderIconButton(icon: ImageVector, contentDescription: String, active: Boolean = false, onClick: () -> Unit) {
    val c = Brand.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) c.ink else c.surface,
        border = if (active) null else BorderStroke(1.dp, c.line),
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, tint = if (active) c.canvas else c.ink, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
fun FloatingAddButton(contentDescription: String, onClick: () -> Unit) {
    val c = Brand.colors
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = c.ink,
        contentColor = c.canvas,
        elevation = FloatingActionButtonDefaults.elevation(6.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription, modifier = Modifier.size(26.dp))
    }
}

@Composable
fun SearchField(placeholder: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Brand.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .archiveField()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = c.gray, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = c.gray, fontSize = 16.sp, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = c.ink, fontSize = 16.sp),
                cursorBrush = SolidColor(c.ink),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Search,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(22.dp)) {
                Icon(Icons.Filled.Cancel, null, tint = c.gray, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Surface-filled rounded field with a hairline border, used for inputs and pickers. */
@Composable
fun Modifier.archiveField(): Modifier {
    val c = Brand.colors
    val shape = RoundedCornerShape(Brand.controlRadius)
    return this
        .clip(shape)
        .background(c.surface)
        .border(1.dp, c.line, shape)
}

@Composable
fun PlatformBadge(platform: SocialPlatform, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(platform.icon, null, tint = Color.White, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text(platform.badgeTitle(context), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun PlatformPlaceholder(platform: SocialPlatform, iconSize: Dp = 26.dp, modifier: Modifier = Modifier) {
    val c = Brand.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(c.surface, c.line.copy(alpha = 0.6f)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(platform.icon, null, tint = c.gray, modifier = Modifier.size(iconSize))
            Spacer(Modifier.height(8.dp))
            Text(platform.label(), color = c.gray, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
fun QuietEmpty(title: String, subtitle: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    val c = Brand.colors
    Column(modifier = modifier.fillMaxWidth()) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(c.surface)
                    .border(1.dp, c.line, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = c.ink, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
        Text(title, color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = c.gray, fontSize = 15.sp)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Brand.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Brand.controlRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.ink,
            contentColor = c.canvas,
            disabledContainerColor = c.ink.copy(alpha = 0.28f),
            disabledContentColor = c.canvas.copy(alpha = 0.72f),
        ),
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    destructive: Boolean = false,
) {
    val c = Brand.colors
    val tint = if (destructive) c.danger else c.ink
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(Brand.controlRadius),
        border = BorderStroke(1.dp, c.line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = c.surface, contentColor = tint),
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            if (text != null) Spacer(Modifier.width(6.dp))
        }
        if (text != null) {
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = Brand.colors.gray,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        modifier = modifier,
    )
}

@Composable
fun CategoryDot(hex: String, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(CategoryPalette.color(hex)))
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Brand.colors.ink,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
        maxLines = 1,
        modifier = modifier,
    )
}

