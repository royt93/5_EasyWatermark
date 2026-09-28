package com.mckimquyen.cmonet

import android.app.Application
import com.google.android.material.color.DynamicColors

object CMonet {

    private const val TAG = "CMonet"

    private lateinit var monetManufacturer: MonetManufacturer

    private lateinit var application: Application

    fun init(application: Application, apply: Boolean = true) {
//        Log.d(TAG, "init")
        this.application = application
        monetManufacturer = MonetManufacturer(application)
        if (apply) {
            applyToActivitiesIfAvailable(application)
        }
    }

    private fun applyToActivitiesIfAvailable(application: Application) {
//        Log.d(TAG, "applyToActivitiesIfAvailable")
        if (isDynamicColorAvailable()) {
            DynamicColors.applyToActivitiesIfAvailable(application)
        }
    }

    /**
     * BUG-41: giờ = thiết bị hỗ trợ VÀ user muốn bật (trước đây chỉ hỏi thiết bị, bỏ qua lựa chọn
     * user) — nguồn sự thật DUY NHẤT cho mọi quyết định vẽ màu động (~30 điểm gọi qua
     * `ContextExtension.kt`) lẫn [applyToActivitiesIfAvailable] ở trên.
     */
    fun isDynamicColorAvailable(): Boolean {
        val shouldApply = monetManufacturer.shouldApplyDynamicColor()
//        Log.d(TAG, "isDynamicColorAvailable $shouldApply")
        return shouldApply
    }

    /** BUG-41: năng lực THIẾT BỊ thuần tuý — dùng để enable/disable switch trong About (khác [isDynamicColorAvailable] đã gộp cả lựa chọn user). */
    fun isDeviceSupported(): Boolean = monetManufacturer.isDeviceCapable()

    /** BUG-41: lựa chọn HIỆN TẠI của user — dùng để set trạng thái checked của switch trong About. */
    fun isUserEnabled(): Boolean = monetManufacturer.isUserEnabled()

    /** BUG-41: user bật/tắt qua switch About — thay cho `forceSupportDynamicColor()`/`disableSupportDynamicColor()` cũ (tên gây hiểu lầm "force", thực chất chỉ dùng làm on/off, gộp lại còn 1 hàm). */
    fun setUserEnabled(enabled: Boolean) {
//        Log.d(TAG, "setUserEnabled $enabled")
        monetManufacturer.setUserEnabled(enabled)
    }
}
