package com.mckimquyen.watermark.ui.dlg

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-22: `dlg_save_file.xml` (dialog "Export to the album") phải bọc `NestedScrollView` — trước
 * đây root là `LinearLayout` `match_parent` trực tiếp, nội dung dài hơn chiều cao khả dụng trên
 * màn hình nhỏ (xác nhận trên Samsung SM_A115F 720x1560) khiến `btnSave`/`btnOpenGallery` bị đẩy
 * ra ngoài viewport của `BottomSheetDialog`, không cuộn tới được.
 *
 * Launch thật `SaveImageBSDialogFragment` không khả thi trong JVM test của repo này — nó ép kiểu
 * `requireContext() as MainActivity` trực tiếp (không qua interface), không thể host bằng
 * `FragmentActivity` giả như kỹ thuật `GalleryFragmentLifecycleRoboTest`/BUG-10 đã dùng cho các
 * `BaseBindBSDFragment` khác (chúng chỉ cần `activityViewModels()`, không ép kiểu Activity cụ
 * thể). Test dưới đây kiểm chứng trực tiếp cấu trúc layout đã inflate (không qua Fragment) —
 * cùng tinh thần "kiểm chứng cấu trúc" mà `ExifPbFragmentRoboTest` (BUG-18) đã dùng khi launch
 * thật không khả thi.
 */
@RunWith(RobolectricTestRunner::class)
class DlgSaveFileLayoutRoboTest {

    private fun inflateRoot(): View {
        val context: Context = ApplicationProvider.getApplicationContext()
        val themed = ContextThemeWrapper(context, R.style.Theme_MyApp)
        val parent = FrameLayout(themed)
        return LayoutInflater.from(themed).inflate(R.layout.dlg_save_file, parent, false)
    }

    @Test
    fun root_isNestedScrollView_soLongContentIsScrollable_notClipped() {
        val root = inflateRoot()

        assertThat(root).isInstanceOf(NestedScrollView::class.java)
    }

    @Test
    fun root_fillsViewport_soShortContentStillLooksFullHeight() {
        val root = inflateRoot() as NestedScrollView

        assertThat(root.isFillViewport).isTrue()
    }

    @Test
    fun ctaButtons_stillReachableInHierarchy_afterWrappingInScrollView() {
        val root = inflateRoot()

        assertThat(root.findViewById<View>(R.id.btnSave)).isNotNull()
        assertThat(root.findViewById<View>(R.id.btnOpenGallery)).isNotNull()
    }

    @Test
    fun scrollableContent_isSingleDirectChild_ofNestedScrollView() {
        // NestedScrollView chỉ cho phép đúng 1 child trực tiếp (contract của ScrollView) — nếu ai
        // đó thêm nhầm 1 View anh em cùng cấp llContainer, nó sẽ không cuộn theo, tái phát bug.
        val root = inflateRoot() as ViewGroup

        assertThat(root.childCount).isEqualTo(1)
        assertThat(root.getChildAt(0).id).isEqualTo(R.id.llContainer)
    }
}
