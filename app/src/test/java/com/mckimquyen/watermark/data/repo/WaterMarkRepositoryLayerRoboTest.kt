package com.mckimquyen.watermark.data.repo

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.WatermarkLayer
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-03: CRUD layer watermark PHỤ — [WaterMarkRepository.addLayer]/[removeLayer]/[updateLayer]/
 * [reorderLayer], giới hạn [WaterMarkRepository.MAX_EXTRA_LAYERS]. DataStore cô lập — xem lý do ở
 * `testutil/TestDataStores.kt`.
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkRepositoryLayerRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var repo: WaterMarkRepository

    @Before
    fun setUp() {
        repo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
    }

    private fun textLayer(text: String) = WatermarkLayer(markMode = WaterMarkRepository.MarkMode.Text, text = text)

    @Test
    fun freshRepo_extraLayersEmpty() = runBlocking {
        assertThat(repo.waterMark.first().extraLayers).isEmpty()
    }

    @Test
    fun addLayer_appendsToEnd() = runBlocking {
        repo.addLayer(textLayer("A"))
        repo.addLayer(textLayer("B"))

        val layers = repo.waterMark.first().extraLayers
        assertThat(layers.map { it.text }).containsExactly("A", "B").inOrder()
    }

    @Test
    fun addLayer_atMaxCapacity_isNoOp() = runBlocking {
        repeat(WaterMarkRepository.MAX_EXTRA_LAYERS) { repo.addLayer(textLayer("layer-$it")) }
        assertThat(repo.waterMark.first().extraLayers).hasSize(WaterMarkRepository.MAX_EXTRA_LAYERS)

        repo.addLayer(textLayer("overflow"))

        assertThat(repo.waterMark.first().extraLayers).hasSize(WaterMarkRepository.MAX_EXTRA_LAYERS)
        assertThat(repo.waterMark.first().extraLayers.map { it.text }).doesNotContain("overflow")
    }

    @Test
    fun removeLayer_validIndex_removesOnlyThatLayer() = runBlocking {
        repo.addLayer(textLayer("A"))
        repo.addLayer(textLayer("B"))
        repo.addLayer(textLayer("C"))

        repo.removeLayer(1)

        assertThat(repo.waterMark.first().extraLayers.map { it.text }).containsExactly("A", "C").inOrder()
    }

    @Test
    fun removeLayer_outOfRangeIndex_isNoOp() = runBlocking {
        repo.addLayer(textLayer("A"))

        repo.removeLayer(5)
        repo.removeLayer(-1)

        assertThat(repo.waterMark.first().extraLayers.map { it.text }).containsExactly("A").inOrder()
    }

    @Test
    fun updateLayer_validIndex_replacesContent() = runBlocking {
        repo.addLayer(textLayer("A"))
        repo.addLayer(textLayer("B"))

        repo.updateLayer(0, textLayer("A-edited"))

        assertThat(repo.waterMark.first().extraLayers.map { it.text }).containsExactly("A-edited", "B").inOrder()
    }

    @Test
    fun updateLayer_outOfRangeIndex_isNoOp() = runBlocking {
        repo.addLayer(textLayer("A"))

        repo.updateLayer(3, textLayer("ignored"))

        assertThat(repo.waterMark.first().extraLayers.map { it.text }).containsExactly("A").inOrder()
    }

    @Test
    fun reorderLayer_movesLayerToNewPosition() = runBlocking {
        repo.addLayer(textLayer("A"))
        repo.addLayer(textLayer("B"))
        repo.addLayer(textLayer("C"))

        repo.reorderLayer(0, 2)

        assertThat(repo.waterMark.first().extraLayers.map { it.text }).containsExactly("B", "C", "A").inOrder()
    }

    @Test
    fun reorderLayer_sameIndex_isNoOp() = runBlocking {
        repo.addLayer(textLayer("A"))
        repo.addLayer(textLayer("B"))

        repo.reorderLayer(0, 0)

        assertThat(repo.waterMark.first().extraLayers.map { it.text }).containsExactly("A", "B").inOrder()
    }

    @Test
    fun reorderLayer_outOfRangeIndex_isNoOp() = runBlocking {
        repo.addLayer(textLayer("A"))

        repo.reorderLayer(0, 5)

        assertThat(repo.waterMark.first().extraLayers.map { it.text }).containsExactly("A").inOrder()
    }
}
