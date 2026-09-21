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
 * FEAT-17: [WaterMarkRepository.toggleSkipExport] chỉ đổi `isSkippedInExport` của ĐÚNG 1 ảnh
 * (theo uri), không đụng ảnh khác, và đảo ngược được (bật rồi tắt lại).
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkRepositorySkipExportRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var repo: WaterMarkRepository

    @Before
    fun setUp() {
        repo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
    }

    private fun images(vararg names: String) = names.map { ImageInfo(Uri.parse("content://media/$it")) }

    @Test
    fun toggleSkipExport_marksOnlyMatchingUri() = runBlocking {
        repo.updateImageList(images("a", "b", "c"))

        repo.toggleSkipExport(Uri.parse("content://media/b"))

        val list = repo.imageInfoMapFlow.first()
        assertThat(list.map { it.isSkippedInExport }).containsExactly(false, true, false).inOrder()
    }

    @Test
    fun toggleSkipExport_calledTwice_flipsBackToActive() = runBlocking {
        repo.updateImageList(images("a"))
        val uri = Uri.parse("content://media/a")

        repo.toggleSkipExport(uri)
        repo.toggleSkipExport(uri)

        val list = repo.imageInfoMapFlow.first()
        assertThat(list.first().isSkippedInExport).isFalse()
    }

    @Test
    fun toggleSkipExport_unknownUri_leavesListUnchanged() = runBlocking {
        repo.updateImageList(images("a", "b"))

        repo.toggleSkipExport(Uri.parse("content://media/does-not-exist"))

        val list = repo.imageInfoMapFlow.first()
        assertThat(list.map { it.isSkippedInExport }).containsExactly(false, false).inOrder()
    }
}
