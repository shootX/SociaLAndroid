package com.ich.socialsave.share

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassifier
import com.ich.socialsave.R
import com.ich.socialsave.container
import com.ich.socialsave.core.UrlNormalizer

/**
 * The library's "Save the copied link?" offer. Android 12+ shows "SocialSave pasted from your clipboard" each time an
 * app reads it, so the text is read only when the person taps Save; until then only the clip's description is used.
 * A clip is offered once: it is remembered by its copy time after Save, dismiss, or a copy made by SocialSave itself.
 */
object CopiedLink {
    /** The copy time of a clip that likely holds a link and wasn't offered yet, or null. Works only while the app has focus. */
    fun pending(context: Context): Long? {
        val description = clipboard(context)?.primaryClipDescription ?: return null
        val stamp = description.timestamp
        if (stamp == context.container.settings.handledClip || !description.hasMimeType("text/*")) return null
        val likelyLink = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Not classified (yet): offer it; Save then says when there was no link.
            description.classificationStatus != ClipDescription.CLASSIFICATION_COMPLETE ||
                description.getConfidenceScore(TextClassifier.TYPE_URL) >= 0.5f
        } else {
            UrlNormalizer.firstHttpUrl(text(context).orEmpty()) != null // reading is silent before Android 12
        }
        return stamp.takeIf { likelyLink }
    }

    fun save(context: Context, stamp: Long) {
        handled(context, stamp)
        val link = UrlNormalizer.firstHttpUrl(text(context).orEmpty())
        if (link == null) toast(context, context.getString(R.string.intent_save_copied_no_link)) else saveLinkInBackground(context, link)
    }

    fun handled(context: Context, stamp: Long) {
        context.container.settings.handledClip = stamp
    }

    /** After SocialSave copies or pastes a link itself: don't offer that clip back. */
    fun markCurrentHandled(context: Context) {
        clipboard(context)?.primaryClipDescription?.timestamp?.let { handled(context, it) }
    }

    private fun clipboard(context: Context) = context.getSystemService(ClipboardManager::class.java)

    private fun text(context: Context): String? =
        clipboard(context)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}
