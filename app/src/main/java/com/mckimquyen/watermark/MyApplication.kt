package com.mckimquyen.watermark

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import com.google.android.gms.ads.MobileAds
import com.mckimquyen.cmonet.CMonet
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.AdSdkConfig
import com.applovin.sdk.AppLovinSdk
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.system.exitProcess

//TODO firebase
//TODO share app

//done
//review in app bingo
//font scale
//120hz
//pkg name
//proguard
//ic launcher
//ad id, internet permission in manifest
//leak canary
//rate app
//more app
//policy
//keystore
//ad applovin

@HiltAndroidApp
class MyApplication : Application() {

    @Inject
    lateinit var waterMarkRepo: WaterMarkRepository

    private val sp by lazy { getSharedPreferences(SP_NAME, MODE_PRIVATE) }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        instance = this
        catchException()
    }

    override fun onCreate() {
        super.onCreate()
        setupAdmob()
        if (checkRecoveryMode()) {
            return
        } else {
            applicationScope.launch {
                waterMarkRepo.resetModeToText()
            }
            CMonet.init(this, true)
        }
    }

    private fun setupAdmob() {
        val adConfig = AdSdkConfig(
            isEnableAdmob = BuildConfig.IS_ENABLE_ADMOB,
            isDebug = BuildConfig.DEBUG,
            admobBannerId = BuildConfig.ADMOB_BANNER_ID,
            admobInterstitialId = BuildConfig.ADMOB_INTERSTITIAL_ID,
            admobAppOpenId = BuildConfig.ADMOB_APP_OPEN_ID,
            applovinBannerId = BuildConfig.APPLOVIN_BANNER_ID,
            applovinInterstitialId = BuildConfig.APPLOVIN_INTERSTITIAL_ID,
            applovinAppOpenId = BuildConfig.APPLOVIN_APP_OPEN_ID
        )

        AdManager.setConfig(adConfig)
        AdManager.earlyInit(this)

        if (BuildConfig.IS_ENABLE_ADMOB) {
            Log.d("MyApplication", "AdMob mode, initializing MobileAds")
            MobileAds.initialize(this) { _ ->
                AdManager.init(this, adConfig) { success, gaid ->
                    Log.d("MyApplication", "AdManager init success=$success, gaid=$gaid")
                    if (success) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            AdManager.registerAppOpenAdLifecycle(this)
                        }
                    }
                }
            }
        } else {
            Log.d("MyApplication", "AppLovin mode, initializing AppLovinSdk")
            val sdk = AppLovinSdk.getInstance(this)
            sdk.mediationProvider = "max"
            sdk.initializeSdk {
                AdManager.init(this, adConfig) { success, gaid ->
                    Log.d("MyApplication", "AdManager init success=$success, gaid=$gaid")
                    if (success) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            AdManager.registerAppOpenAdLifecycle(this)
                        }
                    }
                }
            }
        }
    }

    private fun checkRecoveryMode(): Boolean {
        val crashCount = sp.getInt(SP_KEY_CRASH_COUNT, 0)
        if (crashCount < CRASH_COUNT) {
            return false
        }
        val recoveryVersion = sp.getInt(SP_KEY_RECOVERY_VERSION, BuildConfig.VERSION_CODE - 1)
        if (recoveryVersion < BuildConfig.VERSION_CODE) {
            // maybe we fixed in this version
            recoveryMode = false
            sp.edit {
                putInt(SP_KEY_CRASH_COUNT, 0)
                putInt(SP_KEY_RECOVERY_VERSION, 0)
            }
            return false
        }
        recoveryMode = true
        sp.edit {
            putInt(SP_KEY_RECOVERY_VERSION, BuildConfig.VERSION_CODE)
        }
        return true
    }

    fun launchSuccess() {
        recoveryMode = false
        val sp = getSharedPreferences(SP_NAME, MODE_PRIVATE)
        sp.edit {
            putInt(SP_KEY_CRASH_COUNT, 0)
        }
    }

    private fun catchException() {
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            // Because intent limit data to 1mb, so that we should limit the stack track by magic number below
//            Log.e("MyApp", "uncaughtException")
            val maxStringLength = 1024 * 1024 / 2 / 10 // the 10 is a magic number ;)
            var fullStackTrace = Log.getStackTraceString(e)
            if (fullStackTrace.length > maxStringLength) {
                fullStackTrace = fullStackTrace.substring(IntRange(0, maxStringLength))
            }
//            Log.e("MyApp", "uncaughtException: $fullStackTrace")
            sp.edit(true) {
                putInt(SP_KEY_CRASH_COUNT, sp.getInt(SP_KEY_CRASH_COUNT, 0) + 1)
                putInt(SP_KEY_RECOVERY_VERSION, BuildConfig.VERSION_CODE)
                putBoolean(KEY_IS_CRASH, true)
                putString(
                    /* p0 = */ KEY_STACK_TRACE,
                    /* p1 = */ """
                    Crash in ${t.name}:
                    $fullStackTrace
                    """.trimIndent()
                )
            }
            with(Intent(Intent.ACTION_MAIN)) {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                this@MyApplication.startActivity(this)
            }
            e.printStackTrace()
            exitProcess(0)
        }
    }

    companion object {

        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        @SuppressLint("StaticFieldLeak")
        lateinit var instance: Context
            private set

        var recoveryMode = false
            private set

        private const val CRASH_COUNT = 2

        const val SP_NAME = "sp_water_mark_crash_info"

        const val KEY_IS_CRASH = SP_NAME + "_key_is_crash"
        const val KEY_STACK_TRACE = SP_NAME + "_key_stack_trace"
        const val SP_KEY_CRASH_COUNT = SP_NAME + "_key_crash_count"
        const val SP_KEY_RECOVERY_VERSION = SP_NAME + "_key_recovery_version"
    }
}
