package com.mckimquyen.watermark.ui.dlg

import android.content.DialogInterface
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.slider.Slider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.ui.MainActivity
import com.mckimquyen.watermark.ui.MainViewModel
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** FEAT-18 / ENH-47: compare slider displays shared state, updates label, dismiss resets reveal. */
@RunWith(RobolectricTestRunner::class)
class CompareBottomSheetFragmentWidgetTest {

    private data class Host(val activity: MainActivity, val fragment: CompareBottomSheetFragment, val viewModel: MainViewModel)

    private fun host(initialReveal: Float = 1f): Host {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
        viewModel.updateCompareReveal(initialReveal)
        val fragment = CompareBottomSheetFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction().add(fragment, CompareBottomSheetFragment.TAG).commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return Host(activity, fragment, viewModel)
    }

    @Test
    fun initialState_setsSliderAndPercentageLabel() {
        val (_, fragment) = host(initialReveal = 0.42f)
        val root = requireNotNull(fragment.view)

        assertThat(root.findViewById<Slider>(R.id.slCompare).value).isEqualTo(42f)
        assertThat(root.findViewById<android.widget.TextView>(R.id.tvValue).text.toString()).contains("42")
    }

    @Test
    fun sliderValueChange_updatesVisiblePercentageLabel() {
        val (_, fragment) = host()
        val root = requireNotNull(fragment.view)

        root.findViewById<Slider>(R.id.slCompare).value = 35f

        assertThat(root.findViewById<android.widget.TextView>(R.id.tvValue).text.toString()).contains("35")
    }

    @Test
    fun dismiss_resetsSharedRevealToFullImage() {
        val (_, fragment, viewModel) = host(initialReveal = 0.35f)

        fragment.onDismiss(mockk<DialogInterface>(relaxed = true))

        assertThat(viewModel.compareReveal.value).isEqualTo(1f)
    }
}
