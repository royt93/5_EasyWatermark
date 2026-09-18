package com.mckimquyen.watermark.ui.base

import android.app.Dialog
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
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
        // Một số OEM (Transsion/TECNO) không tự inset dialog window theo edge-to-edge mặc định
        // như Activity (xem BaseActivity.applyEdgeToEdge) — set tường minh để hành vi nhất quán,
        // phần padding thật xử lý ở setOnShowListener bên dưới (design_bottom_sheet).
        dialog.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }

        // Remove BottomSheet's default solid background and apply navigation bar insets
        if (dialog is BottomSheetDialog) {
            setupBottomSheet(dialog, expandFully = false)
        }
        return dialog
    }

    protected fun setupBottomSheet(dialog: BottomSheetDialog, expandFully: Boolean = false) {
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
            bottomSheet?.let { sheet ->
                if (expandFully) {
                    val behavior = BottomSheetBehavior.from(sheet)
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    behavior.skipCollapsed = true
                }
                val baseBottom = sheet.paddingBottom
                ViewCompat.setOnApplyWindowInsetsListener(sheet) { view, insets ->
                    val navBarBottom = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                    ).bottom
                    view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, baseBottom + navBarBottom)
                    insets
                }
                ViewCompat.requestApplyInsets(sheet)
            }
        }
    }

    /**
     * Force bottom sheet mở TOÀN MÀN HÌNH ngay từ đầu, không cho thu gọn (kéo xuống chỉ đóng hẳn,
     * không dừng ở trạng thái collapsed) — bảo toàn padding insets và nền trong suốt.
     */
    protected fun Dialog.expandBottomSheetFully() {
        if (this is BottomSheetDialog) {
            setupBottomSheet(this, expandFully = true)
        }
    }
}
