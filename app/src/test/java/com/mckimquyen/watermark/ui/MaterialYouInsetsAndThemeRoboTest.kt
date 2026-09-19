package com.mckimquyen.watermark.ui

import android.graphics.Color
import android.graphics.Insets
import android.os.Build
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.Anchor
import com.mckimquyen.watermark.ui.about.AboutActivity
import com.mckimquyen.watermark.ui.dlg.PositionAnchorBottomSheetFragment
import com.mckimquyen.watermark.ui.widget.LaunchView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class MaterialYouInsetsAndThemeRoboTest {

    @Test
    fun launchView_m3CardsAndTabLayoutPillIndicatorInitializedCorrectly() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()

        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
        val launchView = contentRoot.getChildAt(0) as LaunchView

        // 1. Verify Home action cards
        assertThat(launchView.ivSelectedPhotoTips).isInstanceOf(MaterialCardView::class.java)
        assertThat(launchView.ivGoAboutPage).isInstanceOf(MaterialCardView::class.java)

        // 2. Verify TabLayout pill indicator setup
        val tabLayout = launchView.tabLayout
        assertThat(tabLayout.tabCount).isEqualTo(3)
        assertThat(tabLayout.tabIndicatorAnimationMode).isEqualTo(com.google.android.material.tabs.TabLayout.INDICATOR_ANIMATION_MODE_ELASTIC)
        assertThat(tabLayout.isTabIndicatorFullWidth).isFalse()

        controller.pause().stop().destroy()
    }

    @Test
    fun aboutActivity_appliesTopAndBottomInsetsCorrectly() {
        val controller = Robolectric.buildActivity(AboutActivity::class.java).create().start().resume()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()

        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val topAppBar = activity.findViewById<MaterialToolbar>(R.id.topAppBar)
        val appBarLayout = activity.findViewById<AppBarLayout>(R.id.appBarLayout)
        val nestedScrollView = activity.findViewById<View>(R.id.nestedScrollView)

        assertThat(topAppBar).isNotNull()
        assertThat(appBarLayout).isNotNull()
        assertThat(nestedScrollView).isNotNull()

        // Simulate dispatch of window insets (statusBarTop=80, navBarBottom=120)
        val mockInsets = WindowInsetsCompat.Builder()
            .setInsets(
                WindowInsetsCompat.Type.statusBars(),
                androidx.core.graphics.Insets.of(0, 80, 0, 0)
            )
            .setInsets(
                WindowInsetsCompat.Type.navigationBars(),
                androidx.core.graphics.Insets.of(0, 0, 0, 120)
            )
            .build()

        ViewCompat.dispatchApplyWindowInsets(contentRoot, mockInsets)

        // Verify topAppBar received status bar padding
        assertThat(topAppBar.paddingTop).isEqualTo(80)

        // Verify nestedScrollView has bottom padding including navBarBottom
        val baseScrollBottom = (32 * activity.resources.displayMetrics.density).toInt()
        assertThat(nestedScrollView.paddingBottom).isEqualTo(baseScrollBottom + 120)

        controller.pause().stop().destroy()
    }

    @Test
    fun signatureActivity_toolbarNotCenteredAndInsetsDispatchedProperly() {
        val controller = Robolectric.buildActivity(SignatureActivity::class.java).create().start().resume()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()

        val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
        val llBottomControls = activity.findViewById<View>(R.id.llBottomControls)
        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)

        assertThat(toolbar).isNotNull()
        assertThat(llBottomControls).isNotNull()

        // Verify title is NOT centered to prevent truncation
        assertThat(toolbar.isTitleCentered).isFalse()

        // Simulate dispatch of window insets
        val mockInsets = WindowInsetsCompat.Builder()
            .setInsets(
                WindowInsetsCompat.Type.statusBars(),
                androidx.core.graphics.Insets.of(0, 90, 0, 0)
            )
            .setInsets(
                WindowInsetsCompat.Type.navigationBars(),
                androidx.core.graphics.Insets.of(0, 0, 0, 100)
            )
            .build()

        ViewCompat.dispatchApplyWindowInsets(contentRoot, mockInsets)

        // Verify toolbar is pushed down to clear status bar
        assertThat(toolbar.paddingTop).isEqualTo(90)

        // Verify llBottomControls has bottom padding to clear navigation bar
        assertThat(llBottomControls.paddingBottom).isAtLeast(100)

        controller.pause().stop().destroy()
    }

    @Test
    fun positionAnchorFragment_contains9DirectionalAnchors() {
        val fragment = PositionAnchorBottomSheetFragment()
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        fragment.show(activity.supportFragmentManager, "test_anchor")
        shadowOf(Looper.getMainLooper()).idle()

        val view = fragment.view
        assertThat(view).isNotNull()

        val btnTopLeft = view!!.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAnchorTopLeft)
        val btnCenter = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAnchorCenter)
        val btnBottomRight = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAnchorBottomRight)

        assertThat(btnTopLeft).isNotNull()
        assertThat(btnTopLeft.icon).isNotNull()

        assertThat(btnCenter).isNotNull()
        assertThat(btnCenter.icon).isNotNull()

        assertThat(btnBottomRight).isNotNull()
        assertThat(btnBottomRight.icon).isNotNull()

        fragment.dismiss()
    }
}
