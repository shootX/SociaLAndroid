package com.ich.socialsave.ads

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.ich.socialsave.BuildConfig
import com.ich.socialsave.container

/**
 * The activity's one anchored adaptive banner, shown above the tab bar. It outlives the tab bar, so opening a post
 * or selecting posts (both hide the tab bar) and coming back shows the same ad instead of requesting a new one.
 */
@Stable
class BannerAdState internal constructor(private val context: Context) {
    internal class Banner(val view: AdView, val size: AdSize)

    private var banner: Banner? = null

    /** Null while the first ad loads (its space is kept), true once one shows, false if that load failed. */
    var loaded by mutableStateOf<Boolean?>(null)
        private set

    /**
     * Created and loaded on first display, sized to the width it gets there. That width doesn't change later:
     * rotation and window resizes recreate the activity, and this state with it.
     */
    internal fun banner(widthDp: Int): Banner = banner ?: run {
        val size = AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp)
        val view = AdView(context).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            setAdSize(size)
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    loaded = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.i(TAG, "Banner ad failed: ${error.message}")
                    // A failed refresh keeps the last ad on screen; only a failed first load gives the space back.
                    if (loaded == null) loaded = false
                }
            }
            loadAd(AdRequest.Builder().build())
        }
        Banner(view, size).also { banner = it }
    }

    internal fun resume() {
        val view = banner?.view ?: return
        view.resume()
        // After a failed load, try again when the user comes back to the app.
        if (loaded == false) {
            loaded = null
            view.loadAd(AdRequest.Builder().build())
        }
    }

    internal fun pause() {
        banner?.view?.pause()
    }

    internal fun destroy() {
        banner?.view?.destroy()
        banner = null
    }

    private companion object {
        const val TAG = "SocialSaveAds"
    }
}

/** Null until consent allows ads and the Mobile Ads SDK has started (see [NativeAdsController.ready]). */
@Composable
fun rememberBannerAdState(): BannerAdState? {
    val context = LocalContext.current
    val ready by context.container.ads.ready.collectAsStateWithLifecycle()
    if (!ready) return null
    val state = remember(context) { BannerAdState(context) }
    LifecycleResumeEffect(state) {
        state.resume()
        onPauseOrDispose { state.pause() }
    }
    DisposableEffect(state) {
        onDispose { state.destroy() }
    }
    return state
}

/**
 * The banner, centered, with a gap before whatever sits below it. It holds its height while the ad loads so the
 * screen doesn't jump, and takes no space at all when no ad could be loaded.
 */
@Composable
fun BannerAd(state: BannerAdState, modifier: Modifier = Modifier) {
    if (state.loaded == false) return
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val banner = state.banner(maxWidth.value.toInt())
        AndroidView(
            // The same view returns each time the tab bar does; detach it from the previous holder first.
            factory = { banner.view.also { (it.parent as? ViewGroup)?.removeView(it) } },
            modifier = Modifier.size(banner.size.width.dp, banner.size.height.dp),
        )
    }
}
