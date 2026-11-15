package com.ultramusicplayer.app.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.ultramusicplayer.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdMob integration: consent (UMP) → SDK init → adaptive banner + frequency-capped interstitial.
 * Debug builds use Google's test ad units so testing never generates invalid traffic on the real
 * account; release builds use the real units (see app/build.gradle.kts).
 */
object Ads {
    private const val TAG = "Ads"

    /** Interstitial at most every 4th trigger, never more than once per 3 minutes, not in the first minute. */
    private const val TRIGGERS_PER_AD = 4
    private const val MIN_INTERVAL_MS = 3 * 60_000L
    private const val GRACE_PERIOD_MS = 60_000L

    private val initStarted = AtomicBoolean(false)
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var consent: ConsentInformation? = null
    private var interstitial: InterstitialAd? = null
    private var loadingInterstitial = false
    private var triggers = 0
    private var lastShownAt = 0L
    private val sessionStart = SystemClock.elapsedRealtime()

    /** Call from the activity's onCreate. Shows the consent form when the user's region requires it. */
    fun start(activity: Activity) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        consent = info
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) Log.w(TAG, "Consent form: ${formError.message}")
                    if (info.canRequestAds()) initialize(activity)
                }
            },
            { error ->
                Log.w(TAG, "Consent info update failed: ${error.message}")
                if (info.canRequestAds()) initialize(activity)
            },
        )
        // Consent obtained in a previous session: start right away.
        if (info.canRequestAds()) initialize(activity)
    }

    /** True when the user's region needs a privacy-options entry point (shown in Settings). */
    val privacyOptionsRequired: Boolean
        get() = consent?.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "Privacy options: ${error.message}")
        }
    }

    private fun initialize(activity: Activity) {
        if (!initStarted.compareAndSet(false, true)) return
        val appContext = activity.applicationContext
        Thread {
            MobileAds.initialize(appContext) {
                // Ads must never interrupt the user's music.
                MobileAds.setAppMuted(true)
                activity.runOnUiThread {
                    _ready.value = true
                    loadInterstitial(appContext)
                }
            }
        }.start()
    }

    private fun loadInterstitial(context: Context) {
        if (!_ready.value || interstitial != null || loadingInterstitial) return
        loadingInterstitial = true
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadingInterstitial = false
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingInterstitial = false
                    interstitial = null
                    Log.w(TAG, "Interstitial failed to load: ${error.message}")
                }
            },
        )
    }

    /**
     * Counts a natural break (opening an album, artist, playlist, folder…) and shows an
     * interstitial when the frequency cap allows it.
     */
    fun onNaturalBreak(context: Context) {
        val activity = context.findActivity() ?: return
        if (!_ready.value) return
        triggers++
        val now = SystemClock.elapsedRealtime()
        val ad = interstitial
        if (ad == null) {
            loadInterstitial(activity.applicationContext)
            return
        }
        if (triggers < TRIGGERS_PER_AD || now - lastShownAt < MIN_INTERVAL_MS || now - sessionStart < GRACE_PERIOD_MS) return
        triggers = 0
        lastShownAt = now
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = loadInterstitial(activity.applicationContext)
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Interstitial failed to show: ${error.message}")
                loadInterstitial(activity.applicationContext)
            }
        }
        ad.show(activity)
    }

    private fun Context.findActivity(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}

/** Anchored adaptive banner that fills the available width. Renders nothing until ads are ready. */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val ready by Ads.ready.collectAsStateWithLifecycle()
    if (!ready) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.toInt().coerceAtLeast(320)
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                    adUnitId = BuildConfig.ADMOB_BANNER_ID
                    loadAd(AdRequest.Builder().build())
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
