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

    /**
     * Force bottom sheet mở TOÀN MÀN HÌNH ngay từ đầu, không cho thu gọn (kéo xuống chỉ đóng hẳn,
     * không dừng ở trạng thái collapsed) — dùng chung cho các bottom sheet cần hiện đủ nội dung
     * ngay (Qr/Signature/PositionAnchor/BatchCaption), tránh copy-paste
     * findViewById(design_bottom_sheet) + BottomSheetBehavior ở từng subclass.
     * Lưu ý: [Dialog.setOnShowListener] chỉ giữ được 1 listener — gọi hàm này THAY VÌ tự set
     * listener riêng (ghi đè, không cộng dồn).
     */
    protected fun Dialog.expandBottomSheetFully() {
        setOnShowListener {
            val bottomSheet = (this as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
    }
}
