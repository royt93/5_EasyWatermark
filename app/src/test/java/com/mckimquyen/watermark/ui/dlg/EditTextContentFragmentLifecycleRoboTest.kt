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
import com.mckimquyen.watermark.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * BUG-20: `EditTextContentFragment` phải collect `shareViewModel.uiStateFlow` bằng
 * `viewLifecycleOwner.lifecycleScope`/`.lifecycle` (không phải `lifecycleScope`/`this.lifecycle`
 * của Fragment) — nếu không, collector sống theo vòng đời Fragment instance (không bị huỷ khi
 * View bị huỷ qua back stack), tích luỹ qua mỗi lần `onViewCreated` chạy lại. Cùng họ lỗi với
 * BUG-10 (`GalleryFragmentLifecycleRoboTest`), test theo đúng pattern đó: `detach()` huỷ View
 * nhưng giữ Fragment instance sống, rồi kiểm tra `uiState` (`MutableStateFlow` nội bộ, truy cập
 * qua reflection vì `uiStateFlow` public chỉ là `StateFlow` bọc `asStateFlow()`) không còn
 * subscriber nào.
 */
@RunWith(RobolectricTestRunner::class)
class EditTextContentFragmentLifecycleRoboTest {

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
    private lateinit var viewModel: MainViewModel

    private fun uiStateMutableFlow(): MutableStateFlow<UiState> {
        val field = MainViewModel::class.java.getDeclaredField("uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(viewModel) as MutableStateFlow<UiState>
    }

    @Before
    fun setUp() {
        runBlocking {
            context.waterMarkDataStore.edit { it.clear() }
        }
        viewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(context.userDataStore),
            waterMarkRepo = WaterMarkRepository(context, context.waterMarkDataStore),
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
        testViewModel = viewModel
    }

    @Test
    fun viewDestroyedViaBackStack_unregistersUiStateCollector_fragmentInstanceStillAlive() {
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

        assertThat(fragment.isAdded).isTrue()
        assertThat(uiStateMutableFlow().subscriptionCount.value).isAtLeast(1)

        // detach() huỷ View (onDestroyView) nhưng giữ nguyên Fragment instance — cùng kịch bản
        // BUG-10 dùng để tái hiện lỗi collector/observer sống ngoài vòng đời View.
        activity.supportFragmentManager.beginTransaction()
            .detach(fragment)
            .commit()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(activity.supportFragmentManager.findFragmentByTag("editText")).isSameInstanceAs(fragment)
        assertThat(fragment.isDetached).isTrue()
        assertThat(fragment.view).isNull()
        assertThat(uiStateMutableFlow().subscriptionCount.value)
            .isEqualTo(0) // FIX: viewLifecycleOwner.lifecycleScope -> huỷ collector ngay khi View huỷ
    }
}
