package com.mckimquyen.watermark.feature.vip

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.roy.sdkadbmob.AdManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class VipTokenSmokeActivityRoboTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val noBackup: File get() = context.noBackupFilesDir.apply { mkdirs() }

    @Before
    fun setUp() {
        mockkObject(AdManager)
        every { AdManager.isVipTokenPending } returns false
        every { AdManager.isVipTokenResolutionInFlight } returns false
        every { AdManager.isVipByKeyActive() } returns false
        clearHandoff()
    }

    @After
    fun tearDown() {
        clearHandoff()
        unmockkObject(AdManager)
    }

    @Test
    fun mint_whileSdkHasPendingToken_isBlockedAndNeverTouchesKeyFile() {
        every { AdManager.isVipTokenPending } returns true
        val keyFile = File(noBackup, "vip-smoke-private-key").apply { writeText(UUID.randomUUID().toString()) }
        val activity = Robolectric.buildActivity(VipTokenSmokeActivity::class.java).setup().get()

        activity.findViewById<android.widget.Button>(R.id.btnMintToken).performClick()

        verify(exactly = 0) { AdManager.generateVipToken(any(), any(), any(), any(), any(), any(), any()) }
        assertThat(keyFile.exists()).isTrue()
        assertThat(statusOf(activity)).contains("Blocked")
    }

    @Test
    fun mint_withoutKeyHandoff_reportsNothingChangedAndNeverSigns() {
        val activity = Robolectric.buildActivity(VipTokenSmokeActivity::class.java).setup().get()

        activity.findViewById<android.widget.Button>(R.id.btnMintToken).performClick()

        verify(exactly = 0) { AdManager.generateVipToken(any(), any(), any(), any(), any(), any(), any()) }
        assertThat(statusOf(activity)).contains("no new token was made")
        assertThat(File(noBackup, "vip-smoke-token").exists()).isFalse()
    }

    @Test
    fun mint_success_deletesKeyAndStatusNeverContainsKeyOrToken() {
        val opaqueKey = UUID.randomUUID().toString()
        val opaqueToken = UUID.randomUUID().toString()
        every { AdManager.generateVipToken(any(), any(), any(), any(), any(), any(), any()) } returns opaqueToken
        File(noBackup, "vip-smoke-private-key").writeText(opaqueKey)
        val activity = Robolectric.buildActivity(VipTokenSmokeActivity::class.java).setup().get()

        activity.findViewById<android.widget.Button>(R.id.btnMintToken).performClick()

        assertThat(File(noBackup, "vip-smoke-private-key").exists()).isFalse()
        assertThat(File(noBackup, "vip-smoke-token").exists()).isTrue()
        // So sánh bằng contains() trên chuỗi, không in giá trị để tránh rò rỉ vào log test.
        assertThat(statusOf(activity).contains(opaqueKey)).isFalse()
        assertThat(statusOf(activity).contains(opaqueToken)).isFalse()
    }

    @Test
    fun mint_signerFailure_stillDeletesKeyAndArmsNothing() {
        every { AdManager.generateVipToken(any(), any(), any(), any(), any(), any(), any()) } throws IllegalStateException("boom")
        File(noBackup, "vip-smoke-private-key").writeText(UUID.randomUUID().toString())
        val activity = Robolectric.buildActivity(VipTokenSmokeActivity::class.java).setup().get()

        activity.findViewById<android.widget.Button>(R.id.btnMintToken).performClick()

        assertThat(File(noBackup, "vip-smoke-private-key").exists()).isFalse()
        assertThat(File(noBackup, "vip-smoke-token").exists()).isFalse()
        assertThat(File(noBackup, "vip-smoke-armed").exists()).isFalse()
    }

    @Test
    fun mint_signerThrowsError_showsFailedStatusInsteadOfCrashingAndDeletesKey() {
        every { AdManager.generateVipToken(any(), any(), any(), any(), any(), any(), any()) } throws OutOfMemoryError("simulated")
        File(noBackup, "vip-smoke-private-key").writeText(UUID.randomUUID().toString())
        val activity = Robolectric.buildActivity(VipTokenSmokeActivity::class.java).setup().get()

        activity.findViewById<android.widget.Button>(R.id.btnMintToken).performClick()

        assertThat(statusOf(activity)).contains("Token generation failed")
        assertThat(File(noBackup, "vip-smoke-private-key").exists()).isFalse()
    }

    @Test
    fun status_whenVipActive_doesNotNameAnyPhysicalDevice() {
        every { AdManager.isVipByKeyActive() } returns true
        val activity = Robolectric.buildActivity(VipTokenSmokeActivity::class.java).setup().get()

        assertThat(statusOf(activity)).doesNotContain("Pixel")
        assertThat(statusOf(activity)).doesNotContain("TECNO")
    }

    @Test
    fun edgeToEdge_systemBarAndCutoutInsetsArePaddedOnTopOfBasePadding() {
        val activity = Robolectric.buildActivity(VipTokenSmokeActivity::class.java).setup().get()
        val root = activity.findViewById<android.view.View>(R.id.tvSmokeStatus).parent as android.view.View
        val base = root.paddingLeft
        val insets = androidx.core.view.WindowInsetsCompat.Builder()
            .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(), androidx.core.graphics.Insets.of(0, TOP_INSET, 0, BOTTOM_INSET))
            .setInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout(), androidx.core.graphics.Insets.of(CUTOUT_SIDE, 0, 0, 0))
            .build()

        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(root, insets)

        assertThat(root.paddingTop).isEqualTo(base + TOP_INSET)
        assertThat(root.paddingBottom).isEqualTo(base + BOTTOM_INSET)
        assertThat(root.paddingLeft).isEqualTo(base + CUTOUT_SIDE)
    }

    private companion object {
        const val TOP_INSET = 90
        const val BOTTOM_INSET = 48
        const val CUTOUT_SIDE = 24
    }

    private fun statusOf(activity: VipTokenSmokeActivity): String =
        activity.findViewById<android.widget.TextView>(R.id.tvSmokeStatus).text.toString()

    private fun clearHandoff() {
        listOf("vip-smoke-private-key", "vip-smoke-token", "vip-smoke-armed").forEach { File(noBackup, it).delete() }
    }
}
