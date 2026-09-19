package com.mckimquyen.watermark.ui.panel

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.ui.adapter.ColorPreviewAdapter
import com.mckimquyen.watermark.ui.adapter.TextEffectAdapter
import com.mckimquyen.watermark.ui.adapter.TextPaintStyleAdapter
import com.mckimquyen.watermark.ui.adapter.TextTypefaceAdapter
import com.mckimquyen.watermark.ui.widget.SelectableImageView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Widget tests for Editor Floating Bottom Tool Panels:
 * - Color Panel (f_color, item_color_preview, ColorPreviewAdapter accessibility & touch targets)
 * - Tile Mode Panel (f_tile_mode, toggle button contrast & touch targets, btnPositionAnchor)
 * - Text Content Display Panel (f_text_content_display, accessibility & clickability)
 * - Text Style Items (item_text_effect, item_typeface_style accessibility & touch targets)
 */
@RunWith(RobolectricTestRunner::class)
class EditorFloatingPanelsWidgetTest {

    private val context by lazy {
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_MyApp
        )
    }

    @Test
    fun itemColorPreview_touchTargetAndAccessibilityDescription() {
        val root = LayoutInflater.from(context).inflate(R.layout.item_color_preview, null, false) as FrameLayout
        val siv = root.findViewById<SelectableImageView>(R.id.sivColor)

        assertThat(siv).isNotNull()
        // Swatch view is 32dp + 8dp padding on both sides = 48dp min touch target
        assertThat(root.paddingLeft).isAtLeast(8)
        assertThat(root.paddingRight).isAtLeast(8)

        // Test adapter binding with accessibility description
        val previewList = arrayListOf(
            ColorPreviewAdapter.PreViewModel(
                color = Color.WHITE,
                selected = true,
                contentDescription = "White"
            ),
            ColorPreviewAdapter.PreViewModel(
                color = Color.BLACK,
                selected = false,
                contentDescription = "Black"
            ),
            ColorPreviewAdapter.PreViewModel(
                type = ColorPreviewAdapter.PreviewType.Res,
                resId = R.drawable.ic_btn_color_picker,
                contentDescription = "Custom color"
            )
        )
        val adapter = ColorPreviewAdapter(previewList)
        val holder = adapter.onCreateViewHolder(root, 0)

        // Bind first item (selected white)
        adapter.onBindViewHolder(holder, 0)
        assertThat(holder.itemView.contentDescription.toString()).contains("White")
        assertThat(holder.itemView.contentDescription.toString()).contains(context.getString(R.string.state_enabled))

        // Bind second item (unselected black)
        adapter.onBindViewHolder(holder, 1)
        assertThat(holder.itemView.contentDescription.toString()).isEqualTo("Black")

        // Bind third item (custom color picker)
        adapter.onBindViewHolder(holder, 2)
        assertThat(holder.itemView.contentDescription.toString()).isEqualTo("Custom color")
    }

    @Test
    fun tileModeLayout_touchTargetsAndButtonStyling() {
        val root = LayoutInflater.from(context).inflate(R.layout.f_tile_mode, null, false)
        val toggleGroup = root.findViewById<MaterialButtonToggleGroup>(R.id.tgTileMode)
        val btnRepeat = root.findViewById<MaterialButton>(R.id.btnTileModeRepeat)
        val btnDecal = root.findViewById<MaterialButton>(R.id.btnTileModeDecal)
        val btnAnchor = root.findViewById<MaterialButton>(R.id.btnPositionAnchor)

        assertThat(toggleGroup).isNotNull()
        assertThat(btnRepeat).isNotNull()
        assertThat(btnDecal).isNotNull()
        assertThat(btnAnchor).isNotNull()

        // Minimum button dimensions for tactile feedback
        assertThat(btnRepeat.minHeight).isAtLeast(40)
        assertThat(btnRepeat.minWidth).isAtLeast(64)
        assertThat(btnDecal.minHeight).isAtLeast(40)
        assertThat(btnDecal.minWidth).isAtLeast(64)

        // Position anchor button has icon and minimum touch target
        assertThat(btnAnchor.icon).isNotNull()
        assertThat(btnAnchor.minHeight).isAtLeast(40)
        assertThat(btnAnchor.minWidth).isAtLeast(48)
        assertThat(btnAnchor.text.toString()).isEqualTo(context.getString(R.string.position_anchor_button))
    }

    @Test
    fun textContentDisplay_clickableFocusableAndAccessibility() {
        val root = LayoutInflater.from(context).inflate(R.layout.f_text_content_display, null, false)
        val tvText = root.findViewById<TextView>(R.id.etWaterText)
        val cursor = root.findViewById<View>(R.id.blinkCursor)

        assertThat(root.isClickable).isTrue()
        assertThat(root.isFocusable).isTrue()
        assertThat(tvText).isNotNull()
        assertThat(cursor).isNotNull()
    }

    @Test
    fun textEffectAdapter_announcesStateChange() {
        val effects = listOf(
            TextEffectAdapter.TextEffectModel("Stroke", false),
            TextEffectAdapter.TextEffectModel("Shadow", true)
        )
        val adapter = TextEffectAdapter(effects)
        val parent = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)

        // Disabled item
        adapter.onBindViewHolder(holder, 0)
        assertThat(holder.root.contentDescription.toString()).contains("Stroke")
        assertThat(holder.root.contentDescription.toString()).contains(context.getString(R.string.state_disabled))

        // Enabled item
        adapter.onBindViewHolder(holder, 1)
        assertThat(holder.root.contentDescription.toString()).contains("Shadow")
        assertThat(holder.root.contentDescription.toString()).contains(context.getString(R.string.state_enabled))
    }

    @Test
    fun textPaintAndTypefaceAdapters_announceAccessibleDescriptions() {
        // Paint Style Adapter
        val paintList = TextPaintStyleAdapter.obtainDefaultPaintStyleList(context)
        val paintAdapter = TextPaintStyleAdapter(paintList)
        val parent = FrameLayout(context)
        val paintHolder = paintAdapter.onCreateViewHolder(parent, 0)
        paintAdapter.onBindViewHolder(paintHolder, 0)
        assertThat(paintHolder.itemView.contentDescription.toString()).contains(context.getString(R.string.text_paint_fill))
        assertThat(paintHolder.itemView.contentDescription.toString()).contains(context.getString(R.string.state_enabled))

        // Typeface Adapter
        val typefaceList = TextTypefaceAdapter.obtainDefaultTypefaceList(context)
        val typefaceAdapter = TextTypefaceAdapter(typefaceList)
        val typefaceHolder = typefaceAdapter.onCreateViewHolder(parent, 0)
        typefaceAdapter.onBindViewHolder(typefaceHolder, 0)
        assertThat(typefaceHolder.itemView.contentDescription.toString()).contains(context.getString(R.string.text_typeface_normal))
        assertThat(typefaceHolder.itemView.contentDescription.toString()).contains(context.getString(R.string.state_enabled))
    }
}
