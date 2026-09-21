package com.mckimquyen.watermark.ui.widget

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.BuildConfig
import com.mckimquyen.watermark.R
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LaunchViewRoboTest {

    private lateinit var context: Context
    private lateinit var launchView: LaunchView

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(R.style.Theme_MyApp)
        launchView = LaunchView(context)
    }

    @Test
    fun init_createsAllBrandElements() {
        assertThat(launchView.tvAppBrand.text.toString()).isEqualTo(context.getString(R.string.app_name))
        assertThat(launchView.tvAppTagline.text.toString()).contains("Offline Protection")
        assertThat(launchView.tvVersionCopyright.text.toString()).contains(BuildConfig.VERSION_NAME)
        assertThat(launchView.tvVersionCopyright.text.toString()).contains(context.getString(R.string.app_copyright))
    }

    @Test
    fun layoutLaunch_respectsSystemInsets_andAvoidsNavigationBarOverlap() {
        launchView.setPadding(0, 120, 0, 168) // 120px status bar, 168px navigation bar
        launchView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY)
        )
        launchView.layout(0, 0, 1080, 2400)

        val usableBottom = 2400 - 168
        val footer = launchView.tvVersionCopyright
        // Footer must be completely above navigation bar inset (usableBottom)
        assertThat(footer.bottom).isAtMost(usableBottom)
        assertThat(footer.top).isLessThan(footer.bottom)
    }

    @Test
    fun layoutLaunch_arranges4ActionCardsAsGrid() {
        launchView.setPadding(0, 120, 0, 168)
        launchView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY)
        )
        launchView.layout(0, 0, 1080, 2400)

        val pick = launchView.ivSelectedPhotoTips
        val camera = launchView.ivCaptureFromCamera
        val paste = launchView.ivPasteFromClipboard
        val about = launchView.ivGoAboutPage

        // 2 card cùng hàng phải cùng top/bottom, khác cột (left/right).
        assertThat(pick.top).isEqualTo(camera.top)
        assertThat(pick.bottom).isEqualTo(camera.bottom)
        assertThat(pick.right).isLessThan(camera.left)

        assertThat(paste.top).isEqualTo(about.top)
        assertThat(paste.bottom).isEqualTo(about.bottom)
        assertThat(paste.right).isLessThan(about.left)

        // 2 cột phải cùng vị trí left/right giữa hàng trên và hàng dưới (grid, không phải list dọc).
        assertThat(pick.left).isEqualTo(paste.left)
        assertThat(camera.left).isEqualTo(about.left)

        // Hàng dưới nằm dưới hàng trên, không chồng lấn.
        assertThat(paste.top).isAtLeast(pick.bottom)

        // 4 card cùng kích thước (lưới đều, không phải card to nhỏ khác nhau).
        assertThat(pick.width).isEqualTo(camera.width)
        assertThat(pick.height).isEqualTo(paste.height)
    }

    @Test
    fun layoutEditor_respectsSystemInsets_forToolbarAndTabLayout() {
        launchView.toEditorMode()
        launchView.setPadding(0, 120, 0, 168)
        launchView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY)
        )
        launchView.layout(0, 0, 1080, 2400)

        // Toolbar sits below status bar
        assertThat(launchView.toolbar.top).isAtLeast(120)

        // TabLayout sits above navigation bar
        val usableBottom = 2400 - 168
        assertThat(launchView.tabLayout.bottom).isAtMost(usableBottom)
    }
}
