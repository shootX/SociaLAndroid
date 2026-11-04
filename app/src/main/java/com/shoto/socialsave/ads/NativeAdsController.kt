package com.shoto.socialsave.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.shoto.socialsave.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns consent (Google UMP, required for users in the EEA/UK) and a small pool of native ads that the
 * library grids place between posts. Ads are only requested after consent allows it.
 */
class NativeAdsController(context: Context) {
    private val appContext = context.applicationContext
    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(appContext)
    private val sdkStarted = AtomicBoolean(false)
    private var loading = false
    private var loadedAt = 0L

    private val _ads = MutableStateFlow<List<NativeAd>>(emptyList())
    /** Loaded ads in display order: the grid shows ads[0] after the 10th post, ads[1] after the 20th… */
    val ads: StateFlow<List<NativeAd>> = _ads.asStateFlow()

    private val _ready = MutableStateFlow(false)
    /** True once consent allows ads and the SDK is initialized. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    /** Call from the main activity's onCreate. Shows the consent form when the user still has to answer it. */
    fun gatherConsent(activity: Activity) {
        val params = ConsentRequestParameters.Builder().build()
        consent.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) Log.w(TAG, "Consent form: ${error.message}")
                    updatePrivacyState()
                    startIfAllowed()
                }
            },
            { error ->
                Log.w(TAG, "Consent info: ${error.message}")
                startIfAllowed()
            },
        )
        // Consent from a previous session lets ads start without waiting for the network round trip.
        startIfAllowed()
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "Privacy options: ${error.message}")
            updatePrivacyState()
        }
    }

    /** Makes sure at least [count] ads are loaded (max 5 per request, the SDK's limit). */
    fun ensureLoaded(count: Int) {
        if (count <= 0 || !_ready.value || !consent.canRequestAds()) return
        val stale = SystemClock.elapsedRealtime() - loadedAt > AD_LIFETIME_MS
        if (stale && _ads.value.isNotEmpty()) clear()
        val wanted = count.coerceAtMost(MAX_ADS)
        if (loading || _ads.value.size >= wanted) return
        loading = true

        val batch = mutableListOf<NativeAd>()
        lateinit var loader: AdLoader
        loader = AdLoader.Builder(appContext, BuildConfig.ADMOB_NATIVE_ID)
            .forNativeAd { ad ->
                batch += ad
                if (!loader.isLoading) finishBatch(batch)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.i(TAG, "Native ad failed: ${error.message}")
                    if (!loader.isLoading) finishBatch(batch)
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_SQUARE)
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build(),
            )
            .build()
        loader.loadAds(AdRequest.Builder().build(), (wanted - _ads.value.size).coerceIn(1, 5))
    }

    private fun finishBatch(batch: MutableList<NativeAd>) {
        if (!loading) return
        loading = false
        if (batch.isNotEmpty()) {
            if (_ads.value.isEmpty()) loadedAt = SystemClock.elapsedRealtime()
            _ads.value = (_ads.value + batch).take(MAX_ADS)
            batch.clear()
        }
    }

    private fun clear() {
        _ads.value.forEach { it.destroy() }
        _ads.value = emptyList()
    }

    private fun updatePrivacyState() {
        _privacyOptionsRequired.value =
            consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun startIfAllowed() {
        if (!consent.canRequestAds() || !sdkStarted.compareAndSet(false, true)) return
        MobileAds.initialize(appContext) { _ready.value = true }
    }

    companion object {
        private const val TAG = "SocialSaveAds"
        /** One native ad after every this many posts. */
        const val POSTS_PER_AD = 10
        private const val MAX_ADS = 5
        private const val AD_LIFETIME_MS = 60L * 60 * 1000
    }
}
