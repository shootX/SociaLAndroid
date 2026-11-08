package com.ich.socialsave.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ich.socialsave.R
import com.ich.socialsave.core.SocialPlatform
import com.ich.socialsave.ui.theme.Brand
import com.ich.socialsave.util.badgeTitle
import com.ich.socialsave.util.icon

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

/** The app's bookmark mark on a rounded tile. */
@Composable
fun BrandMark(size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val c = Brand.colors
    Box(
        modifier
            .size(size)
            .background(if (c.isDark) c.mint else c.ink, RoundedCornerShape(size * 0.31f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_notification),
            null,
            tint = if (c.isDark) c.onMint else c.mint,
            modifier = Modifier.size(size * 0.72f),
        )
    }
}

@Composable
fun BrandLogo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandMark()
        Spacer(Modifier.width(8.dp))
        Text("SocialSave", color = Brand.colors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, maxLines = 1)
    }
}

/** A bold headline whose last word is green. */
@Composable
fun AccentHeadline(
    text: String,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    val c = Brand.colors
    val lastWord = text.trimEnd().lastIndexOf(' ') + 1
    Text(
        buildAnnotatedString {
            append(text.substring(0, lastWord))
            withStyle(SpanStyle(color = c.accentHeadline)) { append(text.substring(lastWord)) }
        },
        color = c.ink,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        textAlign = textAlign,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Tick marks and "01/04" for a multi-step flow. */
@Composable
fun StepCounter(index: Int, total: Int, modifier: Modifier = Modifier) {
    val c = Brand.colors
    fun twoDigits(value: Int) = value.toString().padStart(2, '0')
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(total) { step ->
                val current = step == index
                Box(
                    Modifier.size(3.dp, if (current) 16.dp else 10.dp)
                        .background(if (current) c.ink else c.iconMuted.copy(alpha = 0.5f), CircleShape),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(twoDigits(index + 1)) }
                withStyle(SpanStyle(color = c.gray)) { append("/${twoDigits(total)}") }
            },
            color = c.ink,
            fontSize = 16.sp,
        )
    }
}

@Composable
fun TextTab(title: String, selected: Boolean, onClick: () -> Unit) {
    val c = Brand.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) c.accentSoft else c.muted,
        border = if (selected) BorderStroke(1.5.dp, c.accent) else null,
        modifier = Modifier.height(Brand.chipHeight),
    ) {
        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(
                title,
                color = c.ink,
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
        color = if (active) c.accentSoft else c.surface,
        border = if (active) BorderStroke(1.5.dp, c.accent) else BorderStroke(1.dp, c.line),
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, tint = c.ink, modifier = Modifier.size(20.dp))
        }
    }
}

/** Small outlined pill with a text label, for header actions such as Skip or Edit. */
@Composable
fun PillTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Brand.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = c.surface,
        border = BorderStroke(1.dp, c.line),
        modifier = modifier.height(44.dp),
    ) {
        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(text, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

/** Back pill, centered title and optional actions at the end. */
@Composable
fun CenteredTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    val c = Brand.colors
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = c.surface,
                border = BorderStroke(1.dp, c.line),
                modifier = Modifier.height(44.dp),
            ) {
                Row(Modifier.padding(start = 10.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = c.ink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.a11y_back), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
        Text(
            title,
            color = c.ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

@Composable
fun FloatingAddButton(contentDescription: String, onClick: () -> Unit) {
    val c = Brand.colors
    val glow = if (c.isDark) Color.Transparent else c.accent.copy(alpha = 0.3f)
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = c.mint,
        contentColor = c.onMint,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        modifier = Modifier.size(60.dp).shadow(12.dp, CircleShape, ambientColor = glow, spotColor = glow),
    ) {
        Icon(Icons.Filled.Add, contentDescription, modifier = Modifier.size(26.dp))
    }
}

/** Full-width surface with rounded top corners and a soft upward shadow, for panels docked to the screen's bottom. */
@Composable
fun Modifier.bottomPanel(): Modifier {
    val c = Brand.colors
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val shadow = if (c.isDark) Color.Black.copy(alpha = 0.4f) else c.ink.copy(alpha = 0.14f)
    return this.shadow(16.dp, shape, ambientColor = shadow, spotColor = shadow).background(c.surface, shape)
}

/** The thin vertical rule between a field's icon and its text. */
@Composable
fun FieldDivider() {
    Box(Modifier.size(1.dp, 20.dp).background(Brand.colors.line))
}

@Composable
fun SearchField(placeholder: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Brand.colors
    val focusManager = LocalFocusManager.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .archiveField()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = c.ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        FieldDivider()
        Spacer(Modifier.width(12.dp))
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
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Cancel, null, tint = c.gray, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Surface-filled field with a hairline border. A pill by default; multi-line fields pass their own [shape]. */
@Composable
fun Modifier.archiveField(shape: Shape = CircleShape, highlighted: Boolean = false): Modifier {
    val c = Brand.colors
    return this
        .clip(shape)
        .background(c.surface)
        .border(if (highlighted) 1.5.dp else 1.dp, if (highlighted) c.accent else c.line, shape)
}

/** Tall pill with a round icon, a title, an optional subtitle and a chevron. */
@Composable
fun PillRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlightIcon: Boolean = false,
) {
    val c = Brand.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = c.surface,
        border = BorderStroke(1.dp, c.line),
        modifier = modifier.fillMaxWidth().height(72.dp),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).background(if (highlightIcon) c.mint else c.muted, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = if (highlightIcon) c.onMint else c.ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = c.ink,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (subtitle == null) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, color = c.gray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.ink, modifier = Modifier.size(20.dp))
        }
    }
}

/** Single-choice control: a muted pill track with the selected option outlined in green. */
@Composable
fun <T> SegmentedPill(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    item: @Composable (option: T, isSelected: Boolean) -> Unit,
) {
    val c = Brand.colors
    Row(
        modifier.fillMaxWidth().height(52.dp).background(c.muted, CircleShape).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .then(
                        if (isSelected) Modifier.background(c.surface).border(1.5.dp, c.accent, CircleShape) else Modifier,
                    )
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) }),
                contentAlignment = Alignment.Center,
            ) {
                item(option, isSelected)
            }
        }
    }
}

@Composable
fun PlatformBadge(platform: SocialPlatform, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = Brand.colors
    Text(
        platform.badgeTitle(context),
        color = c.ink,
        fontSize = 13.sp,
        maxLines = 1,
        modifier = modifier
            .background(c.canvas, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun PlatformPlaceholder(platform: SocialPlatform, iconSize: Dp = 26.dp, modifier: Modifier = Modifier) {
    val c = Brand.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.muted),
        contentAlignment = Alignment.Center,
    ) {
        Icon(platform.icon, null, tint = c.iconMuted, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun QuietEmpty(title: String, subtitle: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    val c = Brand.colors
    Column(modifier = modifier.fillMaxWidth()) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(c.mint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = c.onMint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
        Text(title, color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = c.gray, fontSize = 16.sp, lineHeight = 22.sp)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Brand.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = c.action,
            contentColor = c.onAction,
            disabledContainerColor = c.muted,
            disabledContentColor = c.gray,
        ),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Outlined pill. A [destructive] one has no outline and uses the danger color. */
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
        shape = CircleShape,
        border = if (destructive) null else BorderStroke(1.dp, c.line),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (destructive) Color.Transparent else c.surface,
            contentColor = tint,
        ),
        modifier = modifier.heightIn(min = 52.dp),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            if (text != null) Spacer(Modifier.width(8.dp))
        }
        if (text != null) {
            Text(
                text,
                fontSize = 14.sp,
                fontWeight = if (destructive) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Brand.colors.ink,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
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
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
