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
}
