package com.mckimquyen.watermark.ui.dlg

import android.content.Context
import android.os.Looper
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
import com.mckimquyen.watermark.di.userDataStore
import com.mckimquyen.watermark.di.waterMarkDataStore
import com.mckimquyen.watermark.ui.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

/**
 * ENH-02: gõ liên tục vào ô text watermark không được ghi DataStore mỗi ký tự (mỗi lần ghi
 * là 1 lần `DataStore.edit` thật xuống đĩa) — phải debounce 200ms, chỉ ghi thật sau khi dừng gõ.
 * Cùng kỹ thuật launch `TestHostActivity` với `defaultViewModelProviderFactory` giả (bypass Hilt)
 * đã dùng ở `EditTextContentFragmentLifecycleRoboTest` (BUG-20).
 */
@RunWith(RobolectricTestRunner::class)
class EditTextContentFragmentDebounceRoboTest {

    companion object {
        lateinit var testViewModel: MainViewModel
    }

    class TestHostActivity : FragmentActivity() {
        override val defaultViewModelProviderFactory: ViewModelProvider.Factory
            get() = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = testViewModel as T
            }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var waterMarkRepo: WaterMarkRepository

    @Before
    fun setUp() {
        runBlocking {
            context.waterMarkDataStore.edit { it.clear() }
        }
        waterMarkRepo = WaterMarkRepository(context, context.waterMarkDataStore)
        testViewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(context.userDataStore),
            waterMarkRepo = waterMarkRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
    }

    private fun launchFragment(): EditTextContentFragment {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).setup().get()
        val containerId = FrameLayout(activity).let {
            it.id = android.view.View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        val fragment = EditTextContentFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(containerId, fragment, "editText")
            .commit()
        shadowOf(Looper.getMainLooper()).idle()
        return fragment
    }

    private fun persistedText(): String = runBlocking { waterMarkRepo.waterMark.first().text }

    /**
     * `DataStore.edit()` serializes ghi qua 1 actor nội bộ chạy trên dispatcher thật riêng của
     * nó — không được `shadowOf(Looper).idleFor()` (chỉ bơm Main looper) điều khiển, giống hệt
     * pattern generate bitmap thật trên `Dispatchers.Default` ở `QrCodeBottomSheetFragmentRoboTest`
     * — phải poll ngắn bằng thời gian thật thay vì assert ngay sau idleFor.
     */
    private fun awaitPersistedText(expected: String) {
        val deadline = System.currentTimeMillis() + 5_000
        while (persistedText() != expected && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Test
    fun typingRapidly_doesNotWriteDataStore_untilDebounceElapses() {
        val fragment = launchFragment()

        fragment.binding?.etWaterText?.setText("h")
        shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
        fragment.binding?.etWaterText?.setText("he")
        shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
        fragment.binding?.etWaterText?.setText("hello")
        // Mới gõ xong, chưa qua 200ms debounce kể từ ký tự cuối -> chưa ghi DataStore.
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)

        assertThat(persistedText()).isNotEqualTo("hello")
    }

    @Test
    fun typingThenPausing_writesOnlyFinalValue_afterDebounceElapses() {
        val fragment = launchFragment()

        fragment.binding?.etWaterText?.setText("h")
        shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
        fragment.binding?.etWaterText?.setText("he")
        shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
        fragment.binding?.etWaterText?.setText("hello")
        // Dừng gõ, đợi qua hẳn 200ms debounce kể từ ký tự cuối.
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
        awaitPersistedText("hello")

        assertThat(persistedText()).isEqualTo("hello")
    }

    @Test
    fun viewDestroyedRightAfterTyping_stillFlushesLatestValue_beforeDebounceElapses() {
        val fragment = launchFragment()

        fragment.binding?.etWaterText?.setText("urgent")
        // Rời màn hình ngay lập tức, KHÔNG đợi debounce (mô phỏng back nhanh) — flush trong
        // onDestroyView phải đảm bảo giá trị cuối vẫn được ghi, không mất ký tự.
        fragment.parentFragmentManager.beginTransaction().remove(fragment).commit()
        shadowOf(Looper.getMainLooper()).idle()
        awaitPersistedText("urgent")

        assertThat(persistedText()).isEqualTo("urgent")
    }
}
