package com.mckimquyen.watermark.ui.about
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.jakewharton.processphoenix.ProcessPhoenix
import com.mckimquyen.cmonet.CMonet
import com.mckimquyen.watermark.BaseActivity
import com.mckimquyen.watermark.BuildConfig
import com.mckimquyen.watermark.AppLog
import com.mckimquyen.watermark.LOG_TAG
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.databinding.AAboutBinding
import com.mckimquyen.watermark.feature.vip.VipManagementActivity
import com.mckimquyen.watermark.utils.ktx.inflate
import com.mckimquyen.watermark.utils.ktx.openLink
import com.roy.sdkadbmob.AdManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AboutActivity : BaseActivity() {

    private val binding by inflate<AAboutBinding>()

    private val viewModel: AboutViewModel by viewModels()

//    private lateinit var bgDrawable: GradientDrawable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.d(LOG_TAG, "AboutActivity onCreate")
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
        AppLog.d(LOG_TAG, "AboutActivity onCreate complete — adManager set")
    }

    private fun initView() {
        with(binding) {
            AppLog.d(LOG_TAG, "AboutActivity initView — versionName=${BuildConfig.VERSION_NAME}")

            // Version display
            tvVersionValue.text = getString(R.string.about_version_display, BuildConfig.VERSION_NAME)
            tvVersion2.text = BuildConfig.VERSION_NAME

            // Back navigation via CollapsingToolbar's nav icon
            topAppBar.setNavigationOnClickListener {
                AppLog.d(LOG_TAG, "AboutActivity back button clicked via topAppBar")
                finish()
            }

            tvRating.setOnClickListener {
                AppLog.d(LOG_TAG, "AboutActivity tvRating clicked — opening Play Store")
                openLink(Uri.parse("https://play.google.com/store/apps/details?id=${it.context.packageName}"))
            }
            tvMoreApp.setOnClickListener {
                AppLog.d(LOG_TAG, "AboutActivity tvMoreApp clicked — opening developer page")
                openLink("https://play.google.com/store/apps/developer?id=SAIGON PHANTOM LABS")
            }
            tvShareApp.setOnClickListener {
                AppLog.d(LOG_TAG, "AboutActivity tvShareApp clicked — opening share sheet")
                val message = getString(
                    R.string.share_app_message,
                    getString(R.string.app_name),
                    packageName,
                )
                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, message)
                }
                startActivity(android.content.Intent.createChooser(sendIntent, getString(R.string.share_app)))
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
                AppLog.d(LOG_TAG, "AboutActivity tvPrivacyEng clicked — opening privacy policy")
                openLink(Uri.parse(BuildConfig.PRIVACY_POLICY_URL))
            }
            rowVip.setOnClickListener {
                startActivity(android.content.Intent(this@AboutActivity, VipManagementActivity::class.java))
            }

            switchDebug.setOnCheckedChangeListener { _, isChecked ->
                AppLog.d(LOG_TAG, "AboutActivity switchDebug changed -> isChecked=$isChecked")
                viewModel.toggleBounds(isChecked)
            }

            switchDynamicColor.isChecked = CMonet.isDynamicColorAvailable()
            AppLog.d(LOG_TAG, "AboutActivity dynamicColor available=${CMonet.isDynamicColorAvailable()}")

            switchDynamicColor.setOnCheckedChangeListener { _, isChecked ->
                AppLog.d(LOG_TAG, "AboutActivity switchDynamicColor changed -> isChecked=$isChecked — triggering rebirth")
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
                AppLog.d(LOG_TAG, "AboutActivity waterMark observed — enableBounds=$boundsEnabled")
                switchDebug.isChecked = boundsEnabled
            }

            bannerAdView = AdManager.loadBanner(
                context = this@AboutActivity,
                container = binding.layoutAdBanner.bannerContainer,
                tvLabelAd = binding.layoutAdBanner.tvLabelAd,
                adSize = AdManager.getAdaptiveBannerSize(this@AboutActivity),
            )
            AdManager.loadInterstitial(this@AboutActivity)
        }
    }

    /** Reference banner view trả về từ loadBanner — giữ để gỡ thủ công khi VIP active. */
    private var bannerAdView: View? = null

    override fun onResume() {
        super.onResume()
        // ENH-11: khôi phục auto-refresh banner đã tạm dừng ở onPause (null-safe/idempotent
        // nếu bannerAdView đã bị destroy — xem AdManager.bannerResume).
        AdManager.bannerResume(bannerAdView)
        // Người dùng có thể vừa kích hoạt VIP ở VipManagementActivity rồi quay lại đây.
        // Activity này chỉ resume (không recreate) nên banner đã load từ trước vẫn còn hiển thị
        // → gỡ ngay để tôn trọng trạng thái VIP.
        if (AdManager.isVipByKeyActive()) {
            AdManager.bannerDestroy(bannerAdView)
            bannerAdView = null
            binding.layoutAdBanner.bannerContainer.isVisible = false
            binding.layoutAdBanner.tvLabelAd.isVisible = false
        }
    }

    override fun onPause() {
        super.onPause()
        // ENH-11: dừng auto-refresh banner khi Activity không còn ở foreground (tiết kiệm pin,
        // tránh impression ảo) — không destroy hẳn vì user có thể quay lại (xem onResume).
        AdManager.bannerPause(bannerAdView)
    }

    override fun onDestroy() {
        super.onDestroy()
        // ENH-11: destroy hẳn khi Activity bị huỷ thật (không chỉ pause) — idempotent, an toàn
        // nếu đã bị destroy sớm hơn ở nhánh VIP trong onResume.
        AdManager.bannerDestroy(bannerAdView)
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
                AppLog.d(LOG_TAG, "Ad đã hiển thị và đóng thành công")
            } else {
                AppLog.d(LOG_TAG, "Ad không hiển thị được hoặc có lỗi")
            }
            finish()
        }
    }

}
