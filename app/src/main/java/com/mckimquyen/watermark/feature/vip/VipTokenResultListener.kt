package com.mckimquyen.watermark.feature.vip

import android.content.Context

/**
 * Listener toàn cục cho kết quả token VIP đang pending của SDK. Đăng ký ở Application.onCreate nên chạy đúng lúc
 * SDK resolve, kể cả khi không có màn nào mở: ghi mốc cấp VIP tại đây (một lần cho mỗi kết quả thành công) rồi
 * mới công bố cho UI qua [VipTokenPendingState].
 */
internal class VipTokenResultListener(context: Context) : (Boolean) -> Unit {
    private val appContext = context.applicationContext

    override fun invoke(activated: Boolean) {
        // Chỉ ghi mốc cấp khi kết quả được chấp nhận: SDK có thể gọi lặp cho cùng token, ghi lại sẽ reset
        // grantedAt và thanh tiến độ VIP.
        val accepted = VipTokenPendingState.resolve(activated)
        if (accepted && activated) VipPrefs(appContext).markUserActivatedVip()
    }
}
