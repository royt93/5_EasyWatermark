package com.mckimquyen.watermark.data.repo

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.Anchor
import com.mckimquyen.watermark.data.model.DualWatermarkPreset
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WaterMarkRepositoryDualPresetRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var repo: WaterMarkRepository

    @Before
    fun setUp() {
        repo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
    }

    @Test
    fun applyDualPreset_brandCopyright_updatesPrimaryAndExtraLayers() = runBlocking {
        repo.updateText("My Copyright")
        val iconUri = Uri.parse("content://media/external/images/media/100")
        repo.updateIcon(iconUri)

        repo.applyDualPreset(DualWatermarkPreset.BRAND_COPYRIGHT)

        val mark = repo.waterMark.first()
        assertThat(mark.markMode).isEqualTo(WaterMarkRepository.MarkMode.Text)
        assertThat(mark.text).isEqualTo("My Copyright")
        assertThat(mark.anchor).isEqualTo(Anchor.BOTTOM_RIGHT.ordinal)

        assertThat(mark.extraLayers).hasSize(1)
        val extra = mark.extraLayers[0]
        assertThat(extra.markMode).isEqualTo(WaterMarkRepository.MarkMode.Image)
        assertThat(extra.anchor).isEqualTo(Anchor.TOP_RIGHT.ordinal)
        assertThat(extra.iconUri).isEqualTo(iconUri)
    }

    @Test
    fun applyDualPreset_diagonalBalance_setsOppositeCorners() = runBlocking {
        repo.updateText("")
        repo.applyDualPreset(DualWatermarkPreset.DIAGONAL_BALANCE, defaultText = "Default Brand")

        val mark = repo.waterMark.first()
        assertThat(mark.text).isEqualTo("Default Brand")
        assertThat(mark.anchor).isEqualTo(Anchor.BOTTOM_RIGHT.ordinal)

        assertThat(mark.extraLayers).hasSize(1)
        assertThat(mark.extraLayers[0].anchor).isEqualTo(Anchor.TOP_LEFT.ordinal)
    }

    @Test
    fun applyDualPreset_supportsUndo() = runBlocking {
        repo.updateText("Before Preset")
        // Đợi vượt quá debounce window của undo snapshot (400ms)
        kotlinx.coroutines.delay(WaterMarkRepository.UNDO_SNAPSHOT_DEBOUNCE_MS + 100L)
        val initialAnchor = repo.waterMark.first().anchor
        val initialExtraCount = repo.waterMark.first().extraLayers.size

        repo.applyDualPreset(DualWatermarkPreset.BRAND_COPYRIGHT)
        assertThat(repo.waterMark.first().extraLayers).hasSize(1)

        repo.undo()

        val restored = repo.waterMark.first()
        assertThat(restored.text).isEqualTo("Before Preset")
        assertThat(restored.anchor).isEqualTo(initialAnchor)
        assertThat(restored.extraLayers).hasSize(initialExtraCount)
    }
}
