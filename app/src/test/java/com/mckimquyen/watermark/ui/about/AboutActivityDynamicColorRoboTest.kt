package com.mckimquyen.watermark.ui.about

import android.os.Build
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AboutActivityDynamicColorRoboTest {

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun aboutActivity_onAndroid11_dynamicColorIsDisabledWithUnsupportedSubtitle() {
        val controller = Robolectric.buildActivity(AboutActivity::class.java).setup()
        val activity = controller.get()

        val switchDynamicColor = activity.findViewById<MaterialSwitch>(R.id.switchDynamicColor)
        val tvDynamicColorStatus = activity.findViewById<TextView>(R.id.tvDynamicColorStatus)

        assertThat(switchDynamicColor).isNotNull()
        assertThat(tvDynamicColorStatus).isNotNull()

        // Android 11 (< 12) must disable switch and display requirement subtitle
        assertThat(switchDynamicColor.isEnabled).isFalse()
        assertThat(switchDynamicColor.isChecked).isFalse()
        assertThat(tvDynamicColorStatus.text.toString()).isEqualTo(
            activity.getString(R.string.dynamic_color_subtitle_unsupported)
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun aboutActivity_onAndroid14_rowDynamicColorIsVisibleWithSubtitle() {
        val controller = Robolectric.buildActivity(AboutActivity::class.java).setup()
        val activity = controller.get()

        val rowDynamicColor = activity.findViewById<android.view.View>(R.id.rowDynamicColor)
        val switchDynamicColor = activity.findViewById<MaterialSwitch>(R.id.switchDynamicColor)
        val tvDynamicColorStatus = activity.findViewById<TextView>(R.id.tvDynamicColorStatus)

        assertThat(rowDynamicColor).isNotNull()
        assertThat(switchDynamicColor).isNotNull()
        assertThat(tvDynamicColorStatus).isNotNull()

        assertThat(tvDynamicColorStatus.text.toString()).isNotEmpty()
    }
}
