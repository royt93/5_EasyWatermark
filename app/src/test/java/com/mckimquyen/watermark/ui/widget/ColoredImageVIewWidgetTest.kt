package com.mckimquyen.watermark.ui.widget

import android.graphics.Color
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.utils.ktx.colorPrimary
import com.mckimquyen.watermark.utils.ktx.colorSecondary
import com.mckimquyen.watermark.utils.ktx.colorTertiary
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ColoredImageVIew (toolbar logo shimmer) truoc day co nhanh else hardcode 4 mau neon
 * (#FFA51F/#FFD703/#C0FF39/#00FFE0) khi thiet bi khong ho tro Dynamic Color (API < 31) -
 * pha vo Material You tren phan lon thiet bi vi minSdk=24. Test nay khoa lai: colorList
 * phai luon lay tu theme (colorPrimary/Secondary/Tertiary), khong con nhanh fallback rieng.
 */
@RunWith(RobolectricTestRunner::class)
class ColoredImageVIewWidgetTest {

    private val themedContext by lazy {
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)
    }

    @Test
    fun colorList_usesThemeColors_noHardcodedNeonFallback() {
        val view = ColoredImageVIew(themedContext)

        val field = ColoredImageVIew::class.java.getDeclaredField("colorList")
        field.isAccessible = true
        val colorList = (field.get(view) as IntArray).toList()

        val neonColors = listOf(
            Color.parseColor("#FFA51F"),
            Color.parseColor("#FFD703"),
            Color.parseColor("#C0FF39"),
            Color.parseColor("#00FFE0")
        )
        assertThat(colorList).containsNoneIn(neonColors)
        assertThat(colorList).containsExactly(
            themedContext.colorPrimary,
            themedContext.colorSecondary,
            themedContext.colorTertiary,
            themedContext.colorTertiary
        ).inOrder()
    }
}
