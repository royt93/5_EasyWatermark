package com.mckimquyen.watermark.ui.base

import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.ui.MainViewModel

open class BaseBSDFragment : BottomSheetDialogFragment() {

    protected val shareViewModel: MainViewModel by activityViewModels()

    protected val config: WaterMark?
        get() {
            return shareViewModel.waterMark.value
        }

    override fun onCreateDialog(savedInstanceState: android.os.Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.2f) // Light dim for iOS look
        
        // Remove BottomSheet's default solid background
        dialog.setOnShowListener {
            val bottomSheet = (it as com.google.android.material.bottomsheet.BottomSheetDialog)
                .findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
        }
        return dialog
    }
}
