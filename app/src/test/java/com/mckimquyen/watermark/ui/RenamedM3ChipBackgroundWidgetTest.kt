package com.mckimquyen.watermark.ui

import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * activity_vip_management.xml (ivCrown) va layout_ad_banner.xml (tvLabelAd) la 2 layout duy
 * nhat dung background bg_glass_button (doi ten thanh bg_chip_selectable) ma chua co test nao
 * khac inflate toi - cac cho con lai (item_text_effect/item_typeface_style qua adapter,
 * dlg_exif_border, item_signature_history) da duoc cover gian tiep boi test co san.
 */
@RunWith(RobolectricTestRunner::class)
class RenamedM3ChipBackgroundWidgetTest {

    private val themedContext by lazy {
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)
    }
    private val inflater by lazy { LayoutInflater.from(themedContext) }

    @Test
    fun vipManagement_ivCrown_resolvesRenamedChipBackground() {
        val root = inflater.inflate(R.layout.activity_vip_management, null)
        val ivCrown = root.findViewById<ImageView>(R.id.ivCrown)
        assertThat(ivCrown).isNotNull()
        assertThat(shadowOf(ivCrown.background).createdFromResId)
            .isEqualTo(R.drawable.bg_chip_selectable)
    }

    @Test
    fun adBanner_tvLabelAd_resolvesRenamedChipBackground() {
        val root = inflater.inflate(R.layout.layout_ad_banner, null)
        val tvLabelAd = root.findViewById<TextView>(R.id.tvLabelAd)
        assertThat(tvLabelAd).isNotNull()
        assertThat(shadowOf(tvLabelAd.background).createdFromResId)
            .isEqualTo(R.drawable.bg_chip_selectable)
    }
}
