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
            val surfaceContainerLow = com.google.android.material.color.MaterialColors.getColor(
                dialog.context,
                com.google.android.material.R.attr.colorSurfaceContainerLow,
                android.graphics.Color.WHITE
            )
            WindowCompat.setDecorFitsSystemWindows(win, false)
            if (expandFully) {
                win.statusBarColor = surfaceColor
                win.navigationBarColor = surfaceColor
            } else {
                win.statusBarColor = android.graphics.Color.TRANSPARENT
                win.navigationBarColor = surfaceContainerLow
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                win.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
            }
            win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            win.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            updateSystemBarAppearance(dialog, win, expandFully)
        }

        dialog.setOnShowListener {
            val coordinator = dialog.findViewById<View>(com.google.android.material.R.id.coordinator)
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

            dialog.window?.let { win ->
                updateSystemBarAppearance(dialog, win, expandFully)
            }

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
                    sheet.setBackgroundResource(com.mckimquyen.watermark.R.drawable.bg_bottom_sheet_surface)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        val radius = (28 * sheet.resources.displayMetrics.density).toInt()
                        sheet.outlineProvider = object : android.view.ViewOutlineProvider() {
                            override fun getOutline(view: View, outline: android.graphics.Outline) {
                                outline.setRoundRect(0, 0, view.width, view.height + radius, radius.toFloat())
                            }
                        }
                        sheet.clipToOutline = true
                    }

                    val behavior = BottomSheetBehavior.from(sheet)
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    behavior.skipCollapsed = true

                    val baseBottom = sheet.paddingBottom
                    ViewCompat.setOnApplyWindowInsetsListener(sheet) { view, insets ->
                        val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                        val navBarBottom = insets.getInsets(
                            WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout()
                        ).bottom
                        val bottomInset = maxOf(imeBottom, navBarBottom)
                        view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, baseBottom + bottomInset)
                        insets
                    }
                    ViewCompat.requestApplyInsets(sheet)
                }
            }
        }
    }

    private fun updateSystemBarAppearance(dialog: BottomSheetDialog, win: android.view.Window, expandFully: Boolean) {
        val isNight = (dialog.context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val insetsController = WindowCompat.getInsetsController(win, win.decorView)

        if (expandFully) {
            insetsController.isAppearanceLightStatusBars = !isNight
        } else {
            // Keep status bar icon contrast matching underlying Activity behind translucent dim
            val activityLightStatus = activity?.window?.let { actWin ->
                WindowCompat.getInsetsController(actWin, actWin.decorView).isAppearanceLightStatusBars
            }
            insetsController.isAppearanceLightStatusBars = activityLightStatus ?: !isNight
        }
        // Navigation bar: surface container low is behind navigation bar
        insetsController.isAppearanceLightNavigationBars = !isNight
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
