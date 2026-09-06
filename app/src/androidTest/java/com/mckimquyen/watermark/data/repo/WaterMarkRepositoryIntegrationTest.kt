package com.mckimquyen.watermark.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
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
}
