package com.mckimquyen.watermark

import android.util.Log

/**
 * Các hằng số dùng chung toàn app.
 */

/** Log tag chung cho toàn bộ app. */
const val LOG_TAG = "WatermarkCreator"

/**
 * ENH-03: wrapper no-op ở release build cho `Log.d` — hàng trăm lời gọi debug log chạy vô điều
 * kiện xuyên suốt (kể cả trong `onDraw`, gọi rất thường xuyên khi kéo/pinch) tốn CPU dựng string
 * template + áp lực GC không cần thiết, đồng thời lộ đường dẫn URI ảnh người dùng ra Logcat
 * production (privacy). Build debug giữ nguyên hành vi log như cũ.
 */
object AppLog {
    /**
     * ENH-42: `msg` nhận LAMBDA chứ không phải `String` sẵn — nếu nhận `String`, Kotlin dựng xong
     * chuỗi interpolation ở CALL SITE trước khi gọi vào hàm, bất kể `if (BuildConfig.DEBUG)` bên
     * trong có gate hay không (chỉ chặn được `Log.d` syscall thật). `inline` + lambda đảm bảo thân
     * lambda (gồm cả string interpolation) chỉ evaluate khi `BuildConfig.DEBUG == true`.
     */
    inline fun d(tag: String, msg: () -> String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg())
    }

    /**
     * ENH-36: sót khỏi ENH-03 — 41 lời gọi `Log.i` trần rải rác (gồm cả đường nóng nhất:
     * `onTouch`/`onScale` mỗi sự kiện, decode ảnh mỗi lần) vẫn dựng string template + in log ở
     * bản release. Gate giống [d]. ENH-42: cùng lý do nhận lambda thay vì `String` sẵn, xem [d].
     */
    inline fun i(tag: String, msg: () -> String) {
        if (BuildConfig.DEBUG) Log.i(tag, msg())
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
