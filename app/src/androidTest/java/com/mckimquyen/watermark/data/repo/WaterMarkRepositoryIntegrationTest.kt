package com.mckimquyen.watermark.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ExifFrameStyle
import com.mckimquyen.watermark.di.waterMarkDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test (instrumented, DataStore thật trên thiết bị): kiểm chứng refactor gỡ
 * `MyApplication.instance` (doc/todo.md) — [WaterMarkRepository] nay nhận `@ApplicationContext`
 * qua constructor và dùng đúng context đó để lấy chuỗi mặc định (`getString`), không còn phụ
 * thuộc static field đã xóa.
 */
@RunWith(AndroidJUnit4::class)
class WaterMarkRepositoryIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repo: WaterMarkRepository

    @Before
    fun setUp() {
        runBlocking { context.waterMarkDataStore.edit { it.clear() } }
        repo = WaterMarkRepository(context, context.waterMarkDataStore)
    }

    @After
    fun tearDown() {
        runBlocking { context.waterMarkDataStore.edit { it.clear() } }
    }

    @Test
    fun waterMark_emptyDataStore_defaultTextComesFromProvidedContext() = runBlocking {
        val waterMark = repo.waterMark.first()

        assertThat(waterMark.text).isEqualTo(context.getString(R.string.config_default_water_mark_text))
    }

    @Test
    fun updateText_thenReadWaterMark_reflectsNewValue_roundTrip() = runBlocking {
        repo.updateText("hello from test")

        val waterMark = repo.waterMark.first()

        assertThat(waterMark.text).isEqualTo("hello from test")
        assertThat(waterMark.markMode).isEqualTo(WaterMarkRepository.MarkMode.Text)
    }

    /**
     * feat.md #9 — Frame presets cho EXIF border: [WaterMarkRepository.updateExifFrameStyle]
     * phải persist qua DataStore thật (không chỉ trong bộ nhớ) và đọc lại đúng qua ordinal,
     * theo đúng pattern round-trip đã có của `anchor`.
     */
    @Test
    fun waterMark_emptyDataStore_defaultExifFrameStyleIsClassic() = runBlocking {
        val waterMark = repo.waterMark.first()

        assertThat(waterMark.exifFrameStyle).isEqualTo(ExifFrameStyle.CLASSIC.ordinal)
    }

    @Test
    fun updateExifFrameStyle_thenReadWaterMark_reflectsNewValue_roundTrip() = runBlocking {
        repo.updateExifFrameStyle(ExifFrameStyle.FILM_STRIP)

        val waterMark = repo.waterMark.first()

        assertThat(waterMark.exifFrameStyle).isEqualTo(ExifFrameStyle.FILM_STRIP.ordinal)
    }

    @Test
    fun updateExifFrameStyle_everyStyle_roundTripsCorrectly() = runBlocking {
        ExifFrameStyle.entries.forEach { style ->
            repo.updateExifFrameStyle(style)

            val waterMark = repo.waterMark.first()

            assertThat(waterMark.exifFrameStyle).isEqualTo(style.ordinal)
        }
    }

    @Test
    fun updateExifFrameStyle_persistsIndependently_fromEnableExifToggle() = runBlocking {
        repo.updateExifFrameStyle(ExifFrameStyle.POLAROID)
        repo.updateEnableExif(true)

        val waterMark = repo.waterMark.first()

        assertThat(waterMark.enableExif).isTrue()
        assertThat(waterMark.exifFrameStyle).isEqualTo(ExifFrameStyle.POLAROID.ordinal)
    }

    /**
     * FEAT-14 Custom Frame Builder: 3 override nhẹ (band color/thickness/serif caption) phải mặc
     * định là `null` (AC "không tuỳ chỉnh gì → hành vi giữ nguyên") và persist qua DataStore thật
     * theo đúng pattern round-trip của `exifFrameStyle` ở trên.
     */
    @Test
    fun waterMark_emptyDataStore_exifCustomizationDefaultsToNull() = runBlocking {
        val waterMark = repo.waterMark.first()

        assertThat(waterMark.exifBandColor).isNull()
        assertThat(waterMark.exifBandThicknessPercent).isNull()
        assertThat(waterMark.exifUseSerifCaption).isNull()
    }

    @Test
    fun updateExifBandColor_thenReadWaterMark_reflectsNewValue_roundTrip() = runBlocking {
        repo.updateExifBandColor(android.graphics.Color.RED)

        val waterMark = repo.waterMark.first()

        assertThat(waterMark.exifBandColor).isEqualTo(android.graphics.Color.RED)
    }

    @Test
    fun updateExifBandColor_withNull_clearsOverride() = runBlocking {
        repo.updateExifBandColor(android.graphics.Color.RED)
        repo.updateExifBandColor(null)

        val waterMark = repo.waterMark.first()

        assertThat(waterMark.exifBandColor).isNull()
    }

    @Test
    fun updateExifBandThicknessPercent_thenReadWaterMark_reflectsNewValue_roundTrip() = runBlocking {
        repo.updateExifBandThicknessPercent(0.2f)

        val waterMark = repo.waterMark.first()

        assertThat(waterMark.exifBandThicknessPercent).isEqualTo(0.2f)
    }

    @Test
    fun updateExifBandThicknessPercent_outOfRange_getsClampedToSafeBounds() = runBlocking {
        repo.updateExifBandThicknessPercent(0.99f)
        assertThat(repo.waterMark.first().exifBandThicknessPercent)
            .isEqualTo(WaterMarkRepository.MAX_EXIF_BAND_THICKNESS_PERCENT)

        repo.updateExifBandThicknessPercent(0.0f)
        assertThat(repo.waterMark.first().exifBandThicknessPercent)
            .isEqualTo(WaterMarkRepository.MIN_EXIF_BAND_THICKNESS_PERCENT)
    }

    @Test
    fun updateExifUseSerifCaption_thenReadWaterMark_reflectsNewValue_roundTrip() = runBlocking {
        repo.updateExifUseSerifCaption(true)

        assertThat(repo.waterMark.first().exifUseSerifCaption).isTrue()

        repo.updateExifUseSerifCaption(false)

        assertThat(repo.waterMark.first().exifUseSerifCaption).isFalse()
    }

    @Test
    fun resetExifCustomization_clearsAllThreeOverrides_atOnce() = runBlocking {
        repo.updateExifBandColor(android.graphics.Color.BLUE)
        repo.updateExifBandThicknessPercent(0.25f)
        repo.updateExifUseSerifCaption(true)

        repo.resetExifCustomization()

        val waterMark = repo.waterMark.first()
        assertThat(waterMark.exifBandColor).isNull()
        assertThat(waterMark.exifBandThicknessPercent).isNull()
        assertThat(waterMark.exifUseSerifCaption).isNull()
    }

    @Test
    fun exifCustomization_persistsIndependently_fromFrameStyleSelection() = runBlocking {
        repo.updateExifBandColor(android.graphics.Color.GREEN)
        repo.updateExifFrameStyle(ExifFrameStyle.MINIMAL)

        val waterMark = repo.waterMark.first()

        assertThat(waterMark.exifBandColor).isEqualTo(android.graphics.Color.GREEN)
        assertThat(waterMark.exifFrameStyle).isEqualTo(ExifFrameStyle.MINIMAL.ordinal)
    }
}
