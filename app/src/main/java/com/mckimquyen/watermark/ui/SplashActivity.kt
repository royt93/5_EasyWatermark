package com.mckimquyen.watermark.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mckimquyen.watermark.AppLog
import com.mckimquyen.watermark.LOG_TAG
import com.mckimquyen.watermark.databinding.ActivitySplashBinding
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.ExperimentalAdApi
import com.roy.sdkadbmob.awaitSplashComplete
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.d(LOG_TAG, "onCreate")
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        lifecycleScope.launch { runSplashFlow() }
    }

    @OptIn(ExperimentalAdApi::class)
    private suspend fun runSplashFlow() {
        // Fast-path: không có mạng → vào app ngay, không chờ SDK.
        if (!hasNetwork()) {
            AppLog.d(LOG_TAG, "No network — skip all ads, go to main immediately")
            goToMain()
            return
        }

        // Bước 1 — Consent: có mạng nhưng SDK có thể treo (captive portal, mạng yếu...).
        // Timeout 6s chỉ là safety net; path bình thường callback trong < 1s.
        val canRequestAds = withTimeoutOrNull(CONSENT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                AdManager.requestConsentInfoUpdate(this@SplashActivity) { result ->
                    if (cont.isActive) cont.resume(result)
                }
            }
        } ?: run {
            Log.w(LOG_TAG, "requestConsentInfoUpdate timeout — SDK hung, skip ads")
            false
        }

        if (!canRequestAds) {
            goToMain()
            return
        }

        // Bước 2 — Init SDK: timeout 8s safety net.
        if (!isAdInitialized) {
            val success = withTimeoutOrNull(INIT_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    AdManager.initialize(application) { ok, gaid ->
                        AppLog.d(LOG_TAG, "AdManager init success=$ok, gaid=$gaid")
                        if (cont.isActive) cont.resume(ok)
                    }
                }
            } ?: run {
                Log.w(LOG_TAG, "AdManager.initialize timeout — SDK hung, proceeding without ads")
                false
            }
            isAdInitialized = success
        }

        // Bước 3 — Splash ad: SDK tự quản timeout nội bộ; runCatching đảm bảo không crash khi lỗi.
        runCatching {
            AdManager.awaitSplashComplete(this@SplashActivity)
        }.onFailure {
            Log.w(LOG_TAG, "awaitSplashComplete failed, continuing to main", it)
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
        window.decorView.postDelayed({ finish() }, 300)
    }

    companion object {
        @Volatile
        private var isAdInitialized = false

        private const val CONSENT_TIMEOUT_MS = 6_000L
        private const val INIT_TIMEOUT_MS = 8_000L
    }
}
