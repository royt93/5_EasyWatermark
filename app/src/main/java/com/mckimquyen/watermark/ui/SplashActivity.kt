package com.mckimquyen.watermark.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.mckimquyen.watermark.AppLog
import com.mckimquyen.watermark.BaseActivity
import com.mckimquyen.watermark.BuildConfig
import com.mckimquyen.watermark.LOG_TAG
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.databinding.ActivitySplashBinding
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.ExperimentalAdApi
import com.roy.sdkadbmob.awaitSplashComplete
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.d(LOG_TAG) { "onCreate" }
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.tvVersion.text = getString(
            R.string.version_name_build_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE.toString()
        )
        binding.tvCopyright.text = getString(R.string.copyright_rights_reserved_format, getString(R.string.app_copyright))

        lifecycleScope.launch { runSplashFlow() }
    }

    /**
     * Đúng doc Bước 4: `initialize()` đã gọi ở MyApplication (defer provider tới khi có consent). Splash chỉ
     * `requestConsentInfoUpdate` rồi `awaitSplashComplete` — SDK tự giữ watchdog (15s fetch + 180s form UMP).
     * Không tự timeout riêng: timeout app-level từng cắt form consent của user EEA (doc cảnh báo không hạ thấp).
     */
    @OptIn(ExperimentalAdApi::class)
    private suspend fun runSplashFlow() {
        val startTime = System.currentTimeMillis()

        // Không mạng → fullscreen ad fail-soft, vào app ngay (SDK tự retry consent + preload khi mạng về).
        if (hasNetwork()) {
            suspendCancellableCoroutine { cont ->
                AdManager.requestConsentInfoUpdate(this@SplashActivity) { canRequestAds ->
                    AppLog.d(LOG_TAG) { "UMP consent gathered: canRequestAds=$canRequestAds" }
                    if (cont.isActive) cont.resume(canRequestAds)
                }
            }
            // SDK tự load+show App Open với timeout nội bộ; runCatching để lỗi SDK không chặn vào app.
            runCatching {
                AdManager.awaitSplashComplete(this@SplashActivity)
            }.onFailure {
                AppLog.w(LOG_TAG, "awaitSplashComplete failed, continuing to main", it)
            }
        } else {
            AppLog.d(LOG_TAG) { "No network — skip all ads, go to main" }
        }

        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed < MIN_SPLASH_DURATION_MS) {
            kotlinx.coroutines.delay(MIN_SPLASH_DURATION_MS - elapsed)
        }
        goToMain()
    }

    /** Trả true nếu thiết bị có kết nối internet đã được xác thực (không chỉ connected WiFi). */
    private fun hasNetwork(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun goToMain() {
        if (isFinishing || isDestroyed) return
        startActivity(Intent(this@SplashActivity, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        window.decorView.postDelayed({ finish() }, FINISH_AFTER_TRANSITION_DELAY_MS)
    }

    companion object {
        private const val MIN_SPLASH_DURATION_MS = 1_500L

        /** Thời gian chờ hiệu ứng fade chuyển sang MainActivity chạy xong trước khi finish() Splash. */
        private const val FINISH_AFTER_TRANSITION_DELAY_MS = 300L
    }
}
