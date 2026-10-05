package com.mckimquyen.watermark.ui.dlg

import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.entity.Template
import com.mckimquyen.watermark.ui.MainActivity
import com.mckimquyen.watermark.ui.MainViewModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.util.Date

/**
 * BUG-68: `uiStateFlow` từng là `StateFlow` giữ giá trị cuối (`GoEdit`/`UseTemplate`); `flowWithLifecycle(STARTED)`
 * replay lại giá trị cũ khi STOP→START (bấm Home rồi quay lại) → nhánh `GoEdit -> dialog?.onBackPressed()`
 * làm dialog sửa text tự đóng / `UseTemplate` ghi đè text user vừa sửa.
 *
 * Kịch bản ĐÚNG như báo cáo: vào danh sách Template → back/chọn template (về màn sửa) → Home → quay lại.
 * CHÚ Ý: phát GoEdit/UseTemplate trong lúc ĐANG ở màn sửa (không đi qua danh sách template) là luồng KHÔNG
 * hợp lệ — nhánh GoEdit khi child là màn sửa vốn đóng dialog (back-cascade có chủ đích, xem
 * `TextWatermarkBSDFragmentWidgetTest.backPress_fromEditView_cascadesToFullDismiss`).
 */
@RunWith(RobolectricTestRunner::class)
class TextWatermarkBSDFragmentStateReplayRoboTest {

    private fun idleFor(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private class Session(
        val controller: ActivityController<MainActivity>,
        val fragment: TextWatermarkBSDFragment,
        val vm: MainViewModel
    )

    private fun openDialog(): Session {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val vm = ViewModelProvider(controller.get())[MainViewModel::class.java]
        val fragment = TextWatermarkBSDFragment()
        fragment.show(controller.get().supportFragmentManager, TextWatermarkBSDFragment.TAG)
        idleFor(300)
        assertThat(fragment.dialog?.isShowing).isTrue()
        return Session(controller, fragment, vm)
    }

    private fun Session.goToTemplateListThenBackToEdit(finish: (MainViewModel) -> Unit) {
        vm.goTemplate()
        idleFor(700)
        assertThat(fragment.childFragmentManager.fragments.lastOrNull())
            .isInstanceOf(TextContentTemplateListFragment::class.java)
        finish(vm) // goTemplateEdit() hoặc useTemplate(...): về màn sửa
        idleFor(700)
        assertThat(fragment.dialog?.isShowing).isTrue()
        assertThat(fragment.childFragmentManager.fragments.lastOrNull())
            .isInstanceOf(EditTextContentFragment::class.java)
    }

    private fun Session.homeThenReturn() {
        controller.pause().stop()
        idleFor(150)
        controller.start().resume()
        idleFor(500)
    }

    @Test
    fun control_noTemplateNavigation_homeThenReturn_dialogStaysOpen() {
        val s = openDialog()

        s.homeThenReturn()

        assertThat(s.fragment.dialog?.isShowing).isTrue()
    }

    @Test
    fun backFromTemplateList_thenHomeAndReturn_dialogStaysOpen() {
        val s = openDialog()
        s.goToTemplateListThenBackToEdit { it.goTemplateEdit() }

        s.homeThenReturn()

        assertThat(s.fragment.dialog?.isShowing).isTrue()
        assertThat(s.fragment.childFragmentManager.fragments.lastOrNull())
            .isInstanceOf(EditTextContentFragment::class.java)
    }

    @Test
    fun useTemplate_thenHomeAndReturn_dialogStaysOpen_andDoesNotReapplyTemplateText() {
        val s = openDialog()
        s.goToTemplateListThenBackToEdit { it.useTemplate(Template(1, "NỘI DUNG TEMPLATE", Date(), Date())) }
        val edit = s.fragment.childFragmentManager.fragments.lastOrNull() as EditTextContentFragment
        edit.binding?.etWaterText?.setText("TEXT USER VỪA SỬA")
        idleFor(200)

        s.homeThenReturn()

        assertThat(s.fragment.dialog?.isShowing).isTrue()
        val current = (s.fragment.childFragmentManager.fragments.lastOrNull() as? EditTextContentFragment)
            ?.binding?.etWaterText?.text?.toString()
        assertThat(current).isEqualTo("TEXT USER VỪA SỬA")
    }
}
