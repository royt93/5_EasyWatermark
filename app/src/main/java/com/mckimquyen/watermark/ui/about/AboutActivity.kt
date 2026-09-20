package com.mckimquyen.watermark.ui.about
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.jakewharton.processphoenix.ProcessPhoenix
import com.mckimquyen.cmonet.CMonet
import com.mckimquyen.watermark.AppLog
import com.mckimquyen.watermark.BaseActivity
import com.mckimquyen.watermark.BuildConfig
import com.mckimquyen.watermark.LOG_TAG
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.databinding.AAboutBinding
import com.mckimquyen.watermark.feature.vip.VipManagementActivity
import com.mckimquyen.watermark.utils.ktx.inflate
import com.mckimquyen.watermark.utils.ktx.openLink
import android.graphics.Color
import com.google.android.material.color.MaterialColors
import com.mckimquyen.watermark.utils.ktx.applyConsistentIconTint
import com.roy.sdkadbmob.AdManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AboutActivity : BaseActivity() {

    companion object {
        /**
         * BUG-30: trang `store/apps/developer?id=` yêu cầu Developer ID SỐ (không phải tên công
         * ty dạng chuỗi thô) — dùng `store/search?q=pub:` (tìm theo tên publisher) thay thế, kèm
         * `Uri.encode` cho khoảng trắng trong tên. Tách hàm riêng để test được không cần Activity.
         */
        internal fun buildMoreAppsUrl(developerName: String): String =
            "https://play.google.com/store/search?q=pub:${Uri.encode(developerName)}"
    }

    private val binding by inflate<AAboutBinding>()

    private val viewModel: AboutViewModel by viewModels()

    // Đề xuất F: backup/restore Template + Signature qua SAF — launcher phải đăng ký trước
    // STARTED (property khởi tạo ngay khi Activity tạo), không được gọi trong onClick.
    private val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@registerForActivityResult
        viewModel.backupTo(uri) { success ->
            Toast.makeText(this, getString(if (success) R.string.backup_success else R.string.backup_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        viewModel.restoreFrom(uri) { success ->
            Toast.makeText(this, getString(if (success) R.string.restore_success else R.string.restore_failed), Toast.LENGTH_SHORT).show()
        }
    }

//    private lateinit var bgDrawable: GradientDrawable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.d(LOG_TAG, "AboutActivity onCreate")
        initView()
        // Edge-to-edge (BaseActivity.applyEdgeToEdge): push topAppBar down below status bar & camera cutout,
        // and add navigation bar bottom padding to nestedScrollView so all content is reachable.
        val baseScrollBottomPadding = (32 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarTop = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top
            val navBarBottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout()
            ).bottom
            binding.topAppBar.setPadding(0, statusBarTop, 0, 0)
            binding.nestedScrollView.setPadding(0, 0, 0, baseScrollBottomPadding + navBarBottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        AppLog.d(LOG_TAG, "AboutActivity onCreate complete — adManager set")
    }

    private fun initView() {
        with(binding) {
            AppLog.d(LOG_TAG, "AboutActivity initView — versionName=${BuildConfig.VERSION_NAME}")

            // Version display
            tvVersionValue.text = getString(R.string.about_version_display, BuildConfig.VERSION_NAME)
            tvVersion2.text = BuildConfig.VERSION_NAME

            // Back navigation via CollapsingToolbar's nav icon
            val iconColor = MaterialColors.getColor(this@AboutActivity, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
            topAppBar.applyConsistentIconTint(iconColor)
            topAppBar.setNavigationOnClickListener {
                AppLog.d(LOG_TAG, "AboutActivity back button clicked via topAppBar")
                finish()
            }

            tvRating.contentDescription = "${getString(R.string.action_rate_us)}, ${getString(R.string.rate_us_subtitle)}"
            tvRating.setOnClickListener {
                AppLog.d(LOG_TAG, "AboutActivity tvRating clicked — opening Play Store")
                openLink(Uri.parse("https://play.google.com/store/apps/details?id=${it.context.packageName}"))
            }
            tvMoreApp.contentDescription = "${getString(R.string.more_apps)}, ${getString(R.string.more_apps_subtitle)}"
            tvMoreApp.setOnClickListener {
                AppLog.d(LOG_TAG, "AboutActivity tvMoreApp clicked — opening developer page")
                openLink(buildMoreAppsUrl("SAIGON PHANTOM LABS"))
            }
            tvShareApp.contentDescription = "${getString(R.string.share_app)}, ${getString(R.string.share_app_subtitle)}"
            tvShareApp.setOnClickListener {
                AppLog.d(LOG_TAG, "AboutActivity tvShareApp clicked — opening share sheet")
                val message = getString(
                    R.string.share_app_message,
                    getString(R.string.app_name),
                    packageName
                )
                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, message)
                }
                startActivity(android.content.Intent.createChooser(sendIntent, getString(R.string.share_app)))
            }
            tvBackupData.contentDescription = "${getString(R.string.backup_data)}, ${getString(R.string.backup_data_subtitle)}"
            tvBackupData.setOnClickListener {
                AppLog.d(LOG_TAG, "AboutActivity tvBackupData clicked — opening SAF create-document")
                backupLauncher.launch(getString(R.string.backup_file_name))
            }
            tvRestoreData.contentDescription = "${getString(R.string.restore_data)}, ${getString(R.string.restore_data_subtitle)}"
            tvRestoreData.setOnClickListener {
                AppLog.d(LOG_TAG, "AboutActivity tvRestoreData clicked — opening SAF open-document")
                restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
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
                adSize = AdManager.getAdaptiveBannerSize(this@AboutActivity)
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
