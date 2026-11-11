package com.ich.socialsave.share

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.pm.ShortcutManagerCompat
import com.ich.socialsave.R
import com.ich.socialsave.container
import com.ich.socialsave.core.UrlNormalizer
import com.ich.socialsave.data.AppLocale
import com.ich.socialsave.data.SaveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Share → SocialSave", or one of the category shortcuts in the share sheet ([ShareShortcuts]): saves the link in
 * the background, says how it went in a toast and closes at once, so the person stays in the app they shared from.
 */
class ShareReceiverActivity : ComponentActivity() {
    // Activities don't inherit the Application's context, so the in-app language must be applied here too.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val link = sharedLink(intent)
            if (link == null) {
                toast(this, getString(R.string.share_failed))
            } else {
                saveLinkInBackground(this, link, ShareShortcuts.categoryId(intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)))
            }
        }
        finish()
    }

    private fun sharedLink(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val candidates = listOfNotNull(
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            intent.getStringExtra(Intent.EXTRA_SUBJECT),
            intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString(),
        )
        return candidates.firstNotNullOfOrNull { UrlNormalizer.firstHttpUrl(it) }
    }
}

/**
 * Saves [link] in the app scope, so it finishes after the caller is gone (a short link can take seconds to
 * expand), then reports the result in a toast. Used by Share and by the library's copied-link offer.
 */
fun saveLinkInBackground(context: Context, link: String, categoryId: String? = null) {
    val app = context.applicationContext
    val container = app.container
    container.scope.launch {
        val message = when (container.repository.saveLink(link, categoryId)) {
            is SaveResult.Saved -> {
                container.sync.requestSync()
                val category = categoryId?.let { container.database.categories().get(it)?.name }
                if (category != null) app.getString(R.string.share_saved_to, category) else app.getString(R.string.intent_save_copied_saved)
            }
            SaveResult.AlreadyExists -> app.getString(R.string.intent_save_copied_already)
            SaveResult.InvalidUrl -> app.getString(R.string.share_failed)
            SaveResult.Failure -> app.getString(R.string.intent_save_copied_failed)
        }
        withContext(Dispatchers.Main) { toast(app, message) }
    }
}

fun toast(context: Context, message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
