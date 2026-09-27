package com.mckimquyen.watermark.data.repo

import android.graphics.RectF
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * IDEA-14: [WaterMarkRepository.updateImageRedaction] — mirror [WaterMarkRepositoryUpdateImageCropRoboTest]
 * cho phần list, CỘNG THÊM bất biến quan trọng nhất phát hiện qua smoke test thật: phải emit
 * [WaterMarkRepository.selectedImage] khi ảnh vừa sửa ĐANG được chọn — khác [updateImageCrop] (đã
 * có cùng thiếu sót, không sửa ở đây vì ngoài phạm vi ticket), thiếu dòng này khiến
 * `WaterMarkImageView` không bao giờ nhận được vùng redaction mới: editor vẫn hiện ảnh CHƯA che dù
 * user đã bấm Apply — sai âm thầm, không lỗi, không crash.
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkRepositoryUpdateImageRedactionRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var repo: WaterMarkRepository

    @Before
    fun setUp() {
        repo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
    }

    private fun images(vararg names: String) = names.map { ImageInfo(Uri.parse("content://media/$it")) }

    @Test
    fun updateImageRedaction_setsOnlyMatchingUri() = runBlocking {
        repo.updateImageList(images("a", "b", "c"))
        val rects = listOf(RectF(0.1f, 0.1f, 0.5f, 0.5f))

        repo.updateImageRedaction(Uri.parse("content://media/b"), rects)

        val list = repo.imageInfoMapFlow.first()
        assertThat(list[0].redactionRectsNormalized).isNull()
        assertThat(list[1].redactionRectsNormalized).isEqualTo(rects)
        assertThat(list[2].redactionRectsNormalized).isNull()
    }

    @Test
    fun updateImageRedaction_preservesOtherFieldsOfSameImage() = runBlocking {
        repo.updateImageList(images("a"))
        val uri = Uri.parse("content://media/a")
        repo.toggleSkipExport(uri)

        repo.updateImageRedaction(uri, listOf(RectF(0f, 0f, 0.3f, 0.3f)))

        val info = repo.imageInfoMapFlow.first().first()
        assertThat(info.isSkippedInExport).isTrue()
    }

    @Test
    fun updateImageRedaction_unknownUri_leavesListUnchanged() = runBlocking {
        repo.updateImageList(images("a", "b"))

        repo.updateImageRedaction(Uri.parse("content://media/does-not-exist"), listOf(RectF(0f, 0f, 1f, 1f)))

        val list = repo.imageInfoMapFlow.first()
        assertThat(list.map { it.redactionRectsNormalized }).containsExactly(null, null).inOrder()
    }

    @Test
    fun updateImageRedaction_khiAnhDangDuocChon_phaiEmitSelectedImageMoi() = runBlocking {
        repo.updateImageList(images("a", "b"))
        val uri = Uri.parse("content://media/a")
        repo.select(uri)
        val rects = listOf(RectF(0.2f, 0.2f, 0.6f, 0.6f))

        repo.updateImageRedaction(uri, rects)

        // Đây là bất biến cốt lõi: editor (WaterMarkImageView) chỉ nghe qua `selectedImage`, không
        // nghe `imageInfoMapFlow` — thiếu emit này thì Apply xong mà preview vẫn hiện ảnh cũ.
        assertThat(repo.selectedImage.value.redactionRectsNormalized).isEqualTo(rects)
    }

    @Test
    fun updateImageRedaction_khiAnhKHONGDuocChon_khongDungToiSelectedImageAnhKhac() = runBlocking {
        repo.updateImageList(images("a", "b"))
        repo.select(Uri.parse("content://media/a"))
        val untouchedSelected = repo.selectedImage.value

        // Sửa redaction cho ảnh "b" trong khi "a" đang được chọn — selectedImage phải GIỮ NGUYÊN.
        repo.updateImageRedaction(Uri.parse("content://media/b"), listOf(RectF(0f, 0f, 0.5f, 0.5f)))

        assertThat(repo.selectedImage.value).isEqualTo(untouchedSelected)
        assertThat(repo.selectedImage.value.redactionRectsNormalized).isNull()
    }
}
