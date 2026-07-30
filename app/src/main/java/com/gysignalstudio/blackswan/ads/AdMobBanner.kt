package com.gysignalstudio.blackswan.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.gysignalstudio.blackswan.BuildConfig

private val bannerAdUnitId: String
    get() = if (BuildConfig.DEBUG) {
        "ca-app-pub-3940256099942544/9214589741"
    } else {
        BuildConfig.ADMOB_BANNER_ID
    }

@Composable
fun AdMobBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val adView = remember(context, widthDp) {
        AdView(context).apply {
            adUnitId = bannerAdUnitId
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }

    AndroidView(factory = { adView }, modifier = modifier)
}
