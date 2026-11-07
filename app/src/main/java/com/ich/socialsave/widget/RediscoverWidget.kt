package com.ich.socialsave.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ich.socialsave.R
import com.ich.socialsave.container
import com.ich.socialsave.data.SavedPost
import com.ich.socialsave.ui.MainActivity
import com.ich.socialsave.util.RelativeTime
import com.ich.socialsave.util.displayTitle
import com.ich.socialsave.util.label
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Shows one saved post at a time, rotating through favorites and recent saves every few hours. */
class RediscoverWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val posts = context.container.repository.allPosts()
        val picked = pick(posts)
        val item = if (picked.isEmpty()) {
            null
        } else {
            picked[((System.currentTimeMillis() / ROTATION_MILLIS) % picked.size).toInt()]
        }
        val bitmap = item?.let { loadBitmap(context, it) }
        val title = item?.displayTitle(context)
        val platform = item?.socialPlatform?.label(context)
        val time = item?.let { RelativeTime.compact(context, it.createdAt) }
        val count = context.getString(R.string.widget_count, posts.size)

        provideContent {
            GlanceTheme {
                if (item == null) {
                    EmptyContent(context)
                } else {
                    val open = actionStartActivity<MainActivity>(
                        actionParametersOf(MainActivity.postIdParameter to item.id),
                    )
                    val modifier = GlanceModifier.fillMaxSize().clickable(open)
                    if (LocalSize.current.width >= MEDIUM.width) {
                        MediumContent(modifier, bitmap, title!!, platform!!, time!!, count)
                    } else {
                        SmallContent(modifier, bitmap, title!!, platform!!)
                    }
                }
            }
        }
    }

    @Composable
    private fun SmallContent(modifier: GlanceModifier, bitmap: Bitmap?, title: String, platform: String) {
        Box(modifier = modifier.background(ImageProvider(R.drawable.widget_background))) {
            if (bitmap != null) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize(),
                )
            }
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxWidth()
                        .background(ImageProvider(R.drawable.widget_scrim))
                        .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 12.dp),
                ) {
                    Text(platform, style = TextStyle(color = WHITE_SOFT, fontSize = 10.sp, fontWeight = FontWeight.Medium))
                    Text(
                        title,
                        maxLines = 2,
                        style = TextStyle(color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }

    @Composable
    private fun MediumContent(
        modifier: GlanceModifier,
        bitmap: Bitmap?,
        title: String,
        platform: String,
        time: String,
        count: String,
    ) {
        Row(
            modifier = modifier.background(ImageProvider(R.drawable.widget_background)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val imageModifier = GlanceModifier.width(120.dp).fillMaxHeight().cornerRadius(14.dp)
            if (bitmap != null) {
                Image(ImageProvider(bitmap), null, contentScale = ContentScale.Crop, modifier = imageModifier)
            } else {
                Box(
                    modifier = imageModifier.background(ImageProvider(R.drawable.widget_placeholder)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(ImageProvider(R.drawable.ic_bookmark), null, modifier = GlanceModifier.width(28.dp).height(28.dp))
                }
            }
            Spacer(GlanceModifier.width(14.dp))
            Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                Text(platform, style = TextStyle(color = gray(), fontSize = 11.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.height(4.dp))
                Text(title, maxLines = 3, style = TextStyle(color = ink(), fontSize = 15.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.defaultWeight())
                Text(time, maxLines = 1, style = TextStyle(color = gray(), fontSize = 11.sp))
                Text(count, maxLines = 1, style = TextStyle(color = gray(), fontSize = 11.sp, fontWeight = FontWeight.Medium))
            }
        }
    }

    @Composable
    private fun EmptyContent(context: Context) {
        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(ImageProvider(R.drawable.widget_background))
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Image(ImageProvider(R.drawable.ic_bookmark), null, modifier = GlanceModifier.width(22.dp).height(22.dp))
            Spacer(GlanceModifier.defaultWeight())
            Text(
                context.getString(R.string.widget_empty_title),
                style = TextStyle(color = ink(), fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                context.getString(R.string.widget_empty_body),
                maxLines = 3,
                style = TextStyle(color = gray(), fontSize = 11.sp),
            )
        }
    }

    private fun ink() = ColorProvider(R.color.ink)
    private fun gray() = ColorProvider(R.color.gray)

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val MEDIUM = DpSize(250.dp, 110.dp)
        private const val ROTATION_MILLIS = 3L * 60 * 60 * 1000
        private const val MAX_ITEMS = 12
        private val WHITE = ColorProvider(Color.White)
        private val WHITE_SOFT = ColorProvider(Color.White.copy(alpha = 0.85f))

        /** Favorites first, then posts with pictures, then the rest, so the widget usually has an image. */
        fun pick(posts: List<SavedPost>): List<SavedPost> {
            val favorites = posts.filter { it.isFavorite }
            val withImages = posts.filter { !it.isFavorite && it.thumbnailFile != null }
            val rest = posts.filter { !it.isFavorite && it.thumbnailFile == null }
            return (favorites + withImages + rest).take(MAX_ITEMS)
        }

        private fun loadBitmap(context: Context, post: SavedPost): Bitmap? {
            val file = context.container.thumbnails.file(post.thumbnailFile) ?: return null
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            return runCatching { BitmapFactory.decodeFile(file.path, options) }.getOrNull()
        }
    }
}

class RediscoverWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RediscoverWidget()
}

object WidgetUpdater {
    private var pending: Job? = null

    /** Coalesces bursts of library changes into one widget refresh. */
    fun requestUpdate(context: Context, scope: CoroutineScope) {
        pending?.cancel()
        pending = scope.launch {
            delay(500)
            update(context)
        }
    }

    suspend fun update(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        if (manager.getGlanceIds(RediscoverWidget::class.java).isEmpty()) return
        runCatching { RediscoverWidget().updateAll(context) }
    }
}
