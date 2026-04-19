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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.jakewharton.processphoenix.ProcessPhoenix
import com.mckimquyen.cmonet.CMonet
import com.mckimquyen.watermark.BaseActivity
import com.mckimquyen.watermark.BuildConfig
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.databinding.AAboutBinding
import com.roy.sdkadbmob.AdManager
import com.mckimquyen.watermark.utils.ktx.colorSecondaryContainer
import com.mckimquyen.watermark.utils.ktx.inflate
import com.mckimquyen.watermark.utils.ktx.openLink
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AboutActivity : BaseActivity() {

    private val binding by inflate<AAboutBinding>()

    private val viewModel: AboutViewModel by viewModels()

//    private lateinit var bgDrawable: GradientDrawable

    //    private var adView: MaxAdView? = null
    private var adView: View? = null

    override fun onResume() {
        super.onResume()
        Log.d("roy93~", "AboutActivity onResume")
        AdManager.bannerResume(adView)
    }

    override fun onPause() {
        Log.d("roy93~", "AboutActivity onPause")
        AdManager.bannerPause(adView)
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("roy93~", "AboutActivity onCreate")
        initView()
        // Edge-to-edge is handled globally by BaseActivity.applyEdgeToEdge()
        // Add inset listener so AppBarLayout starts BELOW the status bar, and root handles bottom nav bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }
        Log.d("roy93~", "AboutActivity onCreate complete — adManager set")
    }

    private fun initView() {
        with(binding) {
            Log.d("roy93~", "AboutActivity initView — versionName=${BuildConfig.VERSION_NAME}")

            // Version display
            tvVersionValue.text = "v${BuildConfig.VERSION_NAME}"
            tvVersion2.text = BuildConfig.VERSION_NAME

            // Back navigation via CollapsingToolbar's nav icon
            topAppBar.setNavigationOnClickListener {
                Log.d("roy93~", "AboutActivity back button clicked via topAppBar")
                finish()
            }

            tvRating.setOnClickListener {
                Log.d("roy93~", "AboutActivity tvRating clicked — opening Play Store")
                openLink(Uri.parse("https://play.google.com/store/apps/details?id=${it.context.packageName}"))
            }
            tvMoreApp.setOnClickListener {
                Log.d("roy93~", "AboutActivity tvMoreApp clicked — opening developer page")
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
                Log.d("roy93~", "AboutActivity tvPrivacyEng clicked — opening privacy policy")
                openLink(Uri.parse("https://loitp.notion.site/loitp/Privacy-Policy-319b1cd8783942fa8923d2a3c9bce60f"))
            }

            switchDebug.setOnCheckedChangeListener { _, isChecked ->
                Log.d("roy93~", "AboutActivity switchDebug changed -> isChecked=$isChecked")
                viewModel.toggleBounds(isChecked)
            }

            switchDynamicColor.isChecked = CMonet.isDynamicColorAvailable()
            Log.d("roy93~", "AboutActivity dynamicColor available=${CMonet.isDynamicColorAvailable()}")

            switchDynamicColor.setOnCheckedChangeListener { _, isChecked ->
                Log.d("roy93~", "AboutActivity switchDynamicColor changed -> isChecked=$isChecked — triggering rebirth")
                viewModel.toggleSupportDynamicColor(isChecked)
                Toast.makeText(
                    /* context = */ this@AboutActivity,
                    /* text = */ getString(R.string.you_ll_need_to_close_and_restart_the_app_to_switch_themes),
                    /* duration = */ Toast.LENGTH_SHORT
                ).show()
                ProcessPhoenix.triggerRebirth(this@AboutActivity)
            }

            viewModel.waterMark.observe(this@AboutActivity) {
                val boundsEnabled = viewModel.waterMark.value?.enableBounds ?: false
                Log.d("roy93~", "AboutActivity waterMark observed — enableBounds=$boundsEnabled")
                switchDebug.isChecked = boundsEnabled
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

            adView = AdManager.loadBanner(
                context = this@AboutActivity,
                container = binding.layoutAdBanner.bannerContainer,
                tvLabelAd = binding.layoutAdBanner.tvLabelAd,
            )
//            adView = this@AboutActivity.createAdBanner(
//                logTag = AboutActivity::class.simpleName,
//                viewGroup = flAd,
//                isAdaptiveBanner = true,
//            )
//            createAdInter()
            AdManager.loadInterstitial(this@AboutActivity)
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

    private var isFinishingInternal = false
    
    override fun finish() {
        if (isFinishingInternal) {
            super.finish()
            return
        }
        isFinishingInternal = true
        AdManager.showInterstitial(this) { success ->
            if (success) {
                Log.d("roy93~", "Ad đã hiển thị và đóng thành công")
            } else {
                Log.d("roy93~", "Ad không hiển thị được hoặc có lỗi")
            }
            finish()
        }
    }

    override fun onDestroy() {
        AdManager.bannerDestroy(adView)
//        with(binding) {
//            flAd.destroyAdBanner(adView)
//        }
        super.onDestroy()
//        showAd()
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
