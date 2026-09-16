package com.mckimquyen.watermark.data.repo

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
 * FEAT-13: [WaterMarkRepository.updateImageCaptions] gán caption riêng theo ĐÚNG thứ tự hiện tại
 * của [WaterMarkRepository.imageInfoList] — index i trong danh sách captions ứng với ảnh thứ i.
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkRepositoryCaptionRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var repo: WaterMarkRepository

    @Before
    fun setUp() {
        repo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
    }

    private fun images(vararg names: String) = names.map { ImageInfo(Uri.parse("content://media/$it")) }

    @Test
    fun updateImageCaptions_matchingCount_assignsByIndex() = runBlocking {
        repo.updateImageList(images("a", "b", "c"))

        repo.updateImageCaptions(listOf("Cap A", "Cap B", "Cap C"))

        val list = repo.imageInfoMapFlow.first()
        assertThat(list.map { it.caption }).containsExactly("Cap A", "Cap B", "Cap C").inOrder()
    }

    @Test
    fun updateImageCaptions_shorterThanImageList_leavesExtraImagesNull() = runBlocking {
        repo.updateImageList(images("a", "b", "c"))

        repo.updateImageCaptions(listOf("Cap A"))

        val list = repo.imageInfoMapFlow.first()
        assertThat(list.map { it.caption }).containsExactly("Cap A", null, null).inOrder()
    }

    @Test
    fun updateImageCaptions_allNull_clearsExistingCaptions() = runBlocking {
        repo.updateImageList(images("a", "b"))
        repo.updateImageCaptions(listOf("Cap A", "Cap B"))

        repo.updateImageCaptions(listOf(null, null))

        val list = repo.imageInfoMapFlow.first()
        assertThat(list.map { it.caption }).containsExactly(null, null).inOrder()
    }

    @Test
    fun updateImageCaptions_doesNotAffectOtherImageInfoFields() = runBlocking {
        repo.updateImageList(images("a"))
        val before = repo.imageInfoMapFlow.first().first()

        repo.updateImageCaptions(listOf("Cap A"))

        val after = repo.imageInfoMapFlow.first().first()
        assertThat(after.uri).isEqualTo(before.uri)
        assertThat(after.copy(caption = null)).isEqualTo(before.copy(caption = null))
    }
}
