package com.mckimquyen.watermark.ui

import android.os.Build
import android.view.View
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.ui.widget.SignatureView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit & Integration Tests for SignatureActivity:
 * - Activity lifecycle & View hierarchy
 * - Action Bar icon tint consistency (ivUndo, ivClear)
 * - Slider brush size dynamic TalkBack announcements
 * - Neon glow toggle dynamic TalkBack announcements
 * - Clear and Undo actions visibility states
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class SignatureActivityRoboTest {

    @Test
    fun signatureActivity_lifecycleAndControlsIntegration() {
        val controller = Robolectric.buildActivity(SignatureActivity::class.java).setup()
        val activity = controller.get()

        val ivUndo = activity.findViewById<MaterialButton>(R.id.ivUndo)
        val ivClear = activity.findViewById<MaterialButton>(R.id.ivClear)
        val sbSize = activity.findViewById<Slider>(R.id.sbSize)
        val swGlow = activity.findViewById<MaterialSwitch>(R.id.swGlow)
        val tvEmptyHint = activity.findViewById<View>(R.id.tvEmptyHint)
        val signatureView = activity.findViewById<SignatureView>(R.id.signatureView)

        // 1. Action Bar icon tints & descriptions
        assertThat(ivUndo.iconTint).isNotNull()
        assertThat(ivClear.iconTint).isNotNull()
        assertThat(ivUndo.iconTint).isEqualTo(ivClear.iconTint)
        assertThat(ivUndo.contentDescription?.toString()).isEqualTo(activity.getString(R.string.action_undo))
        assertThat(ivClear.contentDescription?.toString()).isEqualTo(activity.getString(R.string.action_clear_canvas))

        // 2. Slider brush size updates drawSize and dynamic contentDescription
        assertThat(sbSize.isFocusable).isTrue()
        val initialSize = sbSize.value.toInt()
        assertThat(sbSize.contentDescription?.toString())
            .isEqualTo(activity.getString(R.string.brush_size_format, initialSize))
        assertThat(signatureView.drawSize).isEqualTo(sbSize.value)

        sbSize.value = 25f
        assertThat(signatureView.drawSize).isEqualTo(25f)
        assertThat(sbSize.contentDescription?.toString())
            .isEqualTo(activity.getString(R.string.brush_size_format, 25))

        // 3. Switch glow updates dynamic contentDescription
        assertThat(swGlow.contentDescription?.toString()).contains(activity.getString(R.string.neon_glow))
        swGlow.isChecked = true
        assertThat(signatureView.isGlowEnabled).isTrue()
        assertThat(swGlow.contentDescription?.toString())
            .isEqualTo("${activity.getString(R.string.neon_glow)}, ${activity.getString(R.string.state_enabled)}")

        swGlow.isChecked = false
        assertThat(signatureView.isGlowEnabled).isFalse()
        assertThat(swGlow.contentDescription?.toString())
            .isEqualTo("${activity.getString(R.string.neon_glow)}, ${activity.getString(R.string.state_disabled)}")

        // 4. Clear action shows empty hint
        tvEmptyHint.visibility = View.GONE
        ivClear.performClick()
        assertThat(tvEmptyHint.visibility).isEqualTo(View.VISIBLE)

        controller.pause().stop().destroy()
    }
}
