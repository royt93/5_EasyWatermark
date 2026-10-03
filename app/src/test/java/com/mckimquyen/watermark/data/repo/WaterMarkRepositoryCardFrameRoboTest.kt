package com.mckimquyen.watermark.data.repo

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** FEAT-28: DataStore round-trip, clamp, mặc định, undo cho khung thẻ. */
@RunWith(RobolectricTestRunner::class)
class WaterMarkRepositoryCardFrameRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var repo: WaterMarkRepository

    @Before
    fun setUp() {
        repo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
    }

    @Test
    fun defaults_cardFrameOff_withDefaultValues() = runBlocking {
        val m = repo.waterMark.first()
        assertThat(m.cardFrameEnabled).isFalse()
        assertThat(m.cardCornerRadiusPercent).isEqualTo(WaterMarkRepository.DEFAULT_CARD_CORNER_PERCENT)
        assertThat(m.cardShadowPercent).isEqualTo(WaterMarkRepository.DEFAULT_CARD_SHADOW_PERCENT)
        assertThat(m.cardBackgroundColor).isEqualTo(WaterMarkRepository.DEFAULT_CARD_BACKGROUND_COLOR)
    }

    @Test
    fun updates_roundTrip() = runBlocking {
        repo.updateCardFrameEnabled(true)
        repo.updateCardCornerRadiusPercent(0.2f)
        repo.updateCardShadowPercent(0.05f)
        repo.updateCardBackgroundColor(0xFF112233.toInt())
        val m = repo.waterMark.first()
        assertThat(m.cardFrameEnabled).isTrue()
        assertThat(m.cardCornerRadiusPercent).isEqualTo(0.2f)
        assertThat(m.cardShadowPercent).isEqualTo(0.05f)
        assertThat(m.cardBackgroundColor).isEqualTo(0xFF112233.toInt())
    }

    @Test
    fun corner_isClampedToRange() = runBlocking {
        repo.updateCardCornerRadiusPercent(9f)
        assertThat(repo.waterMark.first().cardCornerRadiusPercent).isEqualTo(WaterMarkRepository.MAX_CARD_CORNER_PERCENT)
        repo.updateCardCornerRadiusPercent(-3f)
        assertThat(repo.waterMark.first().cardCornerRadiusPercent).isEqualTo(0f)
    }

    @Test
    fun shadow_isClampedToRange() = runBlocking {
        repo.updateCardShadowPercent(9f)
        assertThat(repo.waterMark.first().cardShadowPercent).isEqualTo(WaterMarkRepository.MAX_CARD_SHADOW_PERCENT)
        repo.updateCardShadowPercent(-1f)
        assertThat(repo.waterMark.first().cardShadowPercent).isEqualTo(0f)
    }

    @Test
    fun applyWaterMark_persistsCardFields_andClamps() = runBlocking {
        val base = repo.waterMark.first()
        repo.applyWaterMark(
            base.copy(
                cardFrameEnabled = true,
                cardCornerRadiusPercent = 7f,
                cardShadowPercent = 7f,
                cardBackgroundColor = 0xFF0000FF.toInt()
            )
        )
        val m = repo.waterMark.first()
        assertThat(m.cardFrameEnabled).isTrue()
        assertThat(m.cardCornerRadiusPercent).isEqualTo(WaterMarkRepository.MAX_CARD_CORNER_PERCENT)
        assertThat(m.cardShadowPercent).isEqualTo(WaterMarkRepository.MAX_CARD_SHADOW_PERCENT)
        assertThat(m.cardBackgroundColor).isEqualTo(0xFF0000FF.toInt())
    }

    @Test
    fun undo_restoresPreviousCardFrameState() = runBlocking {
        delay(WaterMarkRepository.UNDO_SNAPSHOT_DEBOUNCE_MS + 100L)
        repo.updateCardFrameEnabled(true)
        assertThat(repo.waterMark.first().cardFrameEnabled).isTrue()
        repo.undo()
        assertThat(repo.waterMark.first().cardFrameEnabled).isFalse()
    }
}
