package com.maswadkar.developers.androidify.ui.components

import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.maswadkar.developers.androidify.R

@Composable
fun NativeAdCard(
    modifier: Modifier = Modifier,
    adUnitId: String
) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var adError by remember { mutableStateOf<String?>(null) }

    // Load Ad
    LaunchedEffect(adUnitId) {
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                // If this callback occurs after the activity is destroyed, you must call
                // destroy and return or you may get a memory leak.
                Log.d("AdMob", "Native Ad Loaded")
                nativeAd?.destroy()
                nativeAd = ad
                adError = null
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("AdMob", "Ad Failed to Load: ${error.code} - ${error.message}")
                    adError = "Ad failed to load: ${error.message}"
                }

                override fun onAdOpened() {
                    Log.d("AdMob", "Ad Opened")
                }
            })
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    // Clean up
    DisposableEffect(Unit) {
        onDispose {
            nativeAd?.destroy()
        }
    }

    if (nativeAd != null) {
        AndroidView(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            factory = { ctx ->
                val adView = LayoutInflater.from(ctx)
                    .inflate(R.layout.native_ad, null) as NativeAdView
                populateNativeAdView(nativeAd!!, adView)
                adView
            },
            update = { adView ->
                // Update the view if ad changes
                if (nativeAd != null) {
                    populateNativeAdView(nativeAd!!, adView)
                }
            }
        )
    } else {
        // Optional: Placeholder for debugging
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = adError ?: "Loading Ad...")
        }
    }
}

private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
    // Set the media view.
    // adView.mediaView = adView.findViewById(R.id.ad_media)

    // Set other ad assets.
    adView.headlineView = adView.findViewById(R.id.ad_headline)
    adView.bodyView = adView.findViewById(R.id.ad_body)
    adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
    adView.iconView = adView.findViewById(R.id.ad_app_icon)
    adView.advertiserView = adView.findViewById(R.id.ad_advertiser)

    // The headline and mediaContent are guaranteed to be in every UnifiedNativeAd.
    (adView.headlineView as TextView).text = nativeAd.headline
    // adView.mediaView.mediaContent = nativeAd.mediaContent

    // These assets aren't guaranteed to be in every UnifiedNativeAd, so it's important to
    // check before trying to display them.
    if (nativeAd.body == null) {
        adView.bodyView?.visibility = android.view.View.INVISIBLE
    } else {
        adView.bodyView?.visibility = android.view.View.VISIBLE
        (adView.bodyView as TextView).text = nativeAd.body
    }

    if (nativeAd.callToAction == null) {
        adView.callToActionView?.visibility = android.view.View.INVISIBLE
    } else {
        adView.callToActionView?.visibility = android.view.View.VISIBLE
        (adView.callToActionView as Button).text = nativeAd.callToAction
    }

    if (nativeAd.icon == null) {
        adView.iconView?.visibility = android.view.View.GONE
    } else {
        (adView.iconView as ImageView).setImageDrawable(
            nativeAd.icon?.drawable
        )
        adView.iconView?.visibility = android.view.View.VISIBLE
    }

    if (nativeAd.advertiser == null) {
        adView.advertiserView?.visibility = android.view.View.INVISIBLE
    } else {
        (adView.advertiserView as TextView).text = nativeAd.advertiser
        adView.advertiserView?.visibility = android.view.View.VISIBLE
    }

    // This method tells the Google Mobile Ads SDK that you have finished populating your
    // native ad view with this native ad.
    adView.setNativeAd(nativeAd)
}
