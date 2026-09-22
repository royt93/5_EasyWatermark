package com.mckimquyen.watermark.data.repo

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.Anchor
import com.mckimquyen.watermark.data.model.ExifFrameStyle
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-06: [WaterMarkRepository.applyWaterMark] — nền tảng cho "áp dụng lại profile", phải ghi
 * ĐÚNG toàn bộ field trong 1 lần gọi (đọc lại qua [WaterMarkRepository.waterMark] phải khớp hệt),
 * và KHÔNG đụng `recentIconUris` (MRU dùng chung toàn app, không thuộc về 1 profile).
 * DataStore cô lập — xem lý do ở `testutil/TestDataStores.kt`.
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkRepositoryApplyWaterMarkRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var repo: WaterMarkRepository

    @Before
    fun setUp() {
        repo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
    }

    private fun sampleMark(recentIconUris: List<Uri> = emptyList()) = WaterMark(
        text = "Profile text",
        textSize = 22f,
        textColor = 0xAABBCC,
        textStyle = TextPaintStyle.Stroke,
        textTypeface = TextTypeface.BoldItalic,
        alpha = 128,
        degree = 90f,
        hGap = 15,
        vGap = 25,
        iconUri = Uri.parse("content://media/profile-icon.png"),
        markMode = WaterMarkRepository.MarkMode.Image,
        enableBounds = true,
        enableExif = true,
        exifFrameStyle = ExifFrameStyle.POLAROID.ordinal,
        anchor = Anchor.TOP_LEFT.ordinal,
        marginPercent = 0.1f,
        exifBandColor = 0x445566,
        exifBandThicknessPercent = 0.15f,
        exifUseSerifCaption = false,
        textEffectStroke = true,
        textEffectShadow = false,
        textEffectPillBackground = true,
        recentIconUris = recentIconUris
    )

    @Test
    fun applyWaterMark_thenReadBack_matchesEveryField() = runBlocking {
        val mark = sampleMark()

        repo.applyWaterMark(mark)

        val readBack = repo.waterMark.first()
        assertThat(readBack.text).isEqualTo(mark.text)
        assertThat(readBack.textSize).isEqualTo(mark.textSize)
        assertThat(readBack.textColor).isEqualTo(mark.textColor)
        assertThat(readBack.textStyle).isEqualTo(mark.textStyle)
        assertThat(readBack.textTypeface).isEqualTo(mark.textTypeface)
        assertThat(readBack.alpha).isEqualTo(mark.alpha)
        assertThat(readBack.degree).isEqualTo(mark.degree)
        assertThat(readBack.hGap).isEqualTo(mark.hGap)
        assertThat(readBack.vGap).isEqualTo(mark.vGap)
        assertThat(readBack.iconUri).isEqualTo(mark.iconUri)
        assertThat(readBack.markMode).isEqualTo(mark.markMode)
        assertThat(readBack.enableBounds).isEqualTo(mark.enableBounds)
        assertThat(readBack.enableExif).isEqualTo(mark.enableExif)
        assertThat(readBack.exifFrameStyle).isEqualTo(mark.exifFrameStyle)
        assertThat(readBack.anchor).isEqualTo(mark.anchor)
        assertThat(readBack.marginPercent).isEqualTo(mark.marginPercent)
        assertThat(readBack.exifBandColor).isEqualTo(mark.exifBandColor)
        assertThat(readBack.exifBandThicknessPercent).isEqualTo(mark.exifBandThicknessPercent)
        assertThat(readBack.exifUseSerifCaption).isEqualTo(mark.exifUseSerifCaption)
        assertThat(readBack.textEffectStroke).isEqualTo(mark.textEffectStroke)
        assertThat(readBack.textEffectShadow).isEqualTo(mark.textEffectShadow)
        assertThat(readBack.textEffectPillBackground).isEqualTo(mark.textEffectPillBackground)
    }

    @Test
    fun applyWaterMark_nullExifOverrides_clearsAnyPreviousOverride() = runBlocking {
        repo.applyWaterMark(sampleMark())
        assertThat(repo.waterMark.first().exifBandColor).isNotNull()

        repo.applyWaterMark(sampleMark().copy(exifBandColor = null, exifBandThicknessPercent = null, exifUseSerifCaption = null))

        val readBack = repo.waterMark.first()
        assertThat(readBack.exifBandColor).isNull()
        assertThat(readBack.exifBandThicknessPercent).isNull()
        assertThat(readBack.exifUseSerifCaption).isNull()
    }

    @Test
    fun applyWaterMark_doesNotTouchRecentIconUris() = runBlocking {
        repo.updateIcon(Uri.parse("content://media/existing-recent.png"))

        repo.applyWaterMark(sampleMark(recentIconUris = listOf(Uri.parse("content://media/should-be-ignored.png"))))

        val readBack = repo.waterMark.first()
        assertThat(readBack.recentIconUris).containsExactly(Uri.parse("content://media/existing-recent.png"))
        Unit
    }

    @Test
    fun applyWaterMark_textMarkMode_roundTripsAsTextMode() = runBlocking {
        repo.applyWaterMark(sampleMark().copy(markMode = WaterMarkRepository.MarkMode.Text))

        assertThat(repo.waterMark.first().markMode).isEqualTo(WaterMarkRepository.MarkMode.Text)
    }
}
