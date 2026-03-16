package com.example.gieterp

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.doOnLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

class DashboardBannerAdController(
    private val activity: Activity,
    private val adContainer: FrameLayout,
) {
    private var adView: AdView? = null
    private var isLoading = false
    private var hasLoaded = false

    fun loadBannerIfNeeded() {
        if (hasLoaded || isLoading) {
            return
        }

        adContainer.doOnLayout { view ->
            val widthPixels = view.width.takeIf { it > 0 } ?: return@doOnLayout
            val widthDp = (widthPixels / view.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            loadBanner(widthDp)
        }
    }

    fun showContainer(visible: Boolean) {
        adContainer.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun onResume() {
        adView?.resume()
    }

    fun onPause() {
        adView?.pause()
    }

    fun onDestroy() {
        adView?.destroy()
        adView = null
        hasLoaded = false
        isLoading = false
    }

    private fun loadBanner(adWidthDp: Int) {
        if (hasLoaded || isLoading) {
            return
        }

        isLoading = true
        val banner = AdView(activity).apply {
            adUnitId = activity.getString(R.string.admob_banner_ad_unit_id)
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidthDp))
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    this@DashboardBannerAdController.isLoading = false
                    this@DashboardBannerAdController.hasLoaded = true
                    showContainer(true)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    this@DashboardBannerAdController.isLoading = false
                    this@DashboardBannerAdController.hasLoaded = false
                    adContainer.removeAllViews()
                    showContainer(false)
                }
            }
        }

        adContainer.removeAllViews()
        adContainer.addView(banner)
        adView = banner
        showContainer(false)
        banner.loadAd(AdRequest.Builder().build())
    }
}
