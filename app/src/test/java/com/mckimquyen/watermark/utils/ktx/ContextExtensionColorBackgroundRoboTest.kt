package com.mckimquyen.watermark.utils.ktx

import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.cmonet.CMonet
import com.mckimquyen.watermark.R
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BUG-AUDIT-2026-09-29: `colorBackground` (không có dynamic color) từng LUÔN trả
 * `md_theme_dark_background` bất kể theme sáng/tối thật — khác mọi property màu khác trong cùng
 * file (`colorPrimary`/`colorSurface`/`colorTertiary` đều branch theo `isNight()`/`supportNight()`).
 * `CMonet.setUserEnabled(false)` (đúng API BUG-41 đã có, tương ứng user tắt switch Dynamic Color
 * trong About) để chắc chắn rơi vào nhánh fallback đang test, không phụ thuộc
 * `MonetManufacturer.isDeviceCapable()` của môi trường chạy test.
 */
@RunWith(RobolectricTestRunner::class)
class ContextExtensionColorBackgroundRoboTest {

    @Before
    fun setUp() {
        CMonet.setUserEnabled(false)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun colorBackground_lightTheme_returnsLightColor() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)

        assertThat(context.colorBackground).isEqualTo(context.getColor(R.color.md_theme_light_background))
    }

    @Test
    @Config(qualifiers = "night")
    fun colorBackground_darkTheme_returnsDarkColor() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)

        assertThat(context.colorBackground).isEqualTo(context.getColor(R.color.md_theme_dark_background))
    }
}
