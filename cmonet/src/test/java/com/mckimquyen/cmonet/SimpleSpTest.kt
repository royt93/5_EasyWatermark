package com.mckimquyen.cmonet

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ENH-21: Kiểm tra lưu/đọc các kiểu dữ liệu cơ bản qua SimpleSp và kiểm chứng
 * không crash khi ghi/đọc, phục vụ gate debug log BuildConfig.DEBUG.
 */
@RunWith(RobolectricTestRunner::class)
class SimpleSpTest {

    private lateinit var context: Context
    private lateinit var storage: SimpleSp

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = SimpleSp(context)
    }

    @Test
    fun testSaveAndGetInt() {
        storage.save("test_int", 42)
        assertThat(storage.getValue("test_int", 0)).isEqualTo(42)
    }

    @Test
    fun testSaveAndGetString() {
        storage.save("test_str", "hello_monet")
        assertThat(storage.getValue("test_str", "")).isEqualTo("hello_monet")
    }

    @Test
    fun testSaveAndGetBoolean() {
        storage.save("test_bool", true)
        assertThat(storage.getValue("test_bool", false)).isTrue()
    }

    @Test
    fun testSaveAndGetFloat() {
        storage.save("test_float", 3.14f)
        assertThat(storage.getValue("test_float", 0f)).isEqualTo(3.14f)
    }

    @Test
    fun testSaveAndGetLong() {
        storage.save("test_long", 9999999999L)
        assertThat(storage.getValue("test_long", 0L)).isEqualTo(9999999999L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testUnsupportedTypeThrows() {
        storage.save("test_invalid", listOf("item"))
    }
}
