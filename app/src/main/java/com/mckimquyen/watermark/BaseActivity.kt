package com.mckimquyen.watermark

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import java.util.Calendar
import kotlin.apply
import kotlin.collections.maxByOrNull

open class BaseActivity : AppCompatActivity() {

    companion object {
        /** REVIEW-13: trần cỡ chữ hệ thống cho phép phóng to — xem giải thích ở [attachBaseContext]. */
        private const val MAX_FONT_SCALE = 1.3f
    }

    override fun attachBaseContext(context: Context) {
        // REVIEW-13: trước đây ép CỨNG fontScale=1.0f cho MỌI activity — vô hiệu hoá hoàn toàn
        // cài đặt cỡ chữ hệ thống (Accessibility > Font size), vi phạm R5 "không được bỏ
        // accessibility". Clamp thay vì chặn tuyệt đối: vẫn tôn trọng người dùng chỉnh cỡ chữ
        // lớn hơn (tới 130%) nhưng chặn mức cực đoan (hệ thống cho tới 200%) có thể vỡ layout
        // các màn hình dùng chiều cao cố định (nút, slider) chưa được test ở scale cực lớn.
        val override = Configuration(context.resources.configuration)
        override.fontScale = context.resources.configuration.fontScale.coerceAtMost(MAX_FONT_SCALE)
        applyOverrideConfiguration(override)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        applyEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Edge-to-edge: unified for all SDK versions.
     * - WindowCompat.setDecorFitsSystemWindows(false) → content draws behind system bars
     * - Status bar: transparent
     * - Navigation bar: transparent
     * - Icon tint: adapts to Light/Dark theme (see isAppearanceLight* below)
     */
    protected fun applyEdgeToEdge() {
        AppLog.d(LOG_TAG) { "BaseActivity applyEdgeToEdge" }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = Color.TRANSPARENT
        }

        // Material You Day/Night: adapt status and nav bar icon contrast to theme
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isNight
        insetsController.isAppearanceLightNavigationBars = !isNight
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            enableAdaptiveRefreshRate()
        }
    }

    private fun enableAdaptiveRefreshRate() {
        runCatching {
            val wm = getSystemService(WINDOW_SERVICE) as? WindowManager
            val currentDisplay: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { display }.getOrNull()
            } else {
                @Suppress("DEPRECATION")
                wm?.defaultDisplay
            }

            if (currentDisplay != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val supportedModes = currentDisplay.supportedModes
                val highestRefreshRateMode = supportedModes.maxByOrNull { it.refreshRate }
                if (highestRefreshRateMode != null) {
                    window.attributes = window.attributes.apply {
                        preferredDisplayModeId = highestRefreshRateMode.modeId
                    }
                }
            }
        }
    }
}

fun Activity.rateAppInApp(forceRateInApp: Boolean = false) {
    val sharedPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    val lastReviewTime = sharedPreferences.getLong("last_review_time", 0L)
    val currentTime = Calendar.getInstance().timeInMillis
    val daysSinceLastReview = (currentTime - lastReviewTime) / (1000 * 60 * 60 * 24)
    if (daysSinceLastReview >= 7 || forceRateInApp) {
        val reviewManager = ReviewManagerFactory.create(this)
        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo: ReviewInfo = task.result
                reviewManager.launchReviewFlow(this, reviewInfo)
                sharedPreferences.edit().putLong("last_review_time", currentTime).apply()
            }
        }
    }
}
