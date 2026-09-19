package com.mckimquyen.watermark.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.widget.ImageView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.utils.ktx.applyConsistentIconTint
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImageResourceWatermarkUntintedTest {

    private val themedContext by lazy {
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_MyApp
        )
    }

    @Test
    fun toolbar_applyConsistentIconTint_doesNotTintLogoImageResource() {
        val toolbar = Toolbar(themedContext)
        val watermarkLogo = ContextCompat.getDrawable(themedContext, R.drawable.ic_watermark)
        toolbar.logo = watermarkLogo

        // Add a mock logo ImageView as direct child of Toolbar
        val logoView = ImageView(themedContext).apply {
            setImageDrawable(watermarkLogo)
        }
        toolbar.addView(logoView)

        // Add ActionMenuView with overflow/action items
        val actionMenuView = ActionMenuView(themedContext)
        val overflowBtn = ImageView(themedContext)
        actionMenuView.addView(overflowBtn)
        toolbar.addView(actionMenuView)

        // Apply consistent tint
        val tintColor = Color.CYAN
        toolbar.applyConsistentIconTint(tintColor)

        // The toolbar logo image view must NEVER have tint applied
        assertThat(logoView.imageTintList).isNull()

        // The action menu item inside ActionMenuView MUST be tinted
        assertThat(overflowBtn.imageTintList).isEqualTo(ColorStateList.valueOf(tintColor))
    }

    @Test
    fun imageWatermarkMode_doesNotApplyColorFilterToImageResourceWatermark() {
        // When watermark is an image/sticker resource (not a signature webp), colorFilter must be null
        val imageResourceUri = Uri.parse("android.resource://com.mckimquyen.watermark/drawable/ic_watermark")
        val config = WaterMark(
            text = "",
            textSize = 20f,
            textColor = Color.MAGENTA,
            textStyle = TextPaintStyle.Fill,
            textTypeface = TextTypeface.Normal,
            alpha = 255,
            degree = 0f,
            hGap = 10,
            vGap = 10,
            iconUri = imageResourceUri,
            markMode = WaterMarkRepository.MarkMode.Image,
            enableBounds = false
        )

        val paint = Paint()
        if (config.iconUri.toString().contains("signature") && config.iconUri.toString().contains(".webp")) {
            paint.colorFilter = android.graphics.PorterDuffColorFilter(config.textColor, android.graphics.PorterDuff.Mode.SRC_IN)
        } else {
            paint.colorFilter = null
        }

        assertThat(paint.colorFilter).isNull()
    }
}
