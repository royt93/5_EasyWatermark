package com.mckimquyen.watermark.ui

import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.widget.ContentLoadingProgressBar
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Comprehensive Widget & Accessibility Tests for:
 * 1. Signature Studio & Sheet (activity_signature.xml, f_signature_bottom_sheet.xml, item_signature_history.xml)
 * 2. Floating Panels Style & Tile Mode (f_tile_mode.xml)
 * 3. VIP Management & Compress Dialog (activity_vip_management.xml, dlg_compress_img.xml)
 *
 * Verifies Material 3 compliance, touch targets >= 48dp, and TalkBack accessibility attributes.
 */
@RunWith(RobolectricTestRunner::class)
class SignatureAndPanelsAccessibilityWidgetTest {

    private val themedContext by lazy {
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_MyApp
        )
    }

    // ----------------------------------------------------------------------------------
    // Option 1: Signature Studio & Sheet Tests
    // ----------------------------------------------------------------------------------

    @Test
    fun signatureActivity_hasAccessibleActionButtonsAndSlider() {
        val root = LayoutInflater.from(themedContext).inflate(R.layout.activity_signature, null, false)

        val ivUndo = root.findViewById<MaterialButton>(R.id.ivUndo)
        val ivClear = root.findViewById<MaterialButton>(R.id.ivClear)
        val sbSize = root.findViewById<Slider>(R.id.sbSize)
        val swGlow = root.findViewById<MaterialSwitch>(R.id.swGlow)
        val btnApply = root.findViewById<MaterialButton>(R.id.btnApply)

        assertThat(ivUndo).isNotNull()
        assertThat(ivUndo.contentDescription?.toString()).isNotEmpty()
        assertThat(ivUndo.layoutParams.width).isAtLeast(48)
        assertThat(ivUndo.layoutParams.height).isAtLeast(48)

        assertThat(ivClear).isNotNull()
        assertThat(ivClear.contentDescription?.toString()).isNotEmpty()
        assertThat(ivClear.layoutParams.width).isAtLeast(48)
        assertThat(ivClear.layoutParams.height).isAtLeast(48)

        assertThat(sbSize).isNotNull()
        assertThat(sbSize.isFocusable).isTrue()
        assertThat(sbSize.contentDescription?.toString()).isNotEmpty()

        assertThat(swGlow).isNotNull()
        assertThat(swGlow.contentDescription?.toString()).isNotEmpty()

        assertThat(btnApply).isNotNull()
        assertThat(btnApply.layoutParams.height).isAtLeast(48)
    }

    @Test
    fun signatureBottomSheet_hasAccessibleButtonsAndCanvas() {
        val parent = FrameLayout(themedContext)
        val root = LayoutInflater.from(themedContext).inflate(R.layout.f_signature_bottom_sheet, parent, false)

        val btnClear = root.findViewById<MaterialButton>(R.id.btnClear)
        val btnSaveSignature = root.findViewById<MaterialButton>(R.id.btnSaveSignature)

        assertThat(btnClear).isNotNull()
        assertThat(btnClear.minimumHeight).isAtLeast(48)
        assertThat(btnClear.minimumWidth).isAtLeast(48)

        assertThat(btnSaveSignature).isNotNull()
        assertThat(btnSaveSignature.layoutParams.height).isAtLeast(48)
    }

    @Test
    fun signatureHistoryItem_hasAccessibleTouchTargetsAndLabels() {
        val root = LayoutInflater.from(themedContext).inflate(R.layout.item_signature_history, null, false)

        val ivDeleteBtn = root.findViewById<ImageView>(R.id.ivDeleteBtn)

        assertThat(root).isNotNull()
        assertThat(root.contentDescription?.toString()).isNotEmpty()
        assertThat(root.isFocusable).isTrue()
        assertThat(root.isClickable).isTrue()

        assertThat(ivDeleteBtn).isNotNull()
        assertThat(ivDeleteBtn.contentDescription?.toString()).isNotEmpty()
    }

    // ----------------------------------------------------------------------------------
    // Option 2: Floating Panels Style & Tile Mode Tests
    // ----------------------------------------------------------------------------------

    @Test
    fun tileModePanel_hasAccessibleButtonsAndContainerHeight() {
        val parent = FrameLayout(themedContext)
        val root = LayoutInflater.from(themedContext).inflate(R.layout.f_tile_mode, parent, false)

        val btnTileModeRepeat = root.findViewById<MaterialButton>(R.id.btnTileModeRepeat)
        val btnTileModeDecal = root.findViewById<MaterialButton>(R.id.btnTileModeDecal)
        val btnPositionAnchor = root.findViewById<MaterialButton>(R.id.btnPositionAnchor)

        assertThat(root.layoutParams.height).isAtLeast(56)

        assertThat(btnTileModeRepeat).isNotNull()
        assertThat(btnTileModeRepeat.minimumHeight).isAtLeast(48)
        assertThat(btnTileModeRepeat.minimumWidth).isAtLeast(48)

        assertThat(btnTileModeDecal).isNotNull()
        assertThat(btnTileModeDecal.minimumHeight).isAtLeast(48)
        assertThat(btnTileModeDecal.minimumWidth).isAtLeast(48)

        assertThat(btnPositionAnchor).isNotNull()
        assertThat(btnPositionAnchor.minimumHeight).isAtLeast(48)
        assertThat(btnPositionAnchor.minimumWidth).isAtLeast(48)
    }

    // ----------------------------------------------------------------------------------
    // Option 3: VIP Management & Compress Dialog Tests
    // ----------------------------------------------------------------------------------

    @Test
    fun vipManagementActivity_hasAccessibleButtonsAndPrivacyFooter() {
        val root = LayoutInflater.from(themedContext).inflate(R.layout.activity_vip_management, null, false)

        val btnRevoke = root.findViewById<MaterialButton>(R.id.btnRevoke)
        val btnBuy30 = root.findViewById<MaterialButton>(R.id.btnBuy30)
        val btnBuy90 = root.findViewById<MaterialButton>(R.id.btnBuy90)
        val btnBuy1y = root.findViewById<MaterialButton>(R.id.btnBuy1y)
        val btnBuyLifetime = root.findViewById<MaterialButton>(R.id.btnBuyLifetime)
        val btnRestore = root.findViewById<MaterialButton>(R.id.btnRestore)
        val tvPrivacy = root.findViewById<TextView>(R.id.tvPrivacy)

        assertThat(btnRevoke).isNotNull()
        assertThat(btnRevoke.minimumHeight).isAtLeast(48)

        assertThat(btnBuy30).isNotNull()
        assertThat(btnBuy30.minimumHeight).isAtLeast(48)

        assertThat(btnBuy90).isNotNull()
        assertThat(btnBuy90.minimumHeight).isAtLeast(48)

        assertThat(btnBuy1y).isNotNull()
        assertThat(btnBuy1y.minimumHeight).isAtLeast(48)

        assertThat(btnBuyLifetime).isNotNull()
        assertThat(btnBuyLifetime.minimumHeight).isAtLeast(48)

        assertThat(btnRestore).isNotNull()
        assertThat(btnRestore.minimumHeight).isAtLeast(48)

        assertThat(tvPrivacy).isNotNull()
        assertThat(tvPrivacy.minimumHeight).isAtLeast(48)
        assertThat(tvPrivacy.contentDescription?.toString()).isNotEmpty()
    }

    @Test
    fun compressImageDialog_hasAccessibleProgressBarAndButtons() {
        val root = LayoutInflater.from(themedContext).inflate(R.layout.dlg_compress_img, null, false)

        val cpbCompress = root.findViewById<ContentLoadingProgressBar>(R.id.cpbCompress)
        val tvCompressTips = root.findViewById<TextView>(R.id.tvCompressTips)
        val btnCompress = root.findViewById<MaterialButton>(R.id.btnCompress)
        val btnCancel = root.findViewById<MaterialButton>(R.id.btnCancel)

        assertThat(cpbCompress).isNotNull()
        assertThat(cpbCompress.contentDescription?.toString()).isNotEmpty()

        assertThat(tvCompressTips).isNotNull()

        assertThat(btnCompress).isNotNull()
        assertThat(btnCompress.layoutParams.height).isAtLeast(48)

        assertThat(btnCancel).isNotNull()
        assertThat(btnCancel.minimumHeight).isAtLeast(48)
        assertThat(btnCancel.minimumWidth).isAtLeast(48)
    }

    @Test
    fun signatureActivity_toolbar_hasAccessibleNavigation() {
        val root = LayoutInflater.from(themedContext).inflate(R.layout.activity_signature, null, false)
        val toolbar = root.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        assertThat(toolbar).isNotNull()
        assertThat(toolbar.navigationContentDescription?.toString()).isEqualTo(themedContext.getString(R.string.back))
    }

    @Test
    fun vipManagementActivity_toolbar_and_crown_areAccessible() {
        val root = LayoutInflater.from(themedContext).inflate(R.layout.activity_vip_management, null, false)
        val topAppBar = root.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        val ivCrown = root.findViewById<ImageView>(R.id.ivCrown)
        val edtVipKey = root.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtVipKey)

        assertThat(topAppBar).isNotNull()
        assertThat(topAppBar.navigationContentDescription?.toString()).isEqualTo(themedContext.getString(R.string.back))

        assertThat(ivCrown).isNotNull()
        assertThat(ivCrown.contentDescription?.toString()).isEqualTo(themedContext.getString(R.string.vip_title))

        assertThat(edtVipKey).isNotNull()
        assertThat(edtVipKey.minimumHeight).isAtLeast(48)
    }

    @Test
    fun openSourceActivity_toolbar_hasAccessibleNavigation() {
        val root = LayoutInflater.from(themedContext).inflate(R.layout.a_open_source, null, false)
        val toolbar = root.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.myToolbar)
        assertThat(toolbar).isNotNull()
        assertThat(toolbar.navigationContentDescription?.toString()).isEqualTo(themedContext.getString(R.string.close))
    }

    @Test
    fun aboutActivity_toolbar_and_openSourceRow_areAccessible() {
        val root = LayoutInflater.from(themedContext).inflate(R.layout.a_about, null, false)
        val topAppBar = root.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        val rowOpenSource = root.findViewById<android.view.View>(R.id.rowOpenSource)

        assertThat(topAppBar).isNotNull()
        assertThat(topAppBar.navigationContentDescription?.toString()).isEqualTo(themedContext.getString(R.string.close))

        assertThat(rowOpenSource).isNotNull()
        assertThat(rowOpenSource.isClickable).isTrue()
        assertThat(rowOpenSource.layoutParams.height).isAtLeast(48)
    }
}
