package com.mckimquyen.watermark.ui.base

import android.app.Dialog
import android.view.View
import android.view.ViewGroup
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
        dialog.window?.let { win ->
            val surfaceColor = com.google.android.material.color.MaterialColors.getColor(
                dialog.context,
                com.google.android.material.R.attr.colorSurface,
                android.graphics.Color.WHITE
            )
            WindowCompat.setDecorFitsSystemWindows(win, false)
            if (expandFully) {
                win.statusBarColor = surfaceColor
                win.navigationBarColor = surfaceColor
            } else {
                win.statusBarColor = android.graphics.Color.TRANSPARENT
                win.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                win.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
            }
            win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            win.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            val isNight = (dialog.context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val insetsController = WindowCompat.getInsetsController(win, win.decorView)
            insetsController.isAppearanceLightStatusBars = !isNight
            insetsController.isAppearanceLightNavigationBars = !isNight
        }

        dialog.setOnShowListener {
            val coordinator = dialog.findViewById<View>(com.google.android.material.R.id.coordinator)
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
            bottomSheet?.let { sheet ->
                if (expandFully) {
                    coordinator?.fitsSystemWindows = false
                    sheet.fitsSystemWindows = false
                    sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    val behavior = BottomSheetBehavior.from(sheet)
                    behavior.expandedOffset = 0
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    behavior.skipCollapsed = true
                    behavior.peekHeight = sheet.resources.displayMetrics.heightPixels
                } else {
                    val baseBottom = sheet.paddingBottom
                    ViewCompat.setOnApplyWindowInsetsListener(sheet) { view, insets ->
                        val navBarBottom = insets.getInsets(
                            WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout()
                        ).bottom
                        view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, baseBottom + navBarBottom)
                        insets
                    }
                    ViewCompat.requestApplyInsets(sheet)
                }
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
