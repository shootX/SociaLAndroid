package com.shoto.socialsave.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shoto.socialsave.R
import com.shoto.socialsave.container
import com.shoto.socialsave.core.UrlNormalizer
import com.shoto.socialsave.data.SaveResult
import com.shoto.socialsave.ui.components.PrimaryButton
import com.shoto.socialsave.ui.theme.Brand
import com.shoto.socialsave.ui.theme.SocialSaveTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

/** Receives "Share → SocialSave" from other apps, saves the link and closes itself. */
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = this.container
        val link = sharedLink(intent)
        // Saving runs in the app scope so it finishes even if the sheet is dismissed early.
        val pending = link?.let { container.scope.async { container.repository.saveLink(it) } }

        setContent {
            val appearance by container.settings.appearance.collectAsStateWithLifecycle()
            SocialSaveTheme(appearance) {
                var result by remember { mutableStateOf<SaveResult?>(if (link == null) SaveResult.InvalidUrl else null) }
                LaunchedEffect(Unit) {
                    if (pending != null) result = pending.await()
                }
                LaunchedEffect(result) {
                    if (result is SaveResult.Saved || result == SaveResult.AlreadyExists) {
                        delay(650)
                        finish()
                    }
                }
                ShareSheet(result, onClose = { finish() })
            }
        }
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

@Composable
private fun ShareSheet(result: SaveResult?, onClose: () -> Unit) {
    val c = Brand.colors
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(16.dp).widthIn(max = 420.dp).fillMaxWidth()
                .background(c.canvas, RoundedCornerShape(24.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(52.dp).background(c.surface, CircleShape), contentAlignment = Alignment.Center) {
                when (result) {
                    null -> CircularProgressIndicator(color = c.ink, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                    is SaveResult.Saved -> Icon(Icons.Filled.Check, null, tint = c.ink)
                    SaveResult.AlreadyExists -> Icon(Icons.Filled.Bookmark, null, tint = c.ink)
                    else -> Icon(Icons.Filled.ErrorOutline, null, tint = c.danger)
                }
            }
            val title = when (result) {
                null -> stringResource(R.string.share_saving)
                is SaveResult.Saved -> stringResource(R.string.share_saved)
                SaveResult.AlreadyExists -> stringResource(R.string.share_already)
                else -> stringResource(R.string.share_failed)
            }
            Text(title, color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (result == SaveResult.InvalidUrl || result == SaveResult.Failure) {
                PrimaryButton(stringResource(R.string.share_close), onClick = onClose)
            }
        }
    }
}
