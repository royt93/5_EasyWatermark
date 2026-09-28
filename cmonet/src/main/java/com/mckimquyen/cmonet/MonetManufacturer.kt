package com.mckimquyen.cmonet

import android.content.Context
import com.google.android.material.color.DynamicColors

/**
 * BUG-41: `isDynamicColorAvailable()` cũ = `DynamicColors.isDynamicColorAvailable() || isForceSupport`
 * — trên API 31+ vế đầu luôn true nên switch "Dynamic Color" ở About (đọc/ghi qua field thứ 2)
 * hoàn toàn vô tác dụng, tắt xong mở lại app vẫn hiện màu động. Tách rõ 2 khái niệm: NĂNG LỰC
 * thiết bị ([isDeviceCapable], không đổi theo lựa chọn user) và LỰA CHỌN user ([isUserEnabled],
 * mặc định true, lưu bền qua [sp]) — [shouldApplyDynamicColor] gộp cả 2, là nguồn sự thật DUY NHẤT
 * cho mọi quyết định vẽ màu động thật sự (xem [CMonet.isDynamicColorAvailable]).
 */
class MonetManufacturer(
    context: Context
) : IMonetManufacturer {
    private val sp: IStorage = SimpleSp(context)

    /** Cache trong RAM — tránh đọc SharedPreferences mỗi lần gọi (nhiều điểm gọi mỗi lần vẽ UI). */
    private var isUserEnabledCache = sp.getValue(KEY_DYNAMIC_COLOR_USER_ENABLED, true)

    override fun isDeviceCapable(): Boolean = DynamicColors.isDynamicColorAvailable()

    override fun isUserEnabled(): Boolean = isUserEnabledCache

    override fun setUserEnabled(enabled: Boolean) {
        sp.save(KEY_DYNAMIC_COLOR_USER_ENABLED, enabled)
        isUserEnabledCache = enabled
    }

    override fun shouldApplyDynamicColor(): Boolean = isDeviceCapable() && isUserEnabled()

    companion object {
        private const val KEY_DYNAMIC_COLOR_USER_ENABLED = "dynamic_color_user_enabled"
    }
}
