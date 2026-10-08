package com.mckimquyen.watermark.utils

import com.roy.sdkadbmob.AdManager

/**
 * Chặn App Open tự-động-resume của SDK trong lúc app mở Activity hệ thống (photo picker, SAF, camera, crop...).
 *
 * Quay về từ các Activity đó làm process `onStart` → SDK coi là "resume từ background" và có thể bật App Open
 * ngay sau khi user vừa chọn ảnh (UX xấu, dễ click nhầm = invalid traffic). Doc AD_PROMPT_AOS.MD (Bước 4):
 * dùng `suppressAppOpenTemporarily` quanh luồng như vậy. SDK tự hết hạn cờ sau 5 phút nếu quên tắt.
 *
 * Gọi [begin] NGAY TRƯỚC `launch(...)`, gọi [end] khi kết quả đã về (callback của launcher) hoặc host resume.
 * Chỉ chặn nhánh tự-động resume, KHÔNG ảnh hưởng showInterstitial/showRewarded gọi tay.
 */
object AppOpenSuppressor {

    /** Seam cho unit test; production gọi thẳng SDK. */
    internal var setSuppressed: (Boolean) -> Unit = { AdManager.suppressAppOpenTemporarily(it) }

    fun begin() = setSuppressed(true)

    fun end() = setSuppressed(false)

    /** Bật suppress, chạy [launch]; nếu [launch] ném lỗi thì tắt lại ngay (không kẹt cờ) rồi ném tiếp. */
    inline fun <T> around(launch: () -> T): T {
        begin()
        return try {
            launch()
        } catch (t: Throwable) {
            end()
            throw t
        }
    }
}
