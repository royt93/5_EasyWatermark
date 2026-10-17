package com.mckimquyen.watermark.ui.widget

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** Regression từ smoke TECNO KJ7: font scale 1,15 từng làm title/description bị cắt ở card cao cố định. */
@RunWith(AndroidJUnit4::class)
class LaunchViewLargeFontLayoutIntegrationTest {

    @Test
    fun layoutLaunch_actionCardText_isNotClippedAtLargeFontScale() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(base.resources.configuration).apply {
            fontScale = FONT_SCALE
            densityDpi = DENSITY_DPI
            setLocale(Locale("vi"))
        }
        val context = ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_MyApp)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val launchView = LaunchView(context)
            listOf(
                launchView.ivSelectedPhotoTips,
                launchView.ivCaptureFromCamera,
                launchView.ivPasteFromClipboard,
                launchView.ivGoAboutPage,
                launchView.ivGoVip,
                launchView.tvAppBrand,
                launchView.tvAppTagline,
                launchView.tvVersionCopyright
            ).forEach { it.isVisible = true }
            launchView.setPadding(0, STATUS_BAR_PX, 0, NAVIGATION_BAR_PX)
            launchView.measure(
                View.MeasureSpec.makeMeasureSpec(SCREEN_WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(SCREEN_HEIGHT_PX, View.MeasureSpec.EXACTLY)
            )
            launchView.layout(0, 0, SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX)

            listOf(
                launchView.ivSelectedPhotoTips,
                launchView.ivCaptureFromCamera,
                launchView.ivPasteFromClipboard,
                launchView.ivGoAboutPage
            ).forEach { card ->
                val column = card.getChildAt(0) as ViewGroup
                val title = column.getChildAt(1) as TextView
                val desc = column.getChildAt(2) as TextView
                assertThat(desc.bottom).isAtMost(column.height - column.paddingBottom)
                assertThat(title.layout.getEllipsisCount(title.lineCount - 1)).isEqualTo(0)
                assertThat(desc.layout.getEllipsisCount(desc.lineCount - 1)).isEqualTo(0)
            }
            assertThat(launchView.ivGoVip.bottom).isAtMost(launchView.tvVersionCopyright.top)
            assertThat(launchView.tvVersionCopyright.bottom).isAtMost(SCREEN_HEIGHT_PX - NAVIGATION_BAR_PX)
            assertThat(launchView.ivSelectedPhotoTips.top).isAtLeast(launchView.tvAppTagline.bottom)
        }
    }

    private companion object {
        const val FONT_SCALE = 1.15f
        const val DENSITY_DPI = 480
        const val SCREEN_WIDTH_PX = 1080
        const val SCREEN_HEIGHT_PX = 2436
        const val STATUS_BAR_PX = 120
        const val NAVIGATION_BAR_PX = 168
    }
}
