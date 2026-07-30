package com.gysignalstudio.blackswan.ads

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.gysignalstudio.blackswan.BuildConfig
import com.gysignalstudio.blackswan.engagement.EngagementPromptManager
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private val interstitialAdUnitId: String
    get() = if (BuildConfig.DEBUG) {
        "ca-app-pub-3940256099942544/1033173712"
    } else {
        BuildConfig.ADMOB_INTERSTITIAL_ID
    }

object AdMobManager {
    private val initialized = AtomicBoolean(false)
    private val _adsReady = MutableStateFlow(false)
    private var interstitialAd: InterstitialAd? = null
    private var interstitialLoading = false
    private var adsDisabled = false
    private var filterChangeCount = 0
    private var detailViewCount = 0
    val adsReady = _adsReady.asStateFlow()

    fun setAdsDisabled(disabled: Boolean) {
        adsDisabled = disabled
        if (disabled) {
            interstitialAd = null
            interstitialLoading = false
            _adsReady.value = false
        }
    }

    fun requestConsentAndInitialize(activity: Activity) {
        if (adsDisabled) return
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val request = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            request,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    initializeIfAllowed(activity, consentInformation)
                }
            },
            {
                initializeIfAllowed(activity, consentInformation)
            }
        )
    }

    fun showPrivacyOptions(activity: Activity, onComplete: (Boolean, String?) -> Unit) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        if (consentInformation.privacyOptionsRequirementStatus !=
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        ) {
            onComplete(false, null)
            return
        }
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            initializeIfAllowed(activity, consentInformation)
            onComplete(true, error?.message)
        }
    }

    fun recordFilterChange(activity: Activity) {
        if (adsDisabled) return
        filterChangeCount += 1
        if (filterChangeCount % 3 == 0) {
            showInterstitialIfReady(activity)
        } else {
            preloadInterstitial(activity)
        }
    }

    fun recordDetailView(activity: Activity) {
        if (adsDisabled) return
        detailViewCount += 1
        if (detailViewCount % 5 == 0) {
            showInterstitialIfReady(activity)
        } else {
            preloadInterstitial(activity)
        }
    }

    private fun initializeIfAllowed(activity: Activity, consentInformation: ConsentInformation) {
        if (adsDisabled) return
        if (!consentInformation.canRequestAds()) return
        if (initialized.compareAndSet(false, true)) {
            Thread {
                MobileAds.initialize(activity.applicationContext) {}
                _adsReady.value = true
                activity.runOnUiThread { preloadInterstitial(activity) }
            }.start()
        } else {
            _adsReady.value = true
            preloadInterstitial(activity)
        }
    }

    private fun preloadInterstitial(activity: Activity) {
        if (adsDisabled) return
        if (!_adsReady.value || interstitialAd != null || interstitialLoading) return
        interstitialLoading = true
        InterstitialAd.load(
            activity,
            interstitialAdUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    interstitialLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    interstitialLoading = false
                }
            }
        )
    }

    private fun showInterstitialIfReady(activity: Activity) {
        if (adsDisabled) return
        if (!_adsReady.value) return
        val ad = interstitialAd
        if (ad == null) {
            preloadInterstitial(activity)
            return
        }
        interstitialAd = null
        var adClicked = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdClicked() {
                adClicked = true
            }

            override fun onAdDismissedFullScreenContent() {
                if (!adClicked) {
                    EngagementPromptManager.recordInterstitialDismiss()
                }
                preloadInterstitial(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                preloadInterstitial(activity)
            }
        }
        ad.show(activity)
    }
}
