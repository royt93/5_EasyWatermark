package com.mckimquyen.watermark.ui.dlg

import android.content.Context
import android.os.Looper
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.entity.Template
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import com.mckimquyen.watermark.testutil.noopWatermarkStyleHistoryRepository
import com.mckimquyen.watermark.ui.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.Date

/**
 * BUG-24: `EditTemplateContentFragment.safetyShow()` ép kiểu `manager.findFragmentByTag(TAG) as?
 * SaveImageBSDialogFragment` (copy-paste sai lớp) — luôn trả null dù đã có fragment cùng tag, nên
 * guard chống trùng ("if (!f.isAdded) show()") luôn tạo instance MỚI + show() lại, bất kể fragment
 * cũ đang hiển thị. Test gọi `safetyShow()` 2 lần liên tiếp (mô phỏng bấm nhanh 2 lần) — với bug
 * cũ sẽ có 2 instance `EditTemplateContentFragment` cùng tồn tại trong `FragmentManager`; sau fix
 * chỉ còn đúng 1.
 */
@RunWith(RobolectricTestRunner::class)
class EditTemplateContentFragmentSafetyShowRoboTest {

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
    private val waterMarkDataStore = newTestWaterMarkDataStore(context)
    private val userDataStore = newTestUserDataStore(context)

    @Before
    fun setUp() {
        runBlocking { waterMarkDataStore.edit { it.clear() } }
        testViewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = WaterMarkRepository(context, waterMarkDataStore),
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null),
            styleHistoryRepo = noopWatermarkStyleHistoryRepository()
        )
    }

    @Test
    fun safetyShow_calledTwiceRapidly_onlyOneFragmentInstanceTracked() {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).setup().get()
        val manager = activity.supportFragmentManager

        EditTemplateContentFragment.safetyShow(manager)
        shadowOf(Looper.getMainLooper()).idle()
        // Gọi lần 2 NGAY (mô phỏng bấm nhanh 2 lần) — trước fix, cast sai lớp khiến findFragmentByTag
        // luôn trả null, tạo + show() thêm 1 instance mới dù instance đầu vẫn đang hiển thị.
        EditTemplateContentFragment.safetyShow(manager)
        shadowOf(Looper.getMainLooper()).idle()

        val matching = manager.fragments.filterIsInstance<EditTemplateContentFragment>()
        assertThat(matching).hasSize(1)
    }

    /**
     * BUG-AUDIT-2026-09-29: `safetyShow()` gọi lại trên instance đã `isAdded` (double-tap 2 template
     * KHÁC nhau liên tiếp) trước đây set `arguments` trên fragment đang active — ném
     * `IllegalStateException("Fragment already active")` bị `catch` nuốt âm thầm, dialog giữ nguyên
     * nội dung của template ĐẦU TIÊN. Sau fix, `updateTemplateForReuse()` cập nhật field + UI trực
     * tiếp, không đụng `arguments`.
     */
    @Test
    fun safetyShow_calledTwiceWithDifferentTemplates_showsSecondTemplateContent_notStale() {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).setup().get()
        val manager = activity.supportFragmentManager
        val t1 = Template(id = 1, content = "Template A", creationDate = Date(1_000L), lastModifiedDate = null)
        val t2 = Template(id = 2, content = "Template B", creationDate = Date(2_000L), lastModifiedDate = null)

        EditTemplateContentFragment.safetyShow(manager, t1)
        shadowOf(Looper.getMainLooper()).idle()
        EditTemplateContentFragment.safetyShow(manager, t2)
        shadowOf(Looper.getMainLooper()).idle()

        val matching = manager.fragments.filterIsInstance<EditTemplateContentFragment>()
        assertThat(matching).hasSize(1)
        assertThat(matching.single().binding.etWaterText.text.toString()).isEqualTo("Template B")
    }
}
