package com.mckimquyen.watermark.ui

import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.Anchor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for Position Anchor enum mapping and newly added UI drawables/tokens.
 */
@RunWith(RobolectricTestRunner::class)
class PositionAnchorAndTemplateTest {

    private val themedContext by lazy {
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_MyApp
        )
    }

    @Test
    fun anchor_obtainReturnsCorrectEnumForAllNinePositions() {
        assertThat(Anchor.obtain(0)).isEqualTo(Anchor.TOP_LEFT)
        assertThat(Anchor.obtain(1)).isEqualTo(Anchor.TOP_CENTER)
        assertThat(Anchor.obtain(2)).isEqualTo(Anchor.TOP_RIGHT)
        assertThat(Anchor.obtain(3)).isEqualTo(Anchor.CENTER_LEFT)
        assertThat(Anchor.obtain(4)).isEqualTo(Anchor.CENTER)
        assertThat(Anchor.obtain(5)).isEqualTo(Anchor.CENTER_RIGHT)
        assertThat(Anchor.obtain(6)).isEqualTo(Anchor.BOTTOM_LEFT)
        assertThat(Anchor.obtain(7)).isEqualTo(Anchor.BOTTOM_CENTER)
        assertThat(Anchor.obtain(8)).isEqualTo(Anchor.BOTTOM_RIGHT)
    }

    @Test
    fun anchor_outOfBoundsReturnsCenterFallback() {
        assertThat(Anchor.obtain(-1)).isEqualTo(Anchor.CENTER)
        assertThat(Anchor.obtain(99)).isEqualTo(Anchor.CENTER)
    }

    @Test
    fun anchorDrawables_allNineVectorDrawablesExistAndLoad() {
        val drawableResIds = listOf(
            R.drawable.ic_anchor_top_left,
            R.drawable.ic_anchor_top_center,
            R.drawable.ic_anchor_top_right,
            R.drawable.ic_anchor_center_left,
            R.drawable.ic_anchor_center,
            R.drawable.ic_anchor_center_right,
            R.drawable.ic_anchor_bottom_left,
            R.drawable.ic_anchor_bottom_center,
            R.drawable.ic_anchor_bottom_right
        )

        for (resId in drawableResIds) {
            val drawable = ContextCompat.getDrawable(themedContext, resId)
            assertThat(drawable).isNotNull()
        }
    }

    @Test
    fun licenseBadge_drawableLoadsSuccessfully() {
        val drawable = ContextCompat.getDrawable(themedContext, R.drawable.bg_license_badge)
        assertThat(drawable).isNotNull()
    }
}
