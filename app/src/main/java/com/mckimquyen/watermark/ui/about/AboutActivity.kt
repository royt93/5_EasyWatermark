package com.mckimquyen.watermark.ui.about
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jakewharton.processphoenix.ProcessPhoenix
import com.mckimquyen.cmonet.CMonet
import com.mckimquyen.watermark.AppLog
import com.mckimquyen.watermark.BaseActivity
import com.mckimquyen.watermark.BuildConfig
import com.mckimquyen.watermark.LOG_TAG
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.databinding.AAboutBinding
import com.mckimquyen.watermark.export.AuthenticityVerifier
import com.mckimquyen.watermark.export.stego.StegoPayload
import com.mckimquyen.watermark.feature.vip.VipManagementActivity
import com.mckimquyen.watermark.utils.ktx.applyConsistentIconTint
import com.mckimquyen.watermark.utils.ktx.formatDate
import com.mckimquyen.watermark.utils.ktx.inflate
import com.mckimquyen.watermark.utils.ktx.openLink
import com.mckimquyen.watermark.utils.ktx.toast
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
            toast(if (success) R.string.backup_success else R.string.backup_failed)
        }
    }

    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        viewModel.restoreFrom(uri) { success ->
            toast(if (success) R.string.restore_success else R.string.restore_failed)
        }
    }

    /** IDEA-03: chọn 1 ảnh bất kỳ để kiểm tra con dấu chứng thực. */
    private val verifyLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        viewModel.verifyAuthenticity(contentResolver, uri, ::showVerifyResult)
    }

    /** IDEA-03 + IDEA-02: hiện kết quả kiểm tra. Nội dung dựng bởi [buildVerifyMessage] để test riêng được. */
    private fun showVerifyResult(report: AboutViewModel.VerifyReport) {
        val result = report.stamp
        val title = when {
            result is AuthenticityVerifier.Result.Stamped && result.intact -> R.string.authenticity_verify_intact
            result is AuthenticityVerifier.Result.Stamped -> R.string.authenticity_verify_altered
            // IDEA-02: EXIF mất sạch (mạng xã hội re-encode) nhưng lớp ẩn còn — vẫn truy được chủ ảnh,
            // nên tiêu đề phải phản ánh điều đó thay vì báo cụt "không có con dấu".
            report.hidden != null -> R.string.invisible_watermark_title
            result is AuthenticityVerifier.Result.Unreadable -> R.string.authenticity_verify_unreadable
            else -> R.string.authenticity_verify_no_stamp
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(buildVerifyMessage(report))
            .setPositiveButton(R.string.tips_confirm_dialog, null)
            .show()
    }

    /** Dựng phần thân dialog kết quả — tách khỏi [showVerifyResult] để test không cần dialog thật. */
    internal fun buildVerifyMessage(report: AboutViewModel.VerifyReport): String {
        val stampPart = buildStampMessage(report.stamp)
        val hiddenPart = report.hidden?.let { hidden ->
            buildList {
                add(getString(R.string.invisible_watermark_found, hidden.ownerIdHex()))
                // Đối chiếu với tên chủ sở hữu đang đặt: lớp ẩn chỉ mang ID rút gọn, không mang tên,
                // nên chỉ khẳng định được "có khớp người đang dùng máy này" chứ không in ra tên lạ.
                if (report.currentOwner.isNotBlank() &&
                    hidden.ownerId == StegoPayload.ownerIdOf(report.currentOwner)
                ) {
                    add(getString(R.string.invisible_watermark_owner_match))
                }
                add(getString(R.string.invisible_watermark_confidence, (hidden.confidence * 100).toInt()))
            }.joinToString("\n")
        } ?: getString(R.string.invisible_watermark_absent)

        // IDEA-10: dấu vân tay truy được người nhận → đặt LÊN ĐẦU, đây là thông tin quan trọng nhất
        // của cả màn hình khi ảnh bị rò rỉ.
        val leakPart = report.leakedRecipient?.let {
            getString(R.string.recipient_leak_detected, it.name, it.code) + "\n\n"
        }.orEmpty()

        return "$leakPart$stampPart\n\n$hiddenPart"
    }

    private fun buildStampMessage(result: AuthenticityVerifier.Result): String = when (result) {
        AuthenticityVerifier.Result.Unreadable -> getString(R.string.authenticity_verify_no_stamp_desc)
        AuthenticityVerifier.Result.NoStamp -> getString(R.string.authenticity_verify_no_stamp_desc)
        is AuthenticityVerifier.Result.Stamped -> buildList {
            // Chữ ký hỏng nghĩa là chính con dấu bị can thiệp — nói rõ, đừng chỉ báo "đã bị sửa".
            if (!result.signatureValid) {
                add(getString(R.string.authenticity_verify_bad_signature))
            } else if (!result.hashMatches) {
                add(getString(R.string.authenticity_verify_altered_desc))
            }
            add(getString(R.string.authenticity_verify_exported_at, result.timestampMs.formatDate("dd/MM/yyyy HH:mm")))
            add(
                if (result.owner.isEmpty()) {
                    getString(R.string.authenticity_verify_owner_unset)
                } else {
                    getString(R.string.authenticity_verify_owner, result.owner)
                }
            )
            if (result.keyFingerprint.isNotEmpty()) {
                add(getString(R.string.authenticity_verify_key, result.keyFingerprint))
                add(
                    if (result.signedByThisDevice) {
                        getString(R.string.authenticity_verify_own_key)
                    } else {
                        getString(R.string.authenticity_verify_other_key)
                    }
                )
            }
        }.joinToString("\n")
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
            tvVerifyAuthenticity.contentDescription =
                "${getString(R.string.authenticity_verify_entry)}, ${getString(R.string.authenticity_verify_entry_subtitle)}"
            tvVerifyAuthenticity.setOnClickListener {
                AppLog.d(LOG_TAG, "AboutActivity tvVerifyAuthenticity clicked — opening SAF open-document")
                verifyLauncher.launch(arrayOf("image/*"))
            }
            tvPrivacyEng.setOnClickListener {
                AppLog.d(LOG_TAG, "AboutActivity tvPrivacyEng clicked — opening privacy policy")
                openLink(Uri.parse(BuildConfig.PRIVACY_POLICY_URL))
            }
            rowVip.setOnClickListener {
                startActivity(android.content.Intent(this@AboutActivity, VipManagementActivity::class.java))
            }
            rowOpenSource.setOnClickListener {
                startActivity(android.content.Intent(this@AboutActivity, OpenSourceActivity::class.java))
            }

            switchDebug.setOnCheckedChangeListener { _, isChecked ->
                AppLog.d(LOG_TAG, "AboutActivity switchDebug changed -> isChecked=$isChecked")
                viewModel.toggleBounds(isChecked)
            }

            // BUG-41: isEnabled theo NĂNG LỰC thiết bị (CMonet.isDeviceSupported()), isChecked
            // theo LỰA CHỌN user (CMonet.isUserEnabled()) — trước đây cả 2 đều đọc
            // isDynamicColorAvailable() (đã gộp || isForceSupport luôn true trên API31+) nên tắt
            // switch xong mở lại About luôn thấy lại BẬT.
            val isDynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && CMonet.isDeviceSupported()
            if (isDynamicColorSupported) {
                switchDynamicColor.isEnabled = true
                switchDynamicColor.isChecked = CMonet.isUserEnabled()
                tvDynamicColorStatus.setText(R.string.dynamic_color_subtitle_supported)
            } else {
                switchDynamicColor.isEnabled = false
                switchDynamicColor.isChecked = false
                tvDynamicColorStatus.setText(R.string.dynamic_color_subtitle_unsupported)
            }
            AppLog.d(LOG_TAG, "AboutActivity dynamicColor supported=$isDynamicColorSupported available=${CMonet.isDynamicColorAvailable()}")

            switchDynamicColor.setOnCheckedChangeListener { _, isChecked ->
                AppLog.d(LOG_TAG, "AboutActivity switchDynamicColor changed -> isChecked=$isChecked — triggering rebirth")
                viewModel.toggleSupportDynamicColor(isChecked)
                this@AboutActivity.toast(R.string.you_ll_need_to_close_and_restart_the_app_to_switch_themes)
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
