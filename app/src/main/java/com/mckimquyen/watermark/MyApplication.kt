package com.mckimquyen.watermark

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mckimquyen.cmonet.CMonet
import com.mckimquyen.watermark.common.const.AdKeys
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.data.repo.WatermarkProfileRepository
import com.mckimquyen.watermark.utils.QrCodeGenerator
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.AdSafetyLimits
import com.roy.sdkadbmob.AdSdkConfig
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
class MyApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var waterMarkRepo: WaterMarkRepository

    @Inject
    lateinit var profileRepo: WatermarkProfileRepository

    /** BUG-64: IO thật cho copy file + Room; SupervisorJob để lỗi migrate không kéo sập scope. */
    private val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ENH-01: BatchExportWorker là @HiltWorker (@AssistedInject) — cần HiltWorkerFactory để
    // WorkManager tạo Worker qua Hilt (inject WaterMarkRepository/UserConfigRepository/engine)
    // thay vì reflection constructor rỗng mặc định. Đã gỡ WorkManagerInitializer mặc định trong
    // AndroidManifest.xml (tools:node="remove") để override bằng workManagerConfiguration này.
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private val sp by lazy { getSharedPreferences(SP_NAME, MODE_PRIVATE) }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        catchException()
    }

    override fun onCreate() {
        super.onCreate()
        setupAdmob()
        // BUG-41: trước đây gọi DynamicColors.applyToActivitiesIfAvailable(this) VÔ ĐIỀU KIỆN ở
        // đây, RỒI CMonet.init(this, true) bên dưới lại tự gọi lại đúng hàm này lần 2 (đăng ký
        // trùng ActivityLifecycleCallbacks) — vế gọi trần này còn bỏ qua hẳn lựa chọn user (switch
        // "Dynamic Color" ở About), khiến tắt switch không có tác dụng. Bỏ hẳn, chỉ còn 1 đường
        // gọi qua CMonet.init() (tôn trọng CMonet.isDynamicColorAvailable() = thiết bị VÀ user).
        // BUG-23: CMonet.init() KHÔNG được đặt trong nhánh else của checkRecoveryMode() — khi app
        // đang recovery mode, mọi màn hình đọc màu theme qua ContextExtension.kt (colorPrimary/
        // colorSecondary/... — ~30 điểm gọi CMonet.isDynamicColorAvailable()) sẽ crash
        // UninitializedPropertyAccessException ngay khi vẽ UI đầu tiên, vô hiệu hoá luôn cơ chế
        // graceful-recovery. init() không có side-effect nguy hiểm liên quan tới nguyên nhân
        // crash đang điều tra — gọi vô điều kiện, không phụ thuộc recovery mode.
        CMonet.init(this, true)
        if (checkRecoveryMode()) {
            return
        } else {
            applicationScope.launch {
                waterMarkRepo.resetModeToText()
            }
            migrateLegacyQrUris()
        }
    }

    /**
     * BUG-64: QR cũ nằm trong cacheDir nhưng URI đã lưu bền (DataStore/MRU/profile). Mỗi lần khởi động: còn
     * file thì copy sang filesDir, mất file thì bỏ URI chết. Idempotent — sau lần đầu không còn URI khớp
     * tiền tố cũ nên chỉ là đọc rồi ghi lại giá trị y nguyên.
     * ponytail: chạy mỗi lần mở app, đổi thành cờ một-lần khi số profile lớn đến mức tốn thời gian.
     */
    private fun migrateLegacyQrUris() {
        // Một URI thường xuất hiện đồng thời ở icon hiện tại + MRU + nhiều profile. Cache kết quả trong
        // lần migrate này để chỉ copy file đúng một lần và mọi record cùng trỏ tới MỘT URI mới.
        val promoted = mutableMapOf<android.net.Uri, android.net.Uri?>()
        val promote: (android.net.Uri) -> android.net.Uri? = {
            promoted.getOrPut(it) { QrCodeGenerator.promoteLegacyCacheUri(this, it) }
        }
        migrationScope.launch {
            runCatching { waterMarkRepo.rewriteIconUris(promote) }
                .onFailure { AppLog.w("MyApplication", "BUG-64 migrate DataStore failed: $it") }
            runCatching { profileRepo.rewriteIconUris(promote) }
                .onFailure { AppLog.w("MyApplication", "BUG-64 migrate profile failed: $it") }
        }
    }

    private fun setupAdmob() {
        val adConfig = AdSdkConfig(
            isEnableAdmob = BuildConfig.IS_ENABLE_ADMOB,
            isDebug = BuildConfig.DEBUG,
            admobBannerId = BuildConfig.ADMOB_BANNER_ID,
            admobInterstitialId = BuildConfig.ADMOB_INTERSTITIAL_ID,
            admobAppOpenId = BuildConfig.ADMOB_APP_OPEN_ID,
            admobRewardedId = BuildConfig.ADMOB_REWARDED_ID,
            applovinBannerId = BuildConfig.APPLOVIN_BANNER_ID,
            applovinInterstitialId = BuildConfig.APPLOVIN_INTERSTITIAL_ID,
            applovinAppOpenId = BuildConfig.APPLOVIN_APP_OPEN_ID,
            applovinRewardedId = BuildConfig.APPLOVIN_REWARDED_ID,
            applovinSdkKey = BuildConfig.APPLOVIN_SDK_KEY,
            vipKeySecret = AdKeys.VIP_SECRET_30_DAYS,
            // Public key ECDSA verify VIP token (private key giữ offline trong myKeyStore, KHÔNG vào app).
            vipTokenPublicKey = BuildConfig.VIP_TOKEN_PUBLIC_KEY,
            // DEBUG: limits gần như tắt để test thoải mái. RELEASE: preset CONTENT (balanced)
            // — 60s gap, 6/session, 3/hour, 10/day — an toàn policy mà vẫn giữ doanh thu.
            // (CONTENT == AdSafetyLimits() default; ghi rõ tên cho khỏi nhầm.)
            safety = if (BuildConfig.DEBUG) AdSafetyLimits.TEST else AdSafetyLimits.CONTENT
        )

        AdManager.setConfig(adConfig)
        // Hash máy test: cả debug lẫn release (invalid traffic = rủi ro khoá tài khoản). Gọi SAU setConfig
        // (provider đã tạo) và TRƯỚC initialize ở Splash. SDK >=1.6.x lưu lại callerTestDeviceIds, gộp với
        // QC_TEST_DEVICE_HASHES nên init bất đồng bộ không ghi đè. Hash CHỈ THÊM, không xoá (myKeyStore).
        val testDeviceIds = BuildConfig.ADMOB_TEST_DEVICE_IDS.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        AdManager.setTestDeviceIds(*testDeviceIds.toTypedArray())
        AdManager.earlyInit(this)
        AppLog.d("MyApplication") { "AdManager config ready; provider init waits for splash consent" }
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
            // Intent chỉ chứa tối đa ~1MB dữ liệu, nên giới hạn độ dài stack trace trước khi nhét vào Intent.
            val maxStringLength = MAX_CRASH_STACK_TRACE_LENGTH
            var fullStackTrace = Log.getStackTraceString(e)
            if (fullStackTrace.length > maxStringLength) {
                fullStackTrace = fullStackTrace.substring(0, maxStringLength)
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

        var recoveryMode = false
            private set

        private const val CRASH_COUNT = 2

        /**
         * Giới hạn độ dài stack trace lưu vào Intent khi crash.
         * Intent chỉ chứa được ~1MB; lấy 1/2 cho an toàn rồi chia 10 để chừa chỗ cho dữ liệu khác.
         */
        private const val MAX_CRASH_STACK_TRACE_LENGTH = 1024 * 1024 / 2 / 10

        const val SP_NAME = "sp_water_mark_crash_info"

        const val KEY_IS_CRASH = SP_NAME + "_key_is_crash"
        const val KEY_STACK_TRACE = SP_NAME + "_key_stack_trace"
        const val SP_KEY_CRASH_COUNT = SP_NAME + "_key_crash_count"
        const val SP_KEY_RECOVERY_VERSION = SP_NAME + "_key_recovery_version"
    }
}
