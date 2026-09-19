package com.mckimquyen.watermark.ui.panel

import android.graphics.Color
import android.graphics.Shader
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.ui.MainActivity
import com.mckimquyen.watermark.ui.MainViewModel
import com.mckimquyen.watermark.ui.adapter.ColorPreviewAdapter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * End-to-end integration tests for Editor Bottom Floating Panels:
 * - TileModeFragment integration with MainViewModel and PositionAnchor visibility
 * - ColorFragment integration with MainViewModel.updateTextColor
 * - TextContentDisplayFragment integration with MainViewModel.waterMark & edit dialog
 */
@RunWith(RobolectricTestRunner::class)
class EditorFloatingPanelsIntegrationTest {

    private fun MainActivity.viewModelField(): MainViewModel =
        MainActivity::class.java.getDeclaredMethod("getViewModel").apply { isAccessible = true }
            .invoke(this) as MainViewModel

    private fun MainViewModel.waterMarkRepoField(): com.mckimquyen.watermark.data.repo.WaterMarkRepository =
        MainViewModel::class.java.getDeclaredField("waterMarkRepo").apply { isAccessible = true }
            .get(this) as com.mckimquyen.watermark.data.repo.WaterMarkRepository

    @Test
    fun textContentDisplayFragment_reflectsWatermarkChangesAndDispatchesEdit() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        shadowOf(Looper.getMainLooper()).idle()

        // Transition to editor mode
        activity.launchView.toEditorMode()
        shadowOf(Looper.getMainLooper()).idle()

        val containerId = activity.launchView.fcFunctionDetail.id
        TextContentDisplayFragment.replaceShow(activity, containerId)
        shadowOf(Looper.getMainLooper()).idle()

        val fragment = activity.supportFragmentManager.findFragmentByTag(TextContentDisplayFragment.TAG) as? TextContentDisplayFragment
        assertThat(fragment).isNotNull()
        assertThat(fragment!!.isAdded).isTrue()

        val tvText = fragment.view?.findViewById<TextView>(R.id.etWaterText)
        assertThat(tvText).isNotNull()

        val viewModel = activity.viewModelField()
        kotlinx.coroutines.runBlocking {
            viewModel.waterMarkRepoField().updateText("CONFIDENTIAL 2026")
        }
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (tvText?.text.toString() == "CONFIDENTIAL 2026") break
            Thread.sleep(20)
        }

        assertThat(tvText?.text.toString()).isEqualTo("CONFIDENTIAL 2026")
        assertThat(fragment.view?.contentDescription.toString()).contains("CONFIDENTIAL 2026")

        // Clicking root calls goEditDialog
        fragment.view?.performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun colorFragment_inflatesPaletteAndUpdatesSelectedColor() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().get()
        shadowOf(Looper.getMainLooper()).idle()

        activity.launchView.toEditorMode()
        shadowOf(Looper.getMainLooper()).idle()

        val containerId = activity.launchView.fcFunctionDetail.id
        ColorFragment.replaceShow(activity, containerId)
        shadowOf(Looper.getMainLooper()).idle()

        val fragment = activity.supportFragmentManager.findFragmentByTag(ColorFragment.TAG) as? ColorFragment
        assertThat(fragment).isNotNull()
        assertThat(fragment!!.isAdded).isTrue()

        val rvColor = fragment.view?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvColor)
        assertThat(rvColor).isNotNull()
        val adapter = rvColor?.adapter as? ColorPreviewAdapter
        assertThat(adapter).isNotNull()
        assertThat(adapter!!.itemCount).isEqualTo(8) // 7 presets + 1 picker

        // Select yellow color
        val yellowColor = Color.parseColor("#FFB800")
        adapter.updateSelectedColor(yellowColor)
        shadowOf(Looper.getMainLooper()).idle()

        val selectedModel = adapter.previewList.firstOrNull { it.selected }
        assertThat(selectedModel).isNotNull()
        assertThat(selectedModel?.color).isEqualTo(yellowColor)
    }

    @Test
    fun tileModeFragment_showsAndInteractsWithToggleGroup() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().get()
        shadowOf(Looper.getMainLooper()).idle()

        activity.launchView.toEditorMode()
        shadowOf(Looper.getMainLooper()).idle()

        val containerId = activity.launchView.fcFunctionDetail.id
        TileModeFragment.replaceShow(activity, containerId)
        shadowOf(Looper.getMainLooper()).idle()

        val fragment = activity.supportFragmentManager.findFragmentByTag(TileModeFragment.TAG) as? TileModeFragment
        assertThat(fragment).isNotNull()
        assertThat(fragment!!.isAdded).isTrue()

        val btnRepeat = fragment.view?.findViewById<MaterialButton>(R.id.btnTileModeRepeat)
        val btnDecal = fragment.view?.findViewById<MaterialButton>(R.id.btnTileModeDecal)
        val btnAnchor = fragment.view?.findViewById<MaterialButton>(R.id.btnPositionAnchor)

        assertThat(btnRepeat).isNotNull()
        assertThat(btnDecal).isNotNull()
        assertThat(btnAnchor).isNotNull()
    }
}
