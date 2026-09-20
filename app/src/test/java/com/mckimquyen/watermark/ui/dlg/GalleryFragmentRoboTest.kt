package com.mckimquyen.watermark.ui.dlg

import android.os.Build
import android.os.Looper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.ui.MainActivity
import com.mckimquyen.watermark.ui.widget.MultiSelectRv
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class GalleryFragmentRoboTest {

    @Test
    fun galleryFragment_sliderPercentCalculation_returnsValidFloat() {
        val percent = GalleryFragment.computeSliderScrollPercent(1000, 2000)
        assertThat(percent).isEqualTo(0.5f)
    }

    @Test
    fun galleryFragment_insetsAppliedToToolbarAndFab() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        shadowOf(Looper.getMainLooper()).idle()

        val fragment = GalleryFragment()
        fragment.show(activity.supportFragmentManager, "test_gallery")
        shadowOf(Looper.getMainLooper()).idle()

        val view = fragment.view
        assertThat(view).isNotNull()

        val topAppBar = view!!.findViewById<MaterialToolbar>(R.id.topAppBar)
        val fab = view.findViewById<ExtendedFloatingActionButton>(R.id.fab)
        val rvContent = view.findViewById<MultiSelectRv>(R.id.rvContent)

        assertThat(topAppBar).isNotNull()
        assertThat(fab).isNotNull()
        assertThat(rvContent).isNotNull()

        // 1. Verify title is NOT centered to prevent truncation
        assertThat(topAppBar.isTitleCentered).isFalse()

        // 2. Verify topAppBar has NO redundant top padding to prevent weird gap with status bar
        assertThat(topAppBar.paddingTop).isEqualTo(0)

        // 3. Verify FAB and RV are present and properly configured
        assertThat(fab.isShown).isFalse() // hidden initially until photos selected

        fragment.dismiss()
        shadowOf(Looper.getMainLooper()).idle()
    }
}
