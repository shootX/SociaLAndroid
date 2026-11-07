package com.shoto.socialsave.share

import android.app.PendingIntent
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.shoto.socialsave.R
import com.shoto.socialsave.container
import com.shoto.socialsave.core.UrlNormalizer
import com.shoto.socialsave.data.SaveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Invisible activity behind the "Save Copied Link" launcher shortcut and Quick Settings tile.
 * Android only lets the focused app read the clipboard, so it reads once it gains window focus,
 * saves in the background and closes straight away.
 */
class ClipboardSaveActivity : ComponentActivity() {
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handled = savedInstanceState?.getBoolean(KEY_HANDLED) ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_HANDLED, handled)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || handled) return
        handled = true

        val clip = getSystemService(ClipboardManager::class.java)?.primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        val link = UrlNormalizer.firstHttpUrl(text)
        if (link == null) {
            toast(R.string.intent_save_copied_no_link)
            finish()
            return
        }

        val app = applicationContext
        app.container.scope.launch {
            val message = when (app.container.repository.saveLink(link)) {
                is SaveResult.Saved -> {
                    app.container.sync.requestSync()
                    R.string.intent_save_copied_saved
                }
                SaveResult.AlreadyExists -> R.string.intent_save_copied_already
                SaveResult.InvalidUrl -> R.string.intent_save_copied_no_link
                SaveResult.Failure -> R.string.intent_save_copied_failed
            }
            withContext(Dispatchers.Main) { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
        }
        finish()
    }

    private fun toast(message: Int) = Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()

    private companion object {
        const val KEY_HANDLED = "handled"
    }
}

/** Quick Settings tile: copy a link anywhere, pull down the shade, tap the tile. */
class SaveLinkTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, ClipboardSaveActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
