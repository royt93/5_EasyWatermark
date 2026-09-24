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
 * FEAT-16: [WaterMarkRepository.updateImageCrop] chỉ đổi `cropRect`/`rotationDegrees` của ĐÚNG 1
 * ảnh (theo uri), không đụng ảnh khác, giữ nguyên state khác của ảnh đó (mirror
 * [WaterMarkRepositorySkipExportRoboTest] cho [WaterMarkRepository.toggleSkipExport]).
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkRepositoryUpdateImageCropRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var repo: WaterMarkRepository

    @Before
    fun setUp() {
        repo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
    }

    private fun images(vararg names: String) = names.map { ImageInfo(Uri.parse("content://media/$it")) }

    @Test
    fun updateImageCrop_setsOnlyMatchingUri() = runBlocking {
        repo.updateImageList(images("a", "b", "c"))
        val cropRect = RectF(0.1f, 0.1f, 0.9f, 0.9f)

        repo.updateImageCrop(Uri.parse("content://media/b"), cropRect, 12f)

        val list = repo.imageInfoMapFlow.first()
        assertThat(list[0].cropRect).isNull()
        assertThat(list[0].rotationDegrees).isEqualTo(0f)
        assertThat(list[1].cropRect).isEqualTo(cropRect)
        assertThat(list[1].rotationDegrees).isEqualTo(12f)
        assertThat(list[2].cropRect).isNull()
        assertThat(list[2].rotationDegrees).isEqualTo(0f)
    }

    @Test
    fun updateImageCrop_nullCropRect_clearsCrop_keepsRotation() = runBlocking {
        repo.updateImageList(images("a"))
        val uri = Uri.parse("content://media/a")
        repo.updateImageCrop(uri, RectF(0f, 0f, 1f, 1f), 5f)

        repo.updateImageCrop(uri, null, 5f)

        val info = repo.imageInfoMapFlow.first().first()
        assertThat(info.cropRect).isNull()
        assertThat(info.rotationDegrees).isEqualTo(5f)
    }

    @Test
    fun updateImageCrop_preservesOtherFieldsOfSameImage() = runBlocking {
        repo.updateImageList(images("a"))
        val uri = Uri.parse("content://media/a")
        repo.toggleSkipExport(uri)

        repo.updateImageCrop(uri, RectF(0f, 0f, 0.5f, 0.5f), 7f)

        val info = repo.imageInfoMapFlow.first().first()
        assertThat(info.isSkippedInExport).isTrue()
        assertThat(info.rotationDegrees).isEqualTo(7f)
    }

    @Test
    fun updateImageCrop_unknownUri_leavesListUnchanged() = runBlocking {
        repo.updateImageList(images("a", "b"))

        repo.updateImageCrop(Uri.parse("content://media/does-not-exist"), RectF(0f, 0f, 1f, 1f), 3f)

        val list = repo.imageInfoMapFlow.first()
        assertThat(list.map { it.cropRect }).containsExactly(null, null).inOrder()
    }
}
