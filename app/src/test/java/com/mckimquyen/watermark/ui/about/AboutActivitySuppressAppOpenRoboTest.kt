package com.mckimquyen.watermark.ui.about

import android.os.Build
import android.view.View
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.utils.AppOpenSuppressor
import com.roy.sdkadbmob.AdManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Mở SAF (backup / restore / verify) từ About phải chặn App Open tự-resume, nếu không user vừa chọn file xong
 * quay về sẽ bị App Open bật ngay (doc: `suppressAppOpenTemporarily` quanh Dialog/Activity hệ thống).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class AboutActivitySuppressAppOpenRoboTest {

    private val calls = mutableListOf<Boolean>()
    private val original = AppOpenSuppressor.setSuppressed

    @Before
    fun setUp() {
        calls.clear()
        AppOpenSuppressor.setSuppressed = { calls.add(it) }
        mockkObject(AdManager)
        every { AdManager.loadBanner(any(), any(), any(), any(), any()) } returns null
    }

    @After
    fun tearDown() {
        AppOpenSuppressor.setSuppressed = original
        unmockkObject(AdManager)
    }

    private fun click(id: Int) {
        val activity = Robolectric.buildActivity(AboutActivity::class.java).setup().get()
        calls.clear()
        activity.findViewById<View>(id).performClick()
    }

    @Test
    fun backup_suppressesAppOpenBeforeLaunchingPicker() {
        click(R.id.tvBackupData)
        assertThat(calls).contains(true)
    }

    @Test
    fun restore_suppressesAppOpenBeforeLaunchingPicker() {
        click(R.id.tvRestoreData)
        assertThat(calls).contains(true)
    }

    @Test
    fun verifyAuthenticity_suppressesAppOpenBeforeLaunchingPicker() {
        click(R.id.tvVerifyAuthenticity)
        assertThat(calls).contains(true)
    }

    @Test
    fun nonPickerRow_doesNotSuppress() {
        click(R.id.rowOpenSource)
        assertThat(calls).doesNotContain(true)
    }

    @Test
    fun returningToAbout_clearsSuppressFlag_soItDoesNotStayStuck() {
        val controller = Robolectric.buildActivity(AboutActivity::class.java).setup()
        controller.get().findViewById<View>(R.id.tvBackupData).performClick()
        assertThat(calls.last()).isTrue()

        controller.pause().resume()

        assertThat(calls.last()).isFalse()
    }
}
