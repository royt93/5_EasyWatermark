package com.mckimquyen.watermark

import android.util.Log

/**
 * Các hằng số dùng chung toàn app.
 */

/** Log tag chung cho toàn bộ app (trước đây hardcoded rải rác là "roy93~"). */
const val LOG_TAG = "roy93~"

/**
 * ENH-03: wrapper no-op ở release build cho `Log.d` — hàng trăm lời gọi debug log chạy vô điều
 * kiện xuyên suốt (kể cả trong `onDraw`, gọi rất thường xuyên khi kéo/pinch) tốn CPU dựng string
 * template + áp lực GC không cần thiết, đồng thời lộ đường dẫn URI ảnh người dùng ra Logcat
 * production (privacy). Build debug giữ nguyên hành vi log như cũ.
 */
object AppLog {
    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }
}
