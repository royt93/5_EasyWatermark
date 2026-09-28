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

    /**
     * ENH-36: sót khỏi ENH-03 — 41 lời gọi `Log.i` trần rải rác (gồm cả đường nóng nhất:
     * `onTouch`/`onScale` mỗi sự kiện, decode ảnh mỗi lần) vẫn dựng string template + in log ở
     * bản release. Gate giống [d].
     */
    fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.i(tag, msg)
    }

    /** ENH-36: trước đây KHÔNG gate gì — cùng lý do với [d]/[i], gate luôn cho nhất quán. */
    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg)
    }

    /** ENH-36: GIỮ không gate — lỗi thật nên thấy được cả ở bản release để chẩn đoán sự cố người dùng gặp. */
    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
    }
}
