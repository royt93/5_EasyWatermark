package com.mckimquyen.watermark.utils.ktx

import android.content.Context
import android.graphics.Color
import androidx.palette.graphics.Palette
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.color.MaterialColors
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PaletteKtxTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(R.style.Theme_MyApp)
    }

    @Test
    fun titleTextColor_nullPalette_returnsDefaultOnSurface() {
        val palette: Palette? = null
        val color = palette.titleTextColor(context)
        val expected = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorOnSurface,
            Color.BLACK
        )
        assertThat(color).isEqualTo(expected)
    }

    @Test
    fun bgColor_nullPalette_returnsColorSurfaceVariant() {
        val palette: Palette? = null
        val color = palette.bgColor(context)
        assertThat(color).isEqualTo(context.colorSurfaceVariant)
    }

    @Test
    fun titleTextColor_meetsWcagAaContrast_whenDerived() {
        val palette: Palette? = null
        val textColor = palette.titleTextColor(context)
        val surfaceContainer = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            Color.LTGRAY
        )
        val contrast = androidx.core.graphics.ColorUtils.calculateContrast(textColor, surfaceContainer)
        // Must satisfy WCAG AA contrast (>= 4.5:1)
        assertThat(contrast).isAtLeast(4.5)
    }
}
