package com.mckimquyen.watermark.ui.dlg

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import com.mckimquyen.watermark.testutil.noopWatermarkStyleHistoryRepository
import com.mckimquyen.watermark.ui.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * BUG-69: switch EXIF/Card frame dùng `if (buttonView.isPressed)` để phân biệt người dùng với set từ
 * code. Hành động accessibility (TalkBack/Switch Access) gọi `performClick()` và KHÔNG đặt `isPressed`
 * nên toggle không bao giờ chạy trong khi nhóm tuỳ chỉnh lại hiện ra (`isVisible = isChecked` nằm
 * ngoài guard) → UI lệch state với config.
 */
@RunWith(RobolectricTestRunner::class)
class SwitchAccessibilityClickRoboTest {

    companion object {
        lateinit var testViewModel: MainViewModel
        const val WAIT_TIMEOUT_MS = 5_000L
        const val WAIT_STEP_MS = 20L
    }

    class TestHostActivity : FragmentActivity() {
        override val defaultViewModelProviderFactory: ViewModelProvider.Factory
            get() = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = testViewModel as T
            }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val waterMarkDataStore = newTestWaterMarkDataStore(context)
    private val userDataStore = newTestUserDataStore(context)
    private lateinit var repo: WaterMarkRepository

    @Before
    fun setUp() {
        runBlocking {
            waterMarkDataStore.edit { it.clear() }
            userDataStore.edit { it.clear() }
        }
        repo = WaterMarkRepository(context, waterMarkDataStore)
        testViewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = repo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null),
            styleHistoryRepo = noopWatermarkStyleHistoryRepository()
        )
    }

    private fun idleUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(WAIT_STEP_MS)
        }
    }

    private fun <F : androidx.fragment.app.Fragment> launch(fragment: F): F {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).setup().get()
        testViewModel.waterMark.observe(activity) {}
        val containerId = FrameLayout(activity).let {
            it.id = View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        activity.supportFragmentManager.beginTransaction().add(containerId, fragment, "t").commit()
        idleUntil { testViewModel.waterMark.value != null }
        shadowOf(Looper.getMainLooper()).idle()
        return fragment
    }

    @Test
    fun cardFrame_accessibilityClick_togglesConfig() {
        val f = launch(CardFramePbFragment().apply { setShowsDialog(false) })

        f.binding.swCardFrame.performClick() // TalkBack: không đặt isPressed

        idleUntil { runBlocking { repo.waterMark.first().cardFrameEnabled } }
        assertThat(runBlocking { repo.waterMark.first().cardFrameEnabled }).isTrue()
    }

    @Test
    fun exif_accessibilityClick_togglesConfig() {
        val f = launch(ExifPbFragment().apply { setShowsDialog(false) })

        f.binding.swExif.performClick()

        idleUntil { runBlocking { repo.waterMark.first().enableExif } }
        assertThat(runBlocking { repo.waterMark.first().enableExif }).isTrue()
    }

    @Test
    fun exif_accessibilityClickOnSerifAndAutoPalette_updatesConfig() {
        val f = launch(ExifPbFragment().apply { setShowsDialog(false) })

        f.binding.swExifAutoPalette.performClick()
        idleUntil { runBlocking { repo.waterMark.first().exifAutoPalette } }
        assertThat(runBlocking { repo.waterMark.first().exifAutoPalette }).isTrue()

        val before = runBlocking { repo.waterMark.first().exifUseSerifCaption }
        f.binding.swExifSerifCaption.performClick()
        idleUntil { runBlocking { repo.waterMark.first().exifUseSerifCaption } != before }
        assertThat(runBlocking { repo.waterMark.first().exifUseSerifCaption }).isNotEqualTo(before)
    }

    @Test
    fun programmaticSyncFromRepo_doesNotWriteBack_noLoop() {
        runBlocking { repo.updateCardFrameEnabled(true) }
        val f = launch(CardFramePbFragment().apply { setShowsDialog(false) })
        idleUntil { f.binding.swCardFrame.isChecked }
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(WAIT_STEP_MS * 5)

        // Observer chỉ đồng bộ UI theo repo — config phải GIỮ NGUYÊN true, không bị toggle ngược thành false.
        assertThat(f.binding.swCardFrame.isChecked).isTrue()
        assertThat(runBlocking { repo.waterMark.first().cardFrameEnabled }).isTrue()
    }
}
