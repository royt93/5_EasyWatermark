package com.mckimquyen.watermark.ui.dlg

import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-49: `.bottom` là toạ độ Y tương đối so với PARENT (= `top + height`), KHÔNG phải chiều cao
 * view. `rvContent` nằm dưới toolbar (`layout_constraintTop_toBottomOf="@id/abl"`) nên `top != 0`
 * trên thiết bị thật — dùng `.bottom` cộng dư đúng bằng offset top đó, làm tỉ lệ scroll của slider
 * lệch. Test dựng 1 `View` có `top` khác 0 (mô phỏng nằm dưới toolbar) để khoá đúng tính chất:
 * công thức phải ra `.height`, không lệch theo `.top`.
 */
@RunWith(RobolectricTestRunner::class)
class GalleryFragmentVisibleScrollHeightRoboTest {

    @Test
    fun visibleScrollHeight_viewBelowToolbar_usesHeightNotBottom() {
        val view = View(ApplicationProvider.getApplicationContext())
        // top=200 mô phỏng offset do toolbar phía trên (layout_constraintTop_toBottomOf), bottom=1000
        // -> height thật = 800. `.bottom` (1000) sẽ SAI nếu dùng nhầm.
        view.layout(0, 200, 300, 1000)

        val result = GalleryFragment.visibleScrollHeight(view)

        assertThat(result).isEqualTo(800)
    }

    @Test
    fun visibleScrollHeight_subtractsBottomPadding() {
        val view = View(ApplicationProvider.getApplicationContext())
        view.layout(0, 200, 300, 1000) // height=800
        view.setPadding(0, 0, 0, 50)

        val result = GalleryFragment.visibleScrollHeight(view)

        assertThat(result).isEqualTo(750)
    }

    @Test
    fun visibleScrollHeight_viewAtTopOfParent_sameAsOldBottomBehavior() {
        // top=0 — trường hợp này `.bottom` và `.height` TRÙNG nhau, không regression cho view
        // không nằm dưới view khác.
        val view = View(ApplicationProvider.getApplicationContext())
        view.layout(0, 0, 300, 800)

        val result = GalleryFragment.visibleScrollHeight(view)

        assertThat(result).isEqualTo(800)
    }
}
