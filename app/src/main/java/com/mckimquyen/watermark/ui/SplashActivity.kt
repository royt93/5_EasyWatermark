package com.mckimquyen.watermark.ui
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mckimquyen.watermark.LOG_TAG
import com.mckimquyen.watermark.databinding.ActivitySplashBinding
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.ExperimentalAdApi
import com.roy.sdkadbmob.awaitSplashComplete
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(LOG_TAG, "onCreate")
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AdManager.requestConsentInfoUpdate(this) { canRequestAds ->
            if (canRequestAds) {
                initializeAdsThenRunSplashFlow()
            } else {
                goToMain()
            }
        }
    }

    private fun initializeAdsThenRunSplashFlow() {
        if (isAdInitialized) {
            runSplashAdFlow()
            return
        }
        AdManager.initialize(application) { success, gaid ->
            Log.d(LOG_TAG, "AdManager init success=$success, gaid=$gaid")
            isAdInitialized = success
            runSplashAdFlow()
        }
    }

    @OptIn(ExperimentalAdApi::class)
    private fun runSplashAdFlow() {
        lifecycleScope.launch {
            runCatching {
                AdManager.awaitSplashComplete(this@SplashActivity)
            }.onFailure {
                Log.w(LOG_TAG, "Splash ad flow failed, continuing to main", it)
            }
            goToMain()
        }
    }

    private fun goToMain() {
        if (isFinishing || isDestroyed) {
            return
        }
        val intent = Intent(this@SplashActivity, MainActivity::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        // Trì hoãn finish để đợi animation hoàn tất
        window.decorView.postDelayed({
            finish() // Finish sau animation
        }, 300) // delay khoảng 300ms (hoặc đúng thời gian của animation)
    }

    companion object {
        private var isAdInitialized = false
    }
}
