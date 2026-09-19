package com.mckimquyen.watermark.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.slider.Slider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.ui.panel.AlphaPbFragment
import com.mckimquyen.watermark.ui.panel.DegreePbFragment
import com.mckimquyen.watermark.ui.panel.HorizonPbFragment
import com.mckimquyen.watermark.ui.panel.TextSizePbFragment
import com.mckimquyen.watermark.ui.panel.VerticalPbFragment
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PbSliderAndGalleryWidgetTest {

    private val themedContext by lazy {
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_MyApp
        )
    }

    private val inflater by lazy { LayoutInflater.from(themedContext) }

    @Test
    fun basePbLayout_inflatesWithVisibleProgressTextViewAndSlider() {
        val root = inflater.inflate(R.layout.f_base_pb, null)
        assertThat(root).isNotNull()

        val slider = root.findViewById<Slider>(R.id.slideContentSize)
        val tvProgress = root.findViewById<TextView>(R.id.tvProgressVertical)

        assertThat(slider).isNotNull()
        assertThat(tvProgress).isNotNull()
        assertThat(tvProgress.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun galleryLayout_inflatesWithEmptyStateContainerAndList() {
        val root = inflater.inflate(R.layout.f_gallery, null)
        assertThat(root).isNotNull()

        val rv = root.findViewById<View>(R.id.rvContent)
        val emptyState = root.findViewById<LinearLayout>(R.id.llEmptyState)

        assertThat(rv).isNotNull()
        assertThat(emptyState).isNotNull()

        val emptyText = emptyState.findViewById<TextView>(android.R.id.text1) ?: emptyState.getChildAt(1) as TextView
        assertThat(emptyText).isNotNull()
        assertThat(emptyText.text.toString()).isEqualTo(themedContext.getString(R.string.tips_list_empty))
    }

    @Test
    fun itemSavingImage_inflatesWithProgressImageViewAndPreviewBadge() {
        val root = inflater.inflate(R.layout.item_saving_image, null)
        assertThat(root).isNotNull()

        val ivIcon = root.findViewById<View>(R.id.ivIcon)
        val ivDone = root.findViewById<ImageView>(R.id.ivDone)
        val tvPreviewInfo = root.findViewById<TextView>(R.id.tvPreviewInfo)

        assertThat(ivIcon).isNotNull()
        assertThat(ivDone).isNotNull()
        assertThat(tvPreviewInfo).isNotNull()
    }

    @Test
    fun sliderFragments_formatValueTips_produceCrispUnits() {
        val alphaFrag = AlphaPbFragment()
        val degreeFrag = DegreePbFragment()
        val textSizeFrag = TextSizePbFragment()
        val horizonFrag = HorizonPbFragment()
        val verticalFrag = VerticalPbFragment()

        val wm = WaterMark(
            text = "Test",
            textSize = 24f,
            textColor = android.graphics.Color.WHITE,
            textStyle = com.mckimquyen.watermark.data.model.TextPaintStyle.Fill,
            textTypeface = com.mckimquyen.watermark.data.model.TextTypeface.Normal,
            alpha = 128,
            degree = 45f,
            hGap = 16,
            vGap = 32,
            iconUri = android.net.Uri.EMPTY,
            markMode = com.mckimquyen.watermark.data.repo.WaterMarkRepository.MarkMode.Text,
            enableBounds = false
        )

        assertThat(alphaFrag.formatValueTips(wm)).isEqualTo("50%")
        assertThat(degreeFrag.formatValueTips(wm)).isEqualTo("45°")
        assertThat(textSizeFrag.formatValueTips(wm)).isEqualTo("24 sp")
        assertThat(horizonFrag.formatValueTips(wm)).isEqualTo("16 px")
        assertThat(verticalFrag.formatValueTips(wm)).isEqualTo("32 px")
    }
}
