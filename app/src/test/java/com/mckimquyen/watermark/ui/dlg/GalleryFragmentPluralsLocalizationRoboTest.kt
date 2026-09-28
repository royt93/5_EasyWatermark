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
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import com.mckimquyen.watermark.ui.MainViewModel
import com.mckimquyen.watermark.ui.adapter.GalleryAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Regression test cho bug thật phát hiện 2026-09-27: `<plurals name="gallery_select_photo_count">`
 * (và 2 plurals khác cùng file) hoàn toàn KHÔNG được dịch ở 12/13 locale sau đợt bổ sung 272+ key
 * dịch — vì `<plurals>` dùng tag XML khác `<string>`/`<string-array>` nên cả unit test parity lẫn
 * agent audit ban đầu đều bỏ sót, chỉ Android Lint (`MissingTranslation`) bắt được.
 *
 * Test set locale runtime bằng `@Config(qualifiers)` — cách chuẩn của Robolectric để giả lập thiết
 * bị đổi ngôn ngữ hệ thống mà không cần base config khác — rồi verify chuỗi hiển thị trên
 * `GalleryFragment` KHÔNG rơi về tiếng Anh (bằng chứng bản dịch thật được nạp, không phải fallback
 * im lặng).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru")
class GalleryFragmentPluralsLocalizationRoboTest {

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
        Locale.setDefault(Locale("ru"))
        runBlocking {
            waterMarkDataStore.edit { it.clear() }
        }
        testViewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = WaterMarkRepository(context, waterMarkDataStore),
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
    }

    private fun launchFragment(): GalleryFragment {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).setup().get()
        val containerId = FrameLayout(activity).let {
            it.id = android.view.View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        val fragment = GalleryFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction().add(containerId, fragment, "gallery").commit()
        shadowOf(Looper.getMainLooper()).idle()
        return fragment
    }

    @Test
    fun russianLocale_galleryPluralsAreTranslated_notEnglishFallback() {
        val fragment = launchFragment()
        val adapter = fragment.binding.rvContent.adapter as GalleryAdapter

        adapter.selectedCount.value = 3
        shadowOf(Looper.getMainLooper()).idle()

        val fabText = fragment.binding.fab.text.toString()
        val hintText = fragment.binding.tvSelectionHint?.text.toString()

        // Bằng chứng bản dịch tiếng Nga THẬT được nạp — nếu <plurals> vẫn thiếu, hệ thống resource
        // fallback về values/strings.xml (tiếng Anh: "Select 3 photos" / "3 selected").
        assertThat(fabText).contains("Выбрать")
        assertThat(hintText).contains("выбрано")
        assertThat(fabText).doesNotContain("Select")
        assertThat(hintText).doesNotContain("selected")
    }

    @Test
    fun russianLocale_batchCaptionSubtitle_isTranslated_notEnglishFallback() {
        val subtitle = context.resources.getQuantityString(
            com.mckimquyen.watermark.R.plurals.batch_caption_subtitle,
            2,
            2
        )
        assertThat(subtitle).contains("изображени")
        assertThat(subtitle).doesNotContain("image")
    }
}
