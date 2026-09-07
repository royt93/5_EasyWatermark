package com.mckimquyen.watermark.ui.base

import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
        // Một số OEM (Transsion/TECNO) không tự inset dialog window theo edge-to-edge mặc định
        // như Activity (xem BaseActivity.applyEdgeToEdge) — set tường minh để hành vi nhất quán,
        // phần padding thật xử lý ở setOnShowListener bên dưới (design_bottom_sheet).
        dialog.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }

        // Remove BottomSheet's default solid background
        dialog.setOnShowListener {
            val bottomSheet = (it as com.google.android.material.bottomsheet.BottomSheetDialog)
                .findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
            // BottomSheetBehavior định vị/đo kích thước theo chính view design_bottom_sheet này
            // (không phải content root bên trong) — padding ở root không đủ để tránh nav bar,
            // phải cộng thêm ở đây thì nút CTA cuối layout mới không bị nav bar che.
            bottomSheet?.let { sheet ->
                val baseBottom = sheet.paddingBottom
                ViewCompat.setOnApplyWindowInsetsListener(sheet) { view, insets ->
                    val navBarBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
                    view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, baseBottom + navBarBottom)
                    insets
                }
                ViewCompat.requestApplyInsets(sheet)
            }
        }
        return dialog
    }
}
