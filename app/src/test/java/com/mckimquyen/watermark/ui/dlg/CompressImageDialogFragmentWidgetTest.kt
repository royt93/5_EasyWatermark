package com.mckimquyen.watermark.ui.dlg

import android.os.Looper
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.Result
import com.mckimquyen.watermark.ui.MainActivity
import com.mckimquyen.watermark.ui.MainViewModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** ENH-47: UI-state widget coverage for compress dialog (idle, running, success, error, cancel). */
@RunWith(RobolectricTestRunner::class)
class CompressImageDialogFragmentWidgetTest {

    private data class Host(val activity: MainActivity, val fragment: CompressImageDialogFragment, val viewModel: MainViewModel)

    private fun host(): Host {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
        val fragment = CompressImageDialogFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction().add(fragment, "compress-test").commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return Host(activity, fragment, viewModel)
    }

    @Test
    fun defaultState_showsPromptAndEnabledAction_hidesProgress() {
        val (_, fragment, _) = host()
        val root = requireNotNull(fragment.view)

        assertThat(root.findViewById<android.widget.TextView>(R.id.tvCompressTips).text).isEqualTo(
            root.context.getString(R.string.tips_need_compress_img)
        )
        assertThat(root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCompress).isEnabled).isTrue()
        assertThat(root.findViewById<androidx.core.widget.ContentLoadingProgressBar>(R.id.cpbCompress).isVisible).isFalse()
    }

    @Test
    fun compressingState_disablesActionAndShowsProgress() {
        val (_, fragment, viewModel) = host()
        viewModel.compressedResult.value = Result.success(null, code = MainViewModel.TYPE_COMPRESSING)
        shadowOf(Looper.getMainLooper()).idle()
        val root = requireNotNull(fragment.view)

        assertThat(root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCompress).isEnabled).isFalse()
        assertThat(root.findViewById<androidx.core.widget.ContentLoadingProgressBar>(R.id.cpbCompress).isVisible).isTrue()
        assertThat(root.findViewById<android.widget.TextView>(R.id.tvCompressTips).text).isEqualTo(
            root.context.getString(R.string.tips_compressing)
        )
    }

    @Test
    fun successState_enablesDoneActionAndHidesProgress() {
        val (_, fragment, viewModel) = host()
        viewModel.compressedResult.value = Result.success(null, code = MainViewModel.TYPE_COMPRESS_OK)
        shadowOf(Looper.getMainLooper()).idle()
        val root = requireNotNull(fragment.view)

        assertThat(root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCompress).isEnabled).isTrue()
        assertThat(root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCompress).text).isEqualTo(
            root.context.getString(R.string.tips_ok)
        )
        assertThat(root.findViewById<androidx.core.widget.ContentLoadingProgressBar>(R.id.cpbCompress).isVisible).isFalse()
    }

    @Test
    fun errorState_showsMessageAndRetryActionEnabled() {
        val (_, fragment, viewModel) = host()
        viewModel.compressedResult.value = Result.failure(null, MainViewModel.TYPE_COMPRESS_ERROR, "storage unavailable")
        shadowOf(Looper.getMainLooper()).idle()
        val root = requireNotNull(fragment.view)

        assertThat(root.findViewById<android.widget.TextView>(R.id.tvCompressTips).text.toString()).contains("storage unavailable")
        assertThat(root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCompress).isEnabled).isTrue()
        assertThat(root.findViewById<androidx.core.widget.ContentLoadingProgressBar>(R.id.cpbCompress).isVisible).isFalse()
    }

    @Test
    fun cancelButton_dismissesFragment() {
        val (_, fragment, _) = host()
        fragment.view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)?.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.isAdded).isFalse()
    }
}
