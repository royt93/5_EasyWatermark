package com.mckimquyen.cmonet

import android.content.Context
import com.google.android.material.color.DynamicColors

class MonetManufacturer(
    context: Context,
) : IMonetManufacturer {
    private val sp: IStorage = SimpleSp(context)

    /**
     * cache in memory
     */
    private var isForceSupport = sp.getValue(KEY_DYNAMIC_COLOR_FORCE, false)

    // ENH-12: trước đây có thêm 1 allow-list thủ công theo tên hãng (Build.MANUFACTURER/BRAND) —
    // deny-list/allow-list cứng dễ lỗi thời (thiết bị/OEM mới không nằm trong danh sách bị tắt
    // oan dù thực sự hỗ trợ). `DynamicColors.isDynamicColorAvailable()` là API chính thức của
    // Material, đủ tin cậy làm nguồn sự thật duy nhất.
    override fun isDynamicColorAvailable(): Boolean {
        return DynamicColors.isDynamicColorAvailable() || isForceSupport
    }

    override fun setForceSupport(supported: Boolean) {
        sp.save(KEY_DYNAMIC_COLOR_FORCE, supported)
        isForceSupport = supported
    }

    companion object {
        //        private const val TAG = "MonetManufacturer"
        private const val KEY_DYNAMIC_COLOR_FORCE = "dynamic_color_force"
    }
}
