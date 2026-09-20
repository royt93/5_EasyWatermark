package com.mckimquyen.watermark.ui

import android.os.Build
import android.os.Looper
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.ui.dlg.PositionAnchorBottomSheetFragment
import com.mckimquyen.watermark.ui.dlg.SaveImageBSDialogFragment
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class MaterialYouIntegrationTest {

    @Test
    fun saveImageBSDialogFragment_showsAndAppliesM3BottomSheetTheme() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()

        val fragment = SaveImageBSDialogFragment()
        fragment.show(activity.supportFragmentManager, "SaveImageTest")
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.dialog).isInstanceOf(BottomSheetDialog::class.java)
        val dialog = fragment.dialog as BottomSheetDialog
        assertThat(dialog.isShowing).isTrue()

        // Verify root view inflated inside dialog
        val root = fragment.view
        assertThat(root).isNotNull()
        val btnSave = root?.findViewById<android.view.View>(R.id.btnSave)
        assertThat(btnSave).isNotNull()

        fragment.dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        controller.destroy()
    }

    @Test
    fun positionAnchorBottomSheet_showsAndAppliesM3Theme() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()

        val fragment = PositionAnchorBottomSheetFragment()
        fragment.show(activity.supportFragmentManager, "AnchorTest")
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.dialog).isInstanceOf(BottomSheetDialog::class.java)
        val dialog = fragment.dialog as BottomSheetDialog
        assertThat(dialog.isShowing).isTrue()

        val root = fragment.view
        assertThat(root).isNotNull()
        val btnCenter = root?.findViewById<android.view.View>(R.id.btnAnchorCenter)
        assertThat(btnCenter).isNotNull()

        fragment.dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        controller.destroy()
    }
}
