package com.mckimquyen.cmonet

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BUG-41: `isDynamicColorAvailable()` cũ trộn 2 khái niệm "thiết bị có hỗ trợ" và "user có muốn
 * bật" vào 1 hàm (`||`) — trên API 31+ vế đầu luôn true nên vế sau (`isForceSupport`, đọc/ghi qua
 * switch About) hoàn toàn vô tác dụng. Test này verify 2 khái niệm tách riêng đúng qua 4 tổ hợp
 * (device hỗ trợ/không × user bật/tắt) bằng `Config(sdk=...)` để giả lập API level, khớp cách
 * Robolectric mô phỏng `DynamicColors.isDynamicColorAvailable()` theo SDK thật của Material.
 */
@RunWith(RobolectricTestRunner::class)
class MonetManufacturerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Mỗi test 1 SharedPreferences sạch (SimpleSp dùng chung 1 SP_NAME cố định trong Context).
        context.getSharedPreferences("sp_water_mark_c_monet", Context.MODE_PRIVATE).edit().clear().apply()
    }

    @Test
    fun isUserEnabled_default_isTrue() {
        val manufacturer = MonetManufacturer(context)
        assertThat(manufacturer.isUserEnabled()).isTrue()
    }

    @Test
    fun setUserEnabled_false_thenTrue_persistsAndUpdatesCacheImmediately() {
        val manufacturer = MonetManufacturer(context)

        manufacturer.setUserEnabled(false)
        assertThat(manufacturer.isUserEnabled()).isFalse()

        manufacturer.setUserEnabled(true)
        assertThat(manufacturer.isUserEnabled()).isTrue()
    }

    @Test
    fun setUserEnabled_persistsAcrossNewInstance() {
        MonetManufacturer(context).setUserEnabled(false)
        val reloaded = MonetManufacturer(context)
        assertThat(reloaded.isUserEnabled()).isFalse()
    }

    @Config(sdk = [31])
    @Test
    fun shouldApplyDynamicColor_deviceSupportedAndUserEnabled_true() {
        val manufacturer = MonetManufacturer(context)
        assertThat(manufacturer.isDeviceCapable()).isTrue()
        manufacturer.setUserEnabled(true)
        assertThat(manufacturer.shouldApplyDynamicColor()).isTrue()
    }

    /** Đây chính là case BUG-41: user tắt switch trên thiết bị API31+ vẫn hỗ trợ native. */
    @Config(sdk = [31])
    @Test
    fun shouldApplyDynamicColor_deviceSupportedButUserDisabled_false() {
        val manufacturer = MonetManufacturer(context)
        manufacturer.setUserEnabled(false)
        assertThat(manufacturer.shouldApplyDynamicColor()).isFalse()
        // Năng lực thiết bị KHÔNG đổi theo lựa chọn user — 2 khái niệm độc lập.
        assertThat(manufacturer.isDeviceCapable()).isTrue()
    }

    @Config(sdk = [28])
    @Test
    fun shouldApplyDynamicColor_deviceNotSupported_falseRegardlessOfUserChoice() {
        val manufacturer = MonetManufacturer(context)
        manufacturer.setUserEnabled(true)
        assertThat(manufacturer.isDeviceCapable()).isFalse()
        assertThat(manufacturer.shouldApplyDynamicColor()).isFalse()
    }
}
