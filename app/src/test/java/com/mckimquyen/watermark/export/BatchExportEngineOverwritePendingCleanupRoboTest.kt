package com.mckimquyen.watermark.export

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ConflictPolicy
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.ViewInfo
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P1 review pass 8: OVERWRITE ghi `IS_PENDING=1` lên row ẢNH CŨ đã có sẵn (existingUri) TRƯỚC khi
 * ghi file mới — nếu bước ghi ném `Throwable` không phải `Exception` (`OutOfMemoryError` khi
 * compress ảnh lớn — không bị `catch (e: Exception)` cục bộ bắt) thoát ra ngoài, trước đây KHÔNG
 * nơi nào dọn lại `IS_PENDING=0` -> ảnh GỐC của user biến mất khỏi mọi app gallery vĩnh viễn.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class BatchExportEngineOverwritePendingCleanupRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val existingUri: Uri = Uri.parse("content://media/external/images/media/4242")

    @Test
    fun generateImage_overwriteThrowsErrorDuringWrite_clearsIsPendingOnExistingRow() {
        val waterMarkRepo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
        val exportNaming = spyk(ExportNaming())
        every { exportNaming.queryExistingMediaUri(any(), any(), any()) } returns existingUri
        val engine = BatchExportEngine(context, exportNaming)

        val resolverSpy = spyk(context.contentResolver)
        every {
            resolverSpy.openFileDescriptor(existingUri, "w", null)
        } throws OutOfMemoryError("forced test failure - not caught by local catch(Exception)")

        val imageInfo = ImageInfo(Uri.parse("content://media/overwrite_pending_test.jpg"))
        val settings = runBlocking {
            BatchExportEngine.ExportSettings(
                config = waterMarkRepo.waterMark.first(),
                outputFormat = Bitmap.CompressFormat.JPEG,
                compressLevel = 90,
                maxOutputLongEdge = 0,
                copyright = "",
                outputNamePattern = "{filename}",
                conflictPolicy = ConflictPolicy.OVERWRITE
            )
        }
        val viewInfo = ViewInfo(
            width = 100,
            height = 100,
            paddingLeft = 0,
            paddingTop = 0,
            paddingRight = 0,
            paddingBottom = 0,
            scaleType = ImageView.ScaleType.FIT_CENTER,
            matrix = Matrix()
        )

        var caught: Throwable? = null
        runBlocking {
            try {
                engine.generateImage(resolverSpy, viewInfo, imageInfo, index = 0, settings = settings)
            } catch (e: OutOfMemoryError) {
                caught = e
            }
        }

        assertThat(caught).isNotNull()
        // Đúng hành vi cần có: IS_PENDING phải được dọn về 0 trên row CŨ dù write bị lỗi giữa chừng.
        io.mockk.verify {
            resolverSpy.update(
                existingUri,
                match { it.getAsInteger(MediaStore.Images.Media.IS_PENDING) == 0 },
                null,
                null
            )
        }
    }
}
