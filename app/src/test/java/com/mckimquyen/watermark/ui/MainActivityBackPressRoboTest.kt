package com.mckimquyen.watermark.ui

import androidx.appcompat.app.AlertDialog
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.ui.widget.LaunchView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog

/**
 * Predictive back (2026-09-23): `@Deprecated override fun onBackPressed()` cu doi sang
 * `OnBackPressedCallback` dang ky qua `onBackPressedDispatcher.addCallback(this) { ... }`. Test
 * nay khoa lai hanh vi qua dispatcher that (khong goi truc tiep ham cu da xoa):
 * - LaunchMode: back pass-through (khong hien dialog xac nhan).
 * - Editor mode: back hien MaterialAlertDialogBuilder xac nhan thoat.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityBackPressRoboTest {

    @Test
    fun backPress_launchMode_noConfirmDialogShown() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        assertThat(activity.launchView.mode).isEqualTo(LaunchView.ViewMode.LaunchMode)

        activity.onBackPressedDispatcher.onBackPressed()

        assertThat(ShadowDialog.getLatestDialog()).isNull()
    }

    @Test
    fun backPress_editorMode_showsConfirmDialog() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        activity.launchView.toEditorMode()
        assertThat(activity.launchView.mode).isEqualTo(LaunchView.ViewMode.Editor)

        activity.onBackPressedDispatcher.onBackPressed()

        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
        assertThat(dialog).isNotNull()
        assertThat(dialog!!.isShowing).isTrue()
    }
}
