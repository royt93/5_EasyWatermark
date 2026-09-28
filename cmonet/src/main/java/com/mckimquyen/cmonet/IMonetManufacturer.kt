package com.mckimquyen.cmonet

interface IMonetManufacturer {
    /** BUG-41: năng lực THIẾT BỊ/OS thuần tuý, không phụ thuộc lựa chọn user. */
    fun isDeviceCapable(): Boolean

    /** BUG-41: user có MUỐN bật Dynamic Color hay không (mặc định true, độc lập [isDeviceCapable]). */
    fun isUserEnabled(): Boolean

    fun setUserEnabled(enabled: Boolean)

    /** BUG-41: áp dụng hay không = thiết bị hỗ trợ VÀ user muốn bật. */
    fun shouldApplyDynamicColor(): Boolean
}
