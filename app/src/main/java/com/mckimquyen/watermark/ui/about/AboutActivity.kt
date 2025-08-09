package com.mckimquyen.watermark.ui.about

import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.jakewharton.processphoenix.ProcessPhoenix
import com.mckimquyen.cmonet.CMonet
import com.mckimquyen.watermark.BaseActivity
import com.mckimquyen.watermark.BuildConfig
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.databinding.AAboutBinding
import com.mckimquyen.watermark.sdkadbmob.AdMobManager
import com.mckimquyen.watermark.utils.ktx.colorSecondaryContainer
import com.mckimquyen.watermark.utils.ktx.inflate
import com.mckimquyen.watermark.utils.ktx.openLink
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AboutActivity : BaseActivity(), AdMobManager.InterstitialAdListener {

    private val binding by inflate<AAboutBinding>()

    private val viewModel: AboutViewModel by viewModels()

//    private lateinit var bgDrawable: GradientDrawable

    //    private var adView: MaxAdView? = null
    private var adView: AdView? = null

    override fun onResume() {
        super.onResume()
        adView?.resume()
    }

    override fun onPause() {
        adView?.pause()
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initView()
        changeStatusBarStyle()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        window?.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window?.navigationBarDividerColor = Color.TRANSPARENT
        }
        AdMobManager.setCurrentActivity(this)
        AdMobManager.interstitialListener = this
    }

    private fun changeStatusBarStyle() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.findViewById<View>(android.R.id.content)?.foreground = null
    }

    private fun initView() {
        with(binding) {

//            bgDrawable = ContextCompat.getDrawable(
//                this@AboutActivity,
//                R.drawable.bg_gradient_about_page
//            ) as GradientDrawable
//            this.root.background = bgDrawable

//            tvVersion.setOnClickListener {
//                openLink("https://github.com/rosuH/EasyWatermark/releases/")
//            }
            tvVersionValue.text = BuildConfig.VERSION_NAME
            tvRating.setOnClickListener {
//                openLink(Uri.parse("https://play.google.com/store/apps/details?id=com.mckimquyen.kqxs"))
                openLink(Uri.parse("https://play.google.com/store/apps/details?id=${it.context.packageName}"))
            }
            tvMoreApp.setOnClickListener {
                openLink("https://play.google.com/store/apps/developer?id=SAIGON PHANTOM LABS")
            }
//            tvChangeLog.setOnClickListener {
//                openLink("https://github.com/rosuH/EasyWatermark/releases/")
//            }
//            tvOpenSource.setOnClickListener {
//                kotlin.runCatching {
//                    startActivity(
//                        Intent(
//                            this@AboutActivity,
//                            OpenSourceActivity::class.java
//                        )
//                    )
//                }
//            }
//            tvPrivacyCn.setOnClickListener {
//                openLink(Uri.parse("https://github.com/rosuH/EasyWatermark/blob/master/PrivacyPolicy_zh-CN.md"))
//            }
            tvPrivacyEng.setOnClickListener {
                openLink(Uri.parse("https://loitp.notion.site/loitp/Privacy-Policy-319b1cd8783942fa8923d2a3c9bce60f"))
            }
//            civAvatar.setOnClickListener {
//                openLink("https://github.com/rosuH")
//            }
//            civAvatarDesigner.setOnClickListener {
//                openLink("https://tovi.fun/")
//            }
            ivBack.setOnClickListener {
                finish()
            }

            switchDebug.setOnCheckedChangeListener { _, isChecked ->
                viewModel.toggleBounds(isChecked)
            }

            switchDynamicColor.isChecked = CMonet.isDynamicColorAvailable()

            switchDynamicColor.setOnCheckedChangeListener { _, isChecked ->
                viewModel.toggleSupportDynamicColor(isChecked)
                Toast.makeText(
                    /* context = */ this@AboutActivity,
                    /* text = */ getString(R.string.you_ll_need_to_close_and_restart_the_app_to_switch_themes),
                    /* duration = */ Toast.LENGTH_SHORT
                ).show()
                ProcessPhoenix.triggerRebirth(this@AboutActivity)
            }

            binding.clDevContainer.backgroundTintList =
                ColorStateList.valueOf(this@AboutActivity.colorSecondaryContainer)
            binding.clDesignerContainer.backgroundTintList =
                ColorStateList.valueOf(this@AboutActivity.colorSecondaryContainer)

            viewModel.waterMark.observe(this@AboutActivity) {
                switchDebug.isChecked = viewModel.waterMark.value?.enableBounds ?: false
            }

//            viewModel.palette.observe(this@AboutActivity) {
//                when {
//                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
//                        applyPaletteForSupportNight(it)
//                    }
//
//                    it == null -> {
//                        binding.clContainer.children
//                            .plus(binding.tvTitle)
//                            .plus(binding.tvSubTitle)
//                            .plus(binding.tvTitleDesigner)
//                            .plus(binding.tvSubTitleDesigner)
//                            .forEach { view ->
//                                if (view !is TextView) {
//                                    return@forEach
//                                }
//                                view.setTextColor(Color.WHITE)
//                                TextViewCompat.setCompoundDrawableTintList(
//                                    view,
//                                    ColorStateList.valueOf(Color.WHITE)
//                                )
//                            }
//                        return@observe
//                    }
//
//                    else -> {
//                        applyPaletteForSupportLightStatusIcon(it)
//                    }
//                }
//            }

            adView = AdMobManager.loadBanner(
                context = this@AboutActivity,
                adUnitId = BuildConfig.ADMOB_BANNER_ID,
                container = binding.layoutAdBanner.bannerContainer,
                tvLabelAd = binding.layoutAdBanner.tvLabelAd,
                adSize = AdSize.LARGE_BANNER,
            )
//            adView = this@AboutActivity.createAdBanner(
//                logTag = AboutActivity::class.simpleName,
//                viewGroup = flAd,
//                isAdaptiveBanner = true,
//            )
//            createAdInter()
            AdMobManager.loadInterstitial(this@AboutActivity, BuildConfig.ADMOB_INTERSTITIAL_ID)
        }
    }

//    private fun applyPaletteForSupportNight(palette: Palette?) {
//        val bgColor = palette?.bgColor(this@AboutActivity) ?: this@AboutActivity.colorPrimary
//        val bgAccent = palette?.bgColor(this@AboutActivity) ?: this@AboutActivity.colorBackground
//        val colorList = arrayOf(
//            ColorUtils.setAlphaComponent(bgColor, 255),
//            ColorUtils.setAlphaComponent(bgAccent, 65),
//        ).toIntArray()
//        bgDrawable.colors = colorList
//    }

//    private fun applyPaletteForSupportLightStatusIcon(palette: Palette) {
//        val bgColor = palette.bgColor(this@AboutActivity)
//        val bgAccent = palette.bgColor(this@AboutActivity)
//        val colorList = arrayOf(
//            ColorUtils.setAlphaComponent(bgColor, 255),
//            ColorUtils.setAlphaComponent(bgAccent, 65),
//        ).toIntArray()
//        bgDrawable.colors = colorList
//
//        val textColor = palette.titleTextColor(this@AboutActivity)
//        binding.clContainer.children
//            .plus(binding.tvTitle)
//            .plus(binding.tvSubTitle)
//            .plus(binding.tvTitleDesigner)
//            .plus(binding.tvSubTitleDesigner)
//            .forEach { view ->
//                if (view !is TextView) {
//                    return@forEach
//                }
//                view.setTextColor(textColor)
//                TextViewCompat.setCompoundDrawableTintList(
//                    view,
//                    ColorStateList.valueOf(textColor)
//                )
//            }
//    }

//    private fun applyPaletteForNoMatterWhoYouAre(palette: Palette) {
//        val bgColor = palette.bgColor(this@AboutActivity)
//        val bgAccent = palette.bgColor(this@AboutActivity)
//        val colorList = arrayOf(
//            ColorUtils.setAlphaComponent(bgColor, 255),
//            ColorUtils.setAlphaComponent(bgAccent, 65),
//        ).toIntArray()
//        bgDrawable.colors = colorList
//
//        val textColor = palette.titleTextColor(this@AboutActivity)
//        binding.clContainer.children
//            .plus(binding.tvTitle)
//            .plus(binding.tvSubTitle)
//            .plus(binding.tvTitleDesigner)
//            .plus(binding.tvSubTitleDesigner)
//            .forEach { view ->
//                if (view !is TextView) {
//                    return@forEach
//                }
//                view.setTextColor(textColor)
//                TextViewCompat.setCompoundDrawableTintList(
//                    view,
//                    ColorStateList.valueOf(textColor)
//                )
//            }
//    }

    override fun onDestroy() {
        adView?.destroy()
//        with(binding) {
//            flAd.destroyAdBanner(adView)
//        }
        super.onDestroy()
//        showAd()
        AdMobManager.showInterstitial(this) { success ->
            if (success) {
                Log.d("roy93~", "Ad đã hiển thị và đóng thành công")
            } else {
                Log.d("roy93~", "Ad không hiển thị được hoặc có lỗi")
            }
        }
    }

    override fun onAdLoaded() {
    }

    override fun onAdFailedToLoad(error: LoadAdError) {
    }

    override fun onAdShowed() {
    }

    override fun onAdDismissed() {
    }

    override fun onAdClicked() {
    }

    override fun onAdFailedToShow(error: AdError) {
    }

    override fun onAdNotAvailable() {
    }

//    private var interstitialAd: MaxInterstitialAd? = null
//
//    private fun createAdInter() {
//        val enableAdInter = getString(R.string.EnableAdInter) == "true"
//        if (enableAdInter) {
//            interstitialAd = MaxInterstitialAd(getString(R.string.INTER), this)
//            interstitialAd?.let { ad ->
//                ad.setListener(object : MaxAdListener {
//                    override fun onAdLoaded(p0: MaxAd) {
////                        logI("onAdLoaded")
////                        retryAttempt = 0
//                    }
//
//                    override fun onAdDisplayed(p0: MaxAd) {
////                        logI("onAdDisplayed")
//                    }
//
//                    override fun onAdHidden(p0: MaxAd) {
////                        logI("onAdHidden")
//                        // Interstitial Ad is hidden. Pre-load the next ad
//                        interstitialAd?.loadAd()
//                    }
//
//                    override fun onAdClicked(p0: MaxAd) {
////                        logI("onAdClicked")
//                    }
//
//                    override fun onAdLoadFailed(p0: String, p1: MaxError) {
////                        logI("onAdLoadFailed")
////                        retryAttempt++
////                        val delayMillis =
////                            TimeUnit.SECONDS.toMillis(2.0.pow(min(6, retryAttempt)).toLong())
////
////                        Handler(Looper.getMainLooper()).postDelayed(
////                            {
////                                interstitialAd?.loadAd()
////                            }, delayMillis
////                        )
//                    }
//
//                    override fun onAdDisplayFailed(p0: MaxAd, p1: MaxError) {
////                        logI("onAdDisplayFailed")
//                        // Interstitial ad failed to display. We recommend loading the next ad.
//                        interstitialAd?.loadAd()
//                    }
//
//                })
//                ad.setRevenueListener {
////                    logI("onAdDisplayed")
//                }
//
//                // Load the first ad.
//                ad.loadAd()
//            }
//        }
//    }
//
//    private fun showAd(runnable: Runnable? = null) {
//        val enableAdInter = getString(R.string.EnableAdInter) == "true"
//        if (enableAdInter) {
//            if (interstitialAd == null) {
//                runnable?.run()
//            } else {
//                interstitialAd?.let { ad ->
//                    if (ad.isReady) {
////                        showDialogProgress()
////                        setDelay(500.getRandomNumber() + 500) {
////                            hideDialogProgress()
////                            ad.showAd()
////                            runnable?.run()
////                        }
//                        ad.showAd()
//                        runnable?.run()
//                    } else {
//                        runnable?.run()
//                    }
//                }
//            }
//        } else {
//            Toast.makeText(this, "Applovin show ad Inter in debug mode", Toast.LENGTH_SHORT).show()
//            runnable?.run()
//        }
//    }

}
