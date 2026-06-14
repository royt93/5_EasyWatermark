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


            adView = AdManager.loadBanner(
                context = this@AboutActivity,
                container = binding.layoutAdBanner.bannerContainer,
                tvLabelAd = binding.layoutAdBanner.tvLabelAd,
            )
            AdManager.loadInterstitial(this@AboutActivity)
        }
    }

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
        super.onDestroy()
    }

}
