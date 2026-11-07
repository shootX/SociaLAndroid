package com.ich.socialsave.util

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.ich.socialsave.R
import com.ich.socialsave.core.SocialPlatform
import com.ich.socialsave.core.TitleSanitizer
import com.ich.socialsave.data.SavedPost

fun SocialPlatform.label(context: Context): String =
    if (this == SocialPlatform.OTHER) context.getString(R.string.platform_other) else displayName

fun SocialPlatform.badgeTitle(context: Context): String =
    if (this == SocialPlatform.OTHER) context.getString(R.string.platform_web) else displayName

fun SocialPlatform.fallbackTitle(context: Context): String = context.getString(
    when (this) {
        SocialPlatform.INSTAGRAM -> R.string.platform_fallback_instagram
        SocialPlatform.TIKTOK -> R.string.platform_fallback_tiktok
        SocialPlatform.FACEBOOK -> R.string.platform_fallback_facebook
        SocialPlatform.YOUTUBE -> R.string.platform_fallback_youtube
        SocialPlatform.X -> R.string.platform_fallback_x
        SocialPlatform.THREADS -> R.string.platform_fallback_threads
        SocialPlatform.PINTEREST -> R.string.platform_fallback_pinterest
        SocialPlatform.REDDIT -> R.string.platform_fallback_reddit
        SocialPlatform.OTHER -> R.string.platform_fallback_other
    },
)

val SocialPlatform.icon: ImageVector
    get() = when (this) {
        SocialPlatform.INSTAGRAM -> Icons.Filled.Photo
        SocialPlatform.TIKTOK, SocialPlatform.YOUTUBE -> Icons.Filled.Videocam
        SocialPlatform.FACEBOOK, SocialPlatform.THREADS -> Icons.Filled.SmartDisplay
        SocialPlatform.X, SocialPlatform.REDDIT -> Icons.Filled.ChatBubble
        SocialPlatform.PINTEREST -> Icons.Filled.PushPin
        SocialPlatform.OTHER -> Icons.Filled.Link
    }

fun SavedPost.displayTitle(context: Context): String =
    TitleSanitizer.cleaned(title) ?: socialPlatform.fallbackTitle(context)

@Composable
fun SavedPost.displayTitle(): String = displayTitle(LocalContext.current)

@Composable
fun SocialPlatform.label(): String = label(LocalContext.current)

/** Plain-text list of titles and links for the share sheet. */
fun shareText(context: Context, posts: List<SavedPost>, title: String? = null): String = buildString {
    if (!title.isNullOrEmpty()) append(title).append("\n\n")
    posts.forEach { append(it.displayTitle(context)).append('\n').append(it.url).append("\n\n") }
}.trim()
