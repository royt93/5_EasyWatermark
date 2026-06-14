package com.mckimquyen.watermark.feature.vip

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mckimquyen.watermark.BaseActivity
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.common.const.AdKeys
import com.mckimquyen.watermark.databinding.ActivityVipManagementBinding
import com.roy.sdkadbmob.AdManager
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.Date
import java.util.concurrent.TimeUnit

class VipManagementActivity : BaseActivity() {

    private lateinit var binding: ActivityVipManagementBinding
    private val vipPrefs by lazy { VipPrefs(this) }

    // Animator/timer nullable + cancel ở onDestroy/onPause để tránh leak (rule template 10.5).
    private var countDownTimer: CountDownTimer? = null
    private var pulseAnimator: ObjectAnimator? = null
    private var countUpAnimator: ValueAnimator? = null
    private var lastShownMinute: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVipManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()
        setupView()
        refreshVipState()
        AdManager.loadRewarded(this)
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun setupView() {
        binding.topAppBar.setNavigationOnClickListener { finish() }
        binding.btnRedeemKey.setOnClickListener { redeemKey() }
        binding.btnWatchRewarded.setOnClickListener { watchRewarded() }
        binding.btnRevoke.setOnClickListener { confirmRevoke() }
        // Nút "Kích hoạt VIP" chỉ bật khi đã nhập mã.
        binding.edtVipKey.doAfterTextChanged { text ->
            binding.btnRedeemKey.isEnabled = !text.isNullOrBlank()
        }
        binding.tvPrivacy.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AdKeys.PRIVACY_POLICY_URL)))
        }
    }

    // ----------------------------------------------------------------------------------
    // Redeem / rewarded / revoke
    // ----------------------------------------------------------------------------------

    private fun redeemKey() {
        val days = VipKeys.durationDaysFor(binding.edtVipKey.text?.toString().orEmpty())
        if (days == null) {
            showResultDialog(R.string.vip_failed_title, getString(R.string.vip_key_invalid))
            return
        }
        val activated = AdManager.activateVipByKey(this, AdKeys.VIP_SECRET_30_DAYS, days)
        if (activated) {
            vipPrefs.markUserActivatedVip()
            binding.edtVipKey.text?.clear()
            celebrate()
            refreshVipState()
            showResultDialog(R.string.vip_success_title, getString(R.string.vip_key_activated, days))
        } else {
            showResultDialog(R.string.vip_failed_title, getString(R.string.vip_key_invalid))
        }
    }

    private fun watchRewarded() {
        binding.btnWatchRewarded.isEnabled = false
        AdManager.showRewarded(this) { earned ->
            if (isFinishing || isDestroyed) return@showRewarded
            if (earned) {
                grantRewardedVip()
                finishRewardFlow()
            } else {
                AdManager.showInterstitial(this) { shown ->
                    if (isFinishing || isDestroyed) return@showInterstitial
                    if (shown) {
                        grantRewardedVip()
                    } else {
                        showResultDialog(R.string.vip_failed_title, getString(R.string.vip_reward_unavailable))
                    }
                    finishRewardFlow()
                }
            }
        }
    }

    private fun grantRewardedVip() {
        val activated = AdManager.activateVipByKey(this, AdKeys.VIP_SECRET_30_DAYS, REWARDED_VIP_DAYS)
        if (activated) {
            vipPrefs.markUserActivatedVip()
            celebrate()
            showResultDialog(R.string.vip_success_title, getString(R.string.vip_reward_activated, REWARDED_VIP_DAYS))
        }
    }

    private fun showResultDialog(titleRes: Int, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun finishRewardFlow() {
        refreshVipState()
        AdManager.loadRewarded(this)
    }

    private fun confirmRevoke() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vip_revoke_confirm_title)
            .setMessage(R.string.vip_revoke_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                AdManager.clearVipByKey()
                vipPrefs.clearGrantedAtMs()
                Toast.makeText(this, R.string.vip_revoked, Toast.LENGTH_SHORT).show()
                refreshVipState()
            }
            .show()
    }

    // ----------------------------------------------------------------------------------
    // State binding
    // ----------------------------------------------------------------------------------

    private fun refreshVipState() {
        val isActive = AdManager.isVipByKeyActive()
        val expiryMs = AdManager.getVipByKeyExpiry()
        val isGrace = isActive &&
            expiryMs > 0L &&
            vipPrefs.isFirstInstallGraceGranted() &&
            !vipPrefs.hasUserActivatedVip()

        // Hero header
        binding.statusHeader.setBackgroundResource(
            if (isActive) R.drawable.bg_vip_status_header_active else R.drawable.bg_vip_status_header_free
        )
        binding.tvVipStatus.text =
            getString(if (isActive) R.string.vip_status_active else R.string.vip_status_free)
        val entryLabel = when {
            isGrace -> getString(R.string.vip_entry_first_install)
            isActive -> getString(R.string.vip_entry_manual)
            else -> getString(R.string.vip_entry_none)
        }
        binding.tvVipEntry.text = entryLabel
        binding.tvVipExpiry.text = if (expiryMs > 0L && isActive) {
            getString(R.string.vip_expiry, formatDate(expiryMs))
        } else {
            getString(R.string.vip_expiry_none)
        }

        // Active card (#12)
        binding.cardActiveVip.isVisible = isActive
        binding.tvActiveEntryLabel.text = entryLabel
        binding.tvActiveRemaining.text = getString(R.string.vip_expiry, formatDate(expiryMs))

        // Reward card chỉ hiện khi chưa VIP
        binding.cardReward.isVisible = !isActive
        binding.btnWatchRewarded.isEnabled = !isActive

        // Timer + progress (#2/#4/#5)
        val grantedAtMs = resolveGrantedAtMs(expiryMs, isGrace)
        if (isActive && expiryMs > System.currentTimeMillis()) {
            binding.cardTimer.isVisible = true
            binding.tvActivatedAt.text = getString(R.string.vip_activated_at, formatDate(grantedAtMs))
            startCountdown(grantedAtMs, expiryMs)
        } else {
            binding.cardTimer.isVisible = false
            stopCountdown()
        }
    }

    /** Thời điểm cấp VIP: ưu tiên giá trị app lưu; grace fallback = expiry - 24h. */
    private fun resolveGrantedAtMs(expiryMs: Long, isGrace: Boolean): Long {
        val stored = vipPrefs.lastGrantedAtMs()
        return when {
            stored in 1 until expiryMs -> stored
            isGrace && expiryMs > DAY_MS -> expiryMs - DAY_MS
            expiryMs > DAY_MS -> expiryMs - DAY_MS
            else -> System.currentTimeMillis()
        }
    }

    // ----------------------------------------------------------------------------------
    // Countdown + progress (elapsed semantic)
    // ----------------------------------------------------------------------------------

    private fun startCountdown(grantedAtMs: Long, expiryMs: Long) {
        stopCountdown()
        val remaining = expiryMs - System.currentTimeMillis()
        if (remaining <= 0L) return
        bindCountdownTick(grantedAtMs, expiryMs)
        countDownTimer = object : CountDownTimer(remaining, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                bindCountdownTick(grantedAtMs, expiryMs)
            }

            override fun onFinish() {
                refreshVipState()
            }
        }.also { it.start() }
    }

    private fun bindCountdownTick(grantedAtMs: Long, expiryMs: Long) {
        val now = System.currentTimeMillis()
        val remaining = (expiryMs - now).coerceAtLeast(0L)
        val days = TimeUnit.MILLISECONDS.toDays(remaining)
        val hours = TimeUnit.MILLISECONDS.toHours(remaining) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
        binding.tvCountdown.text = getString(R.string.vip_remaining, days, hours, minutes, seconds)
        binding.progressVip.setProgressCompat(
            computeElapsedProgress(grantedAtMs, expiryMs, now),
            true
        )

        // Animation #4: count-up nhẹ khi phút đổi (không chạy mỗi giây).
        val currentMinute = TimeUnit.MILLISECONDS.toMinutes(remaining)
        if (currentMinute != lastShownMinute) {
            lastShownMinute = currentMinute
            animateCountdownBump()
        }
    }

    /**
     * progress = (now - grantedAt) / (expiry - grantedAt) * 100. Bar RỖNG lúc kích hoạt, ĐẦY DẦN đến hết hạn.
     */
    private fun computeElapsedProgress(grantedAtMs: Long, expiryMs: Long, nowMs: Long): Int {
        val total = expiryMs - grantedAtMs
        if (total <= 0L) return 100
        val elapsed = nowMs - grantedAtMs
        return ((elapsed.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    }

    private fun stopCountdown() {
        countDownTimer?.cancel()
        countDownTimer = null
        lastShownMinute = -1L
    }

    // ----------------------------------------------------------------------------------
    // Animations
    // ----------------------------------------------------------------------------------

    private fun animateCountdownBump() {
        countUpAnimator?.cancel()
        countUpAnimator = ValueAnimator.ofFloat(1f, 1.06f, 1f).apply {
            duration = 400L
            addUpdateListener {
                val s = it.animatedValue as Float
                binding.tvCountdown.scaleX = s
                binding.tvCountdown.scaleY = s
            }
            start()
        }
    }

    private fun startPulse() {
        if (!binding.btnWatchRewarded.isVisible) return
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.btnWatchRewarded,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.03f),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.03f)
        ).apply {
            duration = 1600L
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        binding.btnWatchRewarded.scaleX = 1f
        binding.btnWatchRewarded.scaleY = 1f
    }

    /** Animation #5 + haptic khi kích hoạt thành công. */
    private fun celebrate() {
        haptic()
        val party = Party(
            speed = 0f,
            maxSpeed = 30f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(0xFFD60A.toInt(), 0xFF726D.toInt(), 0xB48DEF.toInt(), 0x7C4DFF),
            emitter = Emitter(duration = 200, TimeUnit.MILLISECONDS).max(120),
            position = Position.Relative(0.5, 0.25)
        )
        binding.viewKonfetti.start(party)
    }

    private fun haptic() {
        val view = binding.root
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private fun formatDate(timestampMs: Long): String {
        return DateFormat.getMediumDateFormat(this).format(Date(timestampMs)) +
            " " +
            DateFormat.getTimeFormat(this).format(Date(timestampMs))
    }

    // ----------------------------------------------------------------------------------
    // Lifecycle
    // ----------------------------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        binding.shimmerCrown.startShimmer()
        binding.shimmerWatch.startShimmer()
        startPulse()
    }

    override fun onPause() {
        binding.shimmerCrown.stopShimmer()
        binding.shimmerWatch.stopShimmer()
        stopPulse()
        countUpAnimator?.cancel()
        countUpAnimator = null
        super.onPause()
    }

    override fun onDestroy() {
        stopCountdown()
        stopPulse()
        countUpAnimator?.cancel()
        countUpAnimator = null
        super.onDestroy()
    }

    companion object {
        private const val REWARDED_VIP_DAYS = 3
        private const val DAY_MS = 86_400_000L
    }
}
