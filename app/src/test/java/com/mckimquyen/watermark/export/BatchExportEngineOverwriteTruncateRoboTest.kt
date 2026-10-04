package com.mckimquyen.watermark.export

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ConflictPolicy
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.ViewInfo
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import com.mckimquyen.watermark.utils.bitmap.BitmapRecycleGuard
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/**
 * BUG-60: từ Android 10 (Q), mode `"w"` của `openFileDescriptor`/`openOutputStream` KHÔNG đảm bảo
 * truncate file có sẵn — chính sách OVERWRITE tái dùng file cũ nên phải dùng `"wt"`, nếu không ảnh
 * cũ lớn hơn để lại đuôi rác sau dữ liệu ảnh mới (file phình, WebP lệch độ dài RIFF).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class BatchExportEngineOverwriteTruncateRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun settings(policy: ConflictPolicy) = runBlocking {
        BatchExportEngine.ExportSettings(
            config = WaterMarkRepository(context, newTestWaterMarkDataStore(context)).waterMark.first(),
            outputFormat = Bitmap.CompressFormat.PNG,
            compressLevel = 100,
            maxOutputLongEdge = 0,
            copyright = "",
            outputNamePattern = "{filename}",
            conflictPolicy = policy
        )
    }

    @Test
    fun writeIntoDocumentTree_overwrite_opensOutputStreamInTruncateMode() {
        val engine = BatchExportEngine(context, ExportNaming())
        val existingUri = Uri.parse("content://tree/existing.png")
        val existing = mockk<DocumentFile> { every { uri } returns existingUri }
        val root = mockk<DocumentFile> { every { findFile("photo.png") } returns existing }
        val resolver = mockk<ContentResolver>()
        val modeSlot = slot<String>()
        every { resolver.openOutputStream(existingUri, capture(modeSlot)) } returns ByteArrayOutputStream()
        // overload 1-arg mặc định "w" — không được dùng cho OVERWRITE
        every { resolver.openOutputStream(existingUri) } returns ByteArrayOutputStream()
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

        engine.writeIntoDocumentTree(root, resolver, bitmap, "photo.png", settings(ConflictPolicy.OVERWRITE), BitmapRecycleGuard(bitmap))

        verify(exactly = 1) { resolver.openOutputStream(existingUri, "wt") }
        verify(exactly = 0) { resolver.openOutputStream(existingUri) }
        assertThat(modeSlot.captured).isEqualTo("wt")
    }

    @Test
    fun generateImage_mediaStoreOverwrite_opensFileDescriptorInTruncateMode() {
        val existingUri = Uri.parse("content://media/external/images/media/4242")
        val exportNaming = spyk(ExportNaming())
        every { exportNaming.queryExistingMediaUri(any(), any(), any()) } returns existingUri
        val engine = BatchExportEngine(context, exportNaming)
        val resolverSpy = spyk(context.contentResolver)
        every { resolverSpy.openFileDescriptor(existingUri, any(), any()) } returns null

        val viewInfo = ViewInfo(100, 100, 0, 0, 0, 0, ImageView.ScaleType.FIT_CENTER, Matrix())
        runBlocking {
            engine.generateImage(
                resolverSpy,
                viewInfo,
                ImageInfo(Uri.parse("content://media/overwrite_truncate_test.jpg")),
                index = 0,
                settings = settings(ConflictPolicy.OVERWRITE)
            )
        }

        verify { resolverSpy.openFileDescriptor(existingUri, "wt", null) }
        verify(exactly = 0) { resolverSpy.openFileDescriptor(existingUri, "w", null) }
    }
}
