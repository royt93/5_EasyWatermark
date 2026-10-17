package com.mckimquyen.watermark.ui.about

import android.view.View
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AboutActivityStickyBannerWidgetTest {

    @Test
    fun banner_isOutsideScrollContent_andRemainsUnderPinnedToolbar() {
        val activity = Robolectric.buildActivity(AboutActivity::class.java).setup().get()
        val banner = activity.findViewById<View>(R.id.layoutAdBanner)
        val scroll = activity.findViewById<NestedScrollView>(R.id.nestedScrollView)
        val toolbar = activity.findViewById<MaterialToolbar>(R.id.topAppBar)

        assertThat(banner).isNotNull()
        assertThat(scroll).isNotNull()
        assertThat(toolbar).isNotNull()
        assertThat(isDescendantOf(banner, scroll)).isFalse()

        val originalTop = banner.top
        scroll.scrollTo(0, 300)

        assertThat(banner.top).isEqualTo(originalTop)
        assertThat(banner.top).isAtLeast(toolbar.bottom)
    }

    private fun isDescendantOf(view: View, ancestor: View): Boolean {
        var current = view.parent
        while (current is View) {
            if (current === ancestor) return true
            current = current.parent
        }
        return false
    }
}
