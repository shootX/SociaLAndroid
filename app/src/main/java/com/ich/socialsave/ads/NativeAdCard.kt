package com.ich.socialsave.ads

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.ich.socialsave.R
import com.ich.socialsave.ui.theme.BrandColors
import com.ich.socialsave.ui.theme.LocalBrand

/**
 * A native ad drawn like a post card: square media with an "Ad" badge, headline, advertiser and a call to action.
 * The SDK requires real Android views registered on a [NativeAdView], so this is a small view tree inside Compose.
 */
@Composable
fun NativeAdCard(ad: NativeAd, modifier: Modifier = Modifier) {
    val brand = LocalBrand.current
    val badge = stringResource(R.string.ad_label)
    val fallbackCta = stringResource(R.string.ad_open)
    AndroidView(
        modifier = modifier,
        factory = { context -> AdCardViews(context).root },
        update = { view -> (view.tag as AdCardViews).bind(ad, brand, badge, fallbackCta) },
    )
}

private class AdCardViews(context: Context) {
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()

    val root = NativeAdView(context)
    private val media = MediaView(context).apply { setImageScaleType(ImageView.ScaleType.CENTER_CROP) }
    private val icon = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val badge = TextView(context)
    private val headline = TextView(context)
    private val advertiser = TextView(context)
    private val cta = TextView(context)
    private val mediaFrame = SquareFrameLayout(context)
    private val column = LinearLayout(context)

    init {
        root.tag = this
        mediaFrame.clipToOutline = true
        mediaFrame.addView(media, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        mediaFrame.addView(
            icon,
            FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER),
        )
        mediaFrame.addView(
            badge,
            FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
                setMargins(dp(8), dp(8), 0, 0)
            },
        )

        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        badge.setPadding(dp(10), dp(4), dp(10), dp(4))

        headline.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        headline.typeface = Typeface.DEFAULT_BOLD
        headline.maxLines = 2
        headline.ellipsize = TextUtils.TruncateAt.END

        advertiser.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        advertiser.maxLines = 1
        advertiser.ellipsize = TextUtils.TruncateAt.END

        cta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        cta.typeface = Typeface.DEFAULT_BOLD
        cta.gravity = Gravity.CENTER
        cta.maxLines = 1
        cta.setPadding(dp(12), dp(7), dp(12), dp(7))

        column.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(12))
            fun textParams(top: Int) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(top)
                marginStart = dp(4)
                marginEnd = dp(4)
            }
            addView(mediaFrame, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(headline, textParams(8))
            addView(advertiser, textParams(4))
            addView(
                cta,
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    topMargin = dp(8)
                    marginStart = dp(4)
                },
            )
        }
        root.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        root.mediaView = media
        root.iconView = icon
        root.headlineView = headline
        root.advertiserView = advertiser
        root.callToActionView = cta
    }

    fun bind(ad: NativeAd, brand: BrandColors, badgeText: String, fallbackCta: String) {
        column.background = GradientDrawable().apply {
            cornerRadius = 20 * density
            setColor(brand.surface.toArgb())
            setStroke(dp(1), brand.line.toArgb())
        }
        mediaFrame.background = GradientDrawable().apply {
            cornerRadius = 14 * density
            setColor(brand.muted.toArgb())
        }
        badge.text = badgeText
        badge.setTextColor(brand.ink.toArgb())
        badge.background = GradientDrawable().apply {
            cornerRadius = 12 * density
            setColor(brand.canvas.toArgb())
        }
        headline.setTextColor(brand.ink.toArgb())
        advertiser.setTextColor(brand.gray.toArgb())
        cta.setTextColor(brand.onAction.toArgb())
        cta.background = GradientDrawable().apply {
            cornerRadius = 100 * density
            setColor(brand.action.toArgb())
        }

        headline.text = ad.headline
        advertiser.text = ad.advertiser ?: ad.store ?: ad.body
        advertiser.visibility = if (advertiser.text.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        cta.text = ad.callToAction ?: fallbackCta

        val content = ad.mediaContent
        val hasMedia = content != null
        media.visibility = if (hasMedia) android.view.View.VISIBLE else android.view.View.GONE
        if (content != null) media.mediaContent = content
        val iconDrawable = ad.icon?.drawable
        icon.setImageDrawable(iconDrawable)
        icon.visibility = if (!hasMedia && iconDrawable != null) android.view.View.VISIBLE else android.view.View.GONE

        root.setNativeAd(ad)
    }
}

/** Keeps the media area square, matching the post thumbnails next to it. */
private class SquareFrameLayout(context: Context) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}
