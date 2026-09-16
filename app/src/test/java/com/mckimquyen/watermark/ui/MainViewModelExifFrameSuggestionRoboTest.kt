package com.mckimquyen.watermark.ui

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ExifFrameStyle
import com.mckimquyen.watermark.data.model.ExifModel
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * FEAT-10: [MainViewModel.suggestExifFrameStyleIfNeeded] gợi ý style khung theo hãng máy đọc từ
 * EXIF của ảnh đang chọn — chỉ khi user CHƯA từng tự tay đổi style cho đúng ảnh đó
 * ([MainViewModel.selectExifFrameStyle] đánh dấu "đã chọn tay").
 */
@RunWith(RobolectricTestRunner::class)
class MainViewModelExifFrameSuggestionRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val waterMarkDataStore = newTestWaterMarkDataStore(context)
    private val userDataStore = newTestUserDataStore(context)
    private lateinit var waterMarkRepo: WaterMarkRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        runBlocking {
            waterMarkDataStore.edit { it.clear() }
        }
        waterMarkRepo = WaterMarkRepository(context, waterMarkDataStore)
        viewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = waterMarkRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
        // waterMark/selectedImage là LiveData.asLiveData() từ Flow — chỉ bắt đầu collect khi có
        // observer (LiveData chỉ ACTIVE khi có observer), nếu không .value giữ null vĩnh viễn dù
        // repo đã update xong.
        viewModel.waterMark.observeForever {}
        viewModel.selectedImage.observeForever {}
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun selectImageWithMake(make: String): Uri {
        val uri = Uri.parse("content://media/${make.hashCode()}")
        val info = ImageInfo(uri, exifModel = ExifModel(make = make))
        runBlocking {
            waterMarkRepo.updateImageList(listOf(info))
            waterMarkRepo.select(uri)
        }
        // selectedImage đi qua StateFlow.asLiveData(), poll thay vì 1 lần idle() cho chắc.
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (viewModel.selectedImage.value?.uri == uri) break
            Thread.sleep(20)
        }
        return uri
    }

    /**
     * `waterMark` (DataStore-backed) cần thời gian I/O thật để 1 lần `edit{}` lan tới Flow đang
     * collect — poll thay vì 1 lần `idle()` để tránh đọc giá trị cũ (ghi liên tiếp nhanh trong
     * cùng test, giống pattern `awaitJobFinished` đã dùng ở ENH-01).
     */
    private fun awaitExifFrameStyle(expected: ExifFrameStyle, timeoutMs: Long = 3_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (viewModel.waterMark.value?.exifFrameStyle == expected.ordinal) return
            Thread.sleep(20)
        }
    }

    @Test
    fun suggestExifFrameStyleIfNeeded_fujifilmImage_appliesClassic() {
        selectImageWithMake("FUJIFILM")

        viewModel.suggestExifFrameStyleIfNeeded()
        awaitExifFrameStyle(ExifFrameStyle.CLASSIC)

        assertThat(viewModel.waterMark.value?.exifFrameStyle).isEqualTo(ExifFrameStyle.CLASSIC.ordinal)
    }

    @Test
    fun suggestExifFrameStyleIfNeeded_unknownMakeImage_appliesMinimal() {
        selectImageWithMake("")

        viewModel.suggestExifFrameStyleIfNeeded()
        awaitExifFrameStyle(ExifFrameStyle.MINIMAL)

        assertThat(viewModel.waterMark.value?.exifFrameStyle).isEqualTo(ExifFrameStyle.MINIMAL.ordinal)
    }

    /** AC: gợi ý "vẫn đổi tay được" — sau khi user tự chọn, gợi ý không được đè lên cho ĐÚNG ảnh đó nữa. */
    @Test
    fun suggestExifFrameStyleIfNeeded_afterManualSelection_doesNotOverride() {
        selectImageWithMake("FUJIFILM")
        viewModel.suggestExifFrameStyleIfNeeded()
        awaitExifFrameStyle(ExifFrameStyle.CLASSIC)
        assertThat(viewModel.waterMark.value?.exifFrameStyle).isEqualTo(ExifFrameStyle.CLASSIC.ordinal)

        // User tự tay đổi sang Polaroid.
        viewModel.selectExifFrameStyle(ExifFrameStyle.POLAROID)
        awaitExifFrameStyle(ExifFrameStyle.POLAROID)
        assertThat(viewModel.waterMark.value?.exifFrameStyle).isEqualTo(ExifFrameStyle.POLAROID.ordinal)

        // Mở lại ExifPbFragment cho ĐÚNG ảnh này (vd xoay màn hình) — không được gợi ý đè lại Classic.
        viewModel.suggestExifFrameStyleIfNeeded()
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(viewModel.waterMark.value?.exifFrameStyle).isEqualTo(ExifFrameStyle.POLAROID.ordinal)
    }

    /** Ảnh KHÁC (chưa từng chọn tay) vẫn phải được gợi ý bình thường dù ảnh trước đó đã chọn tay. */
    @Test
    fun suggestExifFrameStyleIfNeeded_differentImage_stillGetsSuggestion() {
        selectImageWithMake("FUJIFILM")
        viewModel.selectExifFrameStyle(ExifFrameStyle.POLAROID)
        awaitExifFrameStyle(ExifFrameStyle.POLAROID)

        selectImageWithMake("Canon")
        viewModel.suggestExifFrameStyleIfNeeded()
        awaitExifFrameStyle(ExifFrameStyle.FILM_STRIP)

        assertThat(viewModel.waterMark.value?.exifFrameStyle).isEqualTo(ExifFrameStyle.FILM_STRIP.ordinal)
    }
}
