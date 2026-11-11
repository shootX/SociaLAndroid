package com.ich.socialsave.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.ich.socialsave.R
import com.ich.socialsave.data.Category
import com.ich.socialsave.ui.MainActivity

/**
 * The categories as sharing shortcuts, so the share sheet can offer one and the link is saved straight into it
 * (`res/xml/shortcuts.xml` routes them to [ShareReceiverActivity]).
 */
object ShareShortcuts {
    // shortcut: Android 10+ only; older phones would need the androidx.sharetarget library and just show the app.
    private const val SHARE_CATEGORY = "com.ich.socialsave.category.SAVE_TO_CATEGORY"
    private const val PREFIX = "category-"
    private const val INK = 0xFF141414.toInt()
    private const val MINT = 0xFF52ECB8.toInt()

    fun categoryId(shortcutId: String?): String? = shortcutId?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)

    /** Replaces the shortcuts with [categories], in their order. */
    fun update(context: Context, categories: List<Category>) {
        val shortcuts = categories.take(ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)).map { category ->
            ShortcutInfoCompat.Builder(context, PREFIX + category.id)
                .setShortLabel(category.name)
                .setIcon(icon(context, category.colorHex))
                .setCategories(setOf(SHARE_CATEGORY))
                // Required by Android; Android 13+ keeps these out of the launcher's long-press menu, older ones open the app.
                .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW))
                .setExcludedFromSurfaces(ShortcutInfoCompat.SURFACE_LAUNCHER)
                .build()
        }
        // Rate-limited in the background; the next change in the foreground catches up.
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
            .onFailure { Log.w("ShareShortcuts", "Couldn't update share shortcuts", it) }
    }

    /** The app's mark with the category's color as a dot, drawn inside the adaptive icon's safe zone. */
    private fun icon(context: Context, colorHex: String): IconCompat {
        val size = 216
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(INK)
        ContextCompat.getDrawable(context, R.drawable.ic_notification)?.mutate()?.apply {
            setTint(MINT)
            setBounds(size * 3 / 8, size * 3 / 8, size * 5 / 8, size * 5 / 8)
            draw(canvas)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        canvas.drawCircle(size * 0.64f, size * 0.64f, size * 0.09f, paint)
        paint.color = runCatching { Color.parseColor(colorHex) }.getOrDefault(MINT)
        canvas.drawCircle(size * 0.64f, size * 0.64f, size * 0.07f, paint)
        return IconCompat.createWithAdaptiveBitmap(bitmap)
    }
}
