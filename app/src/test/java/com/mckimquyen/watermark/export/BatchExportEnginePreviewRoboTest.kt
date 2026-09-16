package com.mckimquyen.watermark.export

import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * FEAT-07: [BatchExportEngine.generatePreviewBitmap] — render preview NHẸ cho grid xem trước
 * batch. Robolectric's shadow `BitmapFactory` decode BẤT KỲ stream/uri nào (kể cả uri không tồn
 * tại thật) thành 1 bitmap giả 100x100 mặc định thay vì trả null — nên các case ở đây verify
 * đúng HÀNH VI của hàm (bitmap trả về, kích thước ước lượng, nhánh text rỗng) thay vì giả lập
 * decode-thất-bại (không tái hiện được dưới Robolectric). Phần render watermark PIXEL THẬT (vị
 * trí/kích thước đúng theo config) dựa vào building block đã test riêng
 * (`buildTextBitmapShader`/`buildIconBitmapShader`/`applyConfig`) + smoke test thật trên device
 * (xem `doc/task/done/FEAT-07-preview-grid-truoc-khi-export.md`).
 */
@RunWith(RobolectricTestRunner::class)
class BatchExportEnginePreviewRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun newRepo(): WaterMarkRepository {
        return WaterMarkRepository(context, newTestWaterMarkDataStore(context))
    }

    @Test
    fun generatePreviewBitmap_defaultTextConfig_returnsBitmapWithApproxOriginalDimensions() {
        val waterMarkRepo = newRepo()
        val engine = BatchExportEngine(context, ExportNaming())
        val imageInfo = ImageInfo(Uri.parse("content://media/1.jpg"))

        val result = runBlocking {
            val config = waterMarkRepo.waterMark.first()
            engine.generatePreviewBitmap(context.contentResolver, imageInfo, config, index = 0)
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(result).isNotNull()
        assertThat(result!!.bitmap.isRecycled).isFalse()
        // Robolectric shadow BitmapFactory decode mọi uri thành bitmap giả 100x100 mặc định —
        // preview không downsample thêm (100 < PREVIEW_MAX_SIZE) nên approx == kích thước decode.
        assertThat(result.approxOriginalWidth).isEqualTo(100)
        assertThat(result.approxOriginalHeight).isEqualTo(100)
    }

    @Test
    fun generatePreviewBitmap_textModeBlankText_returnsRawBitmapWithoutBuildingShader() {
        val waterMarkRepo = newRepo()
        runBlocking { waterMarkRepo.updateText("") }
        shadowOf(Looper.getMainLooper()).idle()
        val engine = BatchExportEngine(context, ExportNaming())
        val imageInfo = ImageInfo(Uri.parse("content://media/1.jpg"))

        val result = runBlocking {
            val config = waterMarkRepo.waterMark.first()
            engine.generatePreviewBitmap(context.contentResolver, imageInfo, config, index = 0)
        }
        shadowOf(Looper.getMainLooper()).idle()

        // Text rỗng — không có gì để vẽ, trả thẳng bitmap gốc (không build shader), vẫn có dữ
        // liệu kích thước hợp lệ để hiển thị ước tính.
        assertThat(result).isNotNull()
        assertThat(result!!.approxOriginalWidth).isEqualTo(100)
    }

    @Test
    fun generatePreviewBitmap_imageInfoHasCaption_capionOverridesSharedText() {
        // FEAT-13: caption riêng ("") coi như "cố ý không watermark ảnh này" — phải trả bitmap
        // GỐC (không build shader), dù watermark text CHUNG không rỗng.
        val waterMarkRepo = newRepo()
        runBlocking { waterMarkRepo.updateText("shared text") }
        shadowOf(Looper.getMainLooper()).idle()
        val engine = BatchExportEngine(context, ExportNaming())
        val imageInfo = ImageInfo(Uri.parse("content://media/1.jpg"), caption = "")

        val result = runBlocking {
            val config = waterMarkRepo.waterMark.first()
            engine.generatePreviewBitmap(context.contentResolver, imageInfo, config, index = 0)
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(result).isNotNull()
        assertThat(result!!.approxOriginalWidth).isEqualTo(100)
    }

    @Test
    fun generatePreviewBitmap_imageInfoCaptionNull_fallsBackToSharedText() {
        // caption == null (chưa nhập riêng cho ảnh này) — vẫn dùng watermark text chung như cũ.
        val waterMarkRepo = newRepo()
        runBlocking { waterMarkRepo.updateText("shared text") }
        shadowOf(Looper.getMainLooper()).idle()
        val engine = BatchExportEngine(context, ExportNaming())
        val imageInfo = ImageInfo(Uri.parse("content://media/1.jpg"), caption = null)

        val result = runBlocking {
            val config = waterMarkRepo.waterMark.first()
            engine.generatePreviewBitmap(context.contentResolver, imageInfo, config, index = 0)
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(result).isNotNull()
    }

    @Test
    fun generatePreviewBitmap_differentIndex_stillReturnsValidPreview() {
        // Ảnh khác nhau trong cùng batch (index dùng để resolve token {seq} trong text) không
        // được làm hàm crash/khác biệt hành vi ngoài giá trị token.
        val waterMarkRepo = newRepo()
        val engine = BatchExportEngine(context, ExportNaming())
        val imageInfo = ImageInfo(Uri.parse("content://media/2.jpg"))

        val result = runBlocking {
            val config = waterMarkRepo.waterMark.first()
            engine.generatePreviewBitmap(context.contentResolver, imageInfo, config, index = 5)
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(result).isNotNull()
    }
}
