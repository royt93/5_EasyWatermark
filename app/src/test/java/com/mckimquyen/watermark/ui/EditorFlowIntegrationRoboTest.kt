package com.mckimquyen.watermark.ui

import android.graphics.Color
import android.os.Looper
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.ui.adapter.FuncPanelAdapter
import com.mckimquyen.watermark.ui.widget.LaunchView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class EditorFlowIntegrationRoboTest {

    @Test
    fun launchToEditor_transitionsStateAndInitializesFunctionalPanels() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().get()
        shadowOf(Looper.getMainLooper()).idle()

        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
        val launchView = contentRoot.getChildAt(0) as LaunchView
        assertThat(launchView).isNotNull()
        assertThat(launchView.mode).isEqualTo(LaunchView.ViewMode.LaunchMode)

        // Simulate entering editor mode
        launchView.toEditorMode()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(launchView.mode).isEqualTo(LaunchView.ViewMode.Editor)
        assertThat(launchView.tabLayout.tabCount).isEqualTo(3)

        // Verify tabs
        val tab0 = launchView.tabLayout.getTabAt(0)?.text?.toString()
        val tab1 = launchView.tabLayout.getTabAt(1)?.text?.toString()
        val tab2 = launchView.tabLayout.getTabAt(2)?.text?.toString()

        assertThat(tab0).isNotEmpty()
        assertThat(tab1).isNotEmpty()
        assertThat(tab2).isNotEmpty()

        // Verify FuncPanelAdapter contrast against surface container
        val adapter = launchView.rvPanel.adapter as? FuncPanelAdapter
        assertThat(adapter).isNotNull()
        assertThat(adapter!!.itemCount).isGreaterThan(0)

        val surfaceContainer = MaterialColors.getColor(
            activity,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            Color.LTGRAY
        )
        val contrast = ColorUtils.calculateContrast(adapter.textColor, surfaceContainer)
        assertThat(contrast).isAtLeast(3.0)
    }
}
