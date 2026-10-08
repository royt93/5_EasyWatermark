package com.mckimquyen.watermark.ads

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.BuildConfig
import com.mckimquyen.watermark.feature.vip.VipKeys
import com.roy.sdkadbmob.AdManager
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration (SDK thật trên thiết bị, build debug = ID test của Google nên KHÔNG tạo request ad thật):
 * chứng minh cấu hình ad + VIP đúng theo doc AD_PROMPT_AOS.MD ở runtime, không chỉ ở BuildConfig.
 * KHÔNG gọi hàm show hay load ad nào → không phát sinh impression nào.
 */
@RunWith(AndroidJUnit4::class)
class AdConfigIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        AdManager.clearVipByKey()
    }

    @After
    fun tearDown() {
        AdManager.clearVipByKey()
        AdManager.suppressAppOpenTemporarily(false)
    }

    @Test
    fun debugBuild_usesGoogleSampleAdUnits_neverRealIds() {
        assertThat(BuildConfig.DEBUG).isTrue()
        // Ad unit test của Google: publisher id 3940256099942544. ID thật là 3004713799155145.
        listOf(
            BuildConfig.ADMOB_BANNER_ID,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            BuildConfig.ADMOB_APP_OPEN_ID,
            BuildConfig.ADMOB_REWARDED_ID
        ).forEach {
            assertThat(it).startsWith("ca-app-pub-3940256099942544/")
            assertThat(it).doesNotContain("3004713799155145")
        }
    }

    @Test
    fun testDeviceHashes_registeredAndWellFormed() {
        val ids = BuildConfig.ADMOB_TEST_DEVICE_IDS.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        assertThat(ids).isNotEmpty()
        assertThat(ids).containsNoDuplicates()
        ids.forEach { assertThat(it).matches("[0-9A-F]{32}") }
    }

    @Test
    fun grantVipDays_activatesVip_andClearRevokes_withoutLegacyKey() {
        assertThat(AdManager.isVipByKeyActive()).isFalse()

        assertThat(AdManager.grantVipDays(context, 3)).isTrue()
        assertThat(AdManager.isVipByKeyActive()).isTrue()
        assertThat(AdManager.getVipByKeyExpiry()).isGreaterThan(System.currentTimeMillis())

        AdManager.clearVipByKey()
        assertThat(AdManager.isVipByKeyActive()).isFalse()
    }

    @Test
    fun grantVipDays_rejectsOutOfRangeDays() {
        assertThat(AdManager.grantVipDays(context, 0)).isFalse()
        assertThat(AdManager.grantVipDays(context, -1)).isFalse()
        assertThat(AdManager.isVipByKeyActive()).isFalse()
    }

    @Test
    fun grantVipDays_stacksOnExistingExpiry() {
        AdManager.grantVipDays(context, 3)
        val first = AdManager.getVipByKeyExpiry()

        AdManager.grantVipDays(context, 3)

        assertThat(AdManager.getVipByKeyExpiry()).isGreaterThan(first)
    }

    @Test
    fun legacyPlaintextVipSecret_isRejected_soApkSecretAloneCannotUnlockVip() {
        // vipKeySecret nằm trong APK; nhánh legacy tắt mặc định nên gõ đúng secret KHÔNG được lên VIP.
        val ok = AdManager.activateVipByKey(context, BuildConfig.VIP_KEY_SECRET, 30)

        assertThat(ok).isFalse()
        assertThat(AdManager.isVipByKeyActive()).isFalse()
    }

    @Test
    fun wrongRedeemCode_isRejected() {
        assertThat(AdManager.activateVipByKey(context, "definitely-not-a-code", 30)).isFalse()
        assertThat(AdManager.isVipByKeyActive()).isFalse()
    }

    @Test
    fun redeemCodes_matchConfiguredCards() {
        val codes = VipKeys.redeemCodes()
        assertThat(codes.values).containsExactly(30, 3)
        assertThat(codes.keys).containsNoDuplicates()
    }

    @Test
    fun suppressAppOpenTemporarily_isTogglableOnRealSdk_withoutCrash() {
        AdManager.suppressAppOpenTemporarily(true)
        AdManager.suppressAppOpenTemporarily(false)
        AdManager.suppressAppOpenTemporarily(true)
        AdManager.suppressAppOpenTemporarily(false)
    }

    @Test
    fun privacyDiagnostics_exposesConsentSignals() {
        val d = AdManager.getPrivacyDiagnostics()

        assertThat(d.rawConsentStatus).isNotEmpty()
        assertThat(d.privacyOptionsRequirement).isNotEmpty()
    }
}
