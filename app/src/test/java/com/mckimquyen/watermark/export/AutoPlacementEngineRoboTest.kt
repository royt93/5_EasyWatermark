package com.mckimquyen.watermark.export

import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import com.mckimquyen.watermark.utils.facedetection.FaceDetectionSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * IDEA-01: [AutoPlacementEngine] — dùng [FakeFaceDetectionSource] thay ML Kit thật (native, không
 * mô phỏng tin cậy được trong Robolectric — xem [FaceDetectionSource]). Robolectric shadow
 * `BitmapFactory` decode BẤT KỲ uri nào thành bitmap giả 100x100 (cùng ghi chú ở
 * `BatchExportEnginePreviewRoboTest`), nên mọi ảnh test dùng uri giả `content://media/N.jpg`.
 */
@RunWith(RobolectricTestRunner::class)
class AutoPlacementEngineRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private class FakeFaceDetectionSource(
        private val behavior: (callIndex: Int, bitmap: Bitmap) -> List<RectF>
    ) : FaceDetectionSource {
        var callCount = 0
            private set

        override suspend fun detectFaces(bitmap: Bitmap): List<RectF> {
            val index = callCount
            callCount++
            return behavior(index, bitmap)
        }
    }

    private fun defaultConfig() = runBlocking {
        WaterMarkRepository(context, newTestWaterMarkDataStore(context)).waterMark.first()
    }

    @Test
    fun suggestPlacements_faceCoversWholeImage_writesOffsetAndSwitchesToClamp() = runBlocking {
        val fake = FakeFaceDetectionSource { _, _ -> listOf(RectF(0f, 0f, 1f, 1f)) }
        val engine = AutoPlacementEngine(context, fake, ExportNaming())
        val original = ImageInfo(Uri.parse("content://media/1.jpg"))

        val result = engine.suggestPlacements(context.contentResolver, listOf(original), defaultConfig()) { _, _ -> }

        val updated = result.single()
        assertThat(updated.detectedFaceRectsNormalized).hasSize(1)
        assertThat(updated.obtainTileMode()).isEqualTo(Shader.TileMode.CLAMP)
        // Full-image face -> mọi anchor đều giao, nhưng vẫn phải chọn 1 (fallback đỡ tệ nhất) nên
        // offset chắc chắn khác mặc định 0.5/0.5 (đã bị Anchor.toOffset trừ margin/kích thước watermark).
        assertThat(updated.offsetX == 0.5f && updated.offsetY == 0.5f).isFalse()
    }

    @Test
    fun suggestPlacements_alreadyCachedDetection_doesNotCallDetectorAgain() = runBlocking {
        val fake = FakeFaceDetectionSource { _, _ -> listOf(RectF(0f, 0f, 1f, 1f)) }
        val engine = AutoPlacementEngine(context, fake, ExportNaming())
        val cached = ImageInfo(
            Uri.parse("content://media/1.jpg"),
            detectedFaceRectsNormalized = listOf(RectF(0f, 0f, 1f, 1f))
        )

        engine.suggestPlacements(context.contentResolver, listOf(cached), defaultConfig()) { _, _ -> }

        assertThat(fake.callCount).isEqualTo(0)
    }

    @Test
    fun suggestPlacements_noFacesDetected_leavesOffsetUnchanged_butCachesEmptyResult() = runBlocking {
        val fake = FakeFaceDetectionSource { _, _ -> emptyList() }
        val engine = AutoPlacementEngine(context, fake, ExportNaming())
        val original = ImageInfo(Uri.parse("content://media/1.jpg"))

        val result = engine.suggestPlacements(context.contentResolver, listOf(original), defaultConfig()) { _, _ -> }

        val updated = result.single()
        assertThat(updated.offsetX).isEqualTo(0.5f)
        assertThat(updated.offsetY).isEqualTo(0.5f)
        assertThat(updated.detectedFaceRectsNormalized).isEmpty()
    }

    @Test
    fun suggestPlacements_skippedImage_neverCallsDetector_leftUntouched() = runBlocking {
        val fake = FakeFaceDetectionSource { _, _ -> listOf(RectF(0f, 0f, 1f, 1f)) }
        val engine = AutoPlacementEngine(context, fake, ExportNaming())
        val skipped = ImageInfo(Uri.parse("content://media/1.jpg"), isSkippedInExport = true)

        val result = engine.suggestPlacements(context.contentResolver, listOf(skipped), defaultConfig()) { _, _ -> }

        assertThat(result.single()).isSameInstanceAs(skipped)
        assertThat(fake.callCount).isEqualTo(0)
    }

    @Test
    fun suggestPlacements_detectorThrowsForOneImage_doesNotFailBatch_otherImageStillProcessed() = runBlocking {
        val fake = FakeFaceDetectionSource { index, _ ->
            if (index == 0) throw RuntimeException("boom") else listOf(RectF(0f, 0f, 1f, 1f))
        }
        val engine = AutoPlacementEngine(context, fake, ExportNaming())
        val infoList = listOf(
            ImageInfo(Uri.parse("content://media/1.jpg")),
            ImageInfo(Uri.parse("content://media/2.jpg"))
        )

        val result = engine.suggestPlacements(context.contentResolver, infoList, defaultConfig()) { _, _ -> }

        assertThat(result).hasSize(2)
        // Ảnh 1 lỗi detect -> giữ nguyên y hệt (không cache gì, không đổi offset).
        assertThat(result[0]).isEqualTo(infoList[0])
        // Ảnh 2 vẫn xử lý bình thường dù ảnh 1 lỗi trước đó -> batch không dừng giữa chừng.
        assertThat(result[1].detectedFaceRectsNormalized).hasSize(1)
    }

    @Test
    fun suggestPlacements_progressCallback_reportsEveryImageIncludingSkipped() = runBlocking {
        val fake = FakeFaceDetectionSource { _, _ -> emptyList() }
        val engine = AutoPlacementEngine(context, fake, ExportNaming())
        val infoList = listOf(
            ImageInfo(Uri.parse("content://media/1.jpg")),
            ImageInfo(Uri.parse("content://media/2.jpg"), isSkippedInExport = true)
        )
        val progressCalls = mutableListOf<Pair<Int, Int>>()

        engine.suggestPlacements(context.contentResolver, infoList, defaultConfig()) { done, total ->
            progressCalls.add(done to total)
        }

        assertThat(progressCalls).containsExactly(1 to 2, 2 to 2).inOrder()
    }
}
