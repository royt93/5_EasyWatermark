package com.mckimquyen.watermark.export

import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import com.mckimquyen.watermark.ui.widget.WaterMarkImageView
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * P1 review pass 8: `generatePreviewBitmap`/`generateCompareBitmaps` tạo bitmap tạm riêng
 * (`mutableBitmap`/`originalCopy`+`watermarkedCopy`, bản `copy()` KHÔNG qua `BitmapCache`) rồi vẽ
 * watermark lên đó — nếu 1 bước giữa chừng (build shader/tính auto-contrast) ném exception, nhánh
 * `catch (e: Exception)` chỉ `bitmapValue.release()` (bitmap GỐC trong cache), KHÔNG recycle bitmap
 * tạm -> rò rỉ 1-2 bitmap mỗi lần render lỗi giữa batch.
 *
 * Ép lỗi bằng cách mock [WaterMarkImageView.resolveAutoContrast] (nhận thẳng bitmap đang xử lý
 * làm tham số — capture được đúng instance cần kiểm tra `isRecycled`), không cần giả lập decode
 * thất bại (Robolectric luôn decode ra bitmap giả, không tái hiện được decode-lỗi thật).
 */
@RunWith(RobolectricTestRunner::class)
class BatchExportEngineBitmapLeakOnExceptionRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun tearDown() {
        unmockkObject(WaterMarkImageView)
    }

    private fun forceRenderException(): io.mockk.CapturingSlot<Bitmap> {
        mockkObject(WaterMarkImageView)
        val captured = slot<Bitmap>()
        coEvery {
            WaterMarkImageView.resolveAutoContrast(
                bitmap = capture(captured),
                tileMode = any(),
                offsetX = any(),
                offsetY = any(),
                textSizeInBitmapPx = any(),
                config = any()
            )
        } throws RuntimeException("forced test failure")
        return captured
    }

    @Test
    fun generatePreviewBitmap_exceptionMidRender_recyclesTempBitmapCopy() {
        val captured = forceRenderException()
        val waterMarkRepo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
        val engine = BatchExportEngine(context, ExportNaming())
        val imageInfo = ImageInfo(Uri.parse("content://media/leak_test_preview.jpg"))

        val result = runBlocking {
            val config = waterMarkRepo.waterMark.first()
            engine.generatePreviewBitmap(context.contentResolver, imageInfo, config, index = 0)
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(result).isInstanceOf(BatchExportEngine.PreviewResult.DecodeFailure::class.java)
        assertThat(captured.isCaptured).isTrue()
        assertThat(captured.captured.isRecycled).isTrue()
    }

    @Test
    fun generateCompareBitmaps_exceptionMidRender_recyclesBothTempCopies() {
        val captured = forceRenderException()
        val waterMarkRepo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
        val engine = BatchExportEngine(context, ExportNaming())
        val imageInfo = ImageInfo(Uri.parse("content://media/leak_test_compare.jpg"))

        val result = runBlocking {
            val config = waterMarkRepo.waterMark.first()
            engine.generateCompareBitmaps(context.contentResolver, imageInfo, config, index = 0)
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(result).isNull()
        assertThat(captured.isCaptured).isTrue()
        // Chỉ verify được watermarkedCopy (tham số resolveAutoContrast capture được instance thật).
        // originalCopy không có handle nào lộ ra ngoài để assert riêng — áp cùng pattern fix hệt
        // watermarkedCopy trong code, tin cậy qua code review + symmetry thay vì test riêng.
        assertThat(captured.captured.isRecycled).isTrue()
    }
}
