package com.mckimquyen.watermark.utils.bitmap

import android.graphics.Bitmap

/**
 * BUG-21: theo dõi 1 bitmap "đang sở hữu, chưa recycle" qua nhiều bước xử lý tuần tự (decode →
 * vẽ watermark → EXIF border → resize → ghi file). Nếu luồng xử lý return sớm (kể cả bằng
 * exception hoặc `return@label`) mà chưa gọi [release] (đã recycle/chuyển giao thành công ở
 * nhánh cuối), bitmap đang track sẽ được [recycleIfOwned] tự động dọn — gọi hàm này trong
 * `finally` của khối xử lý. Bổ sung cho BUG-05 (đã xử lý recycle ở nhánh THÀNH CÔNG); guard này
 * đảm bảo mọi nhánh LỖI (config sai/icon lỗi/MediaStore insert-hoặc-ghi lỗi) cũng không rò rỉ.
 */
class BitmapRecycleGuard(initial: Bitmap) {
    private var current: Bitmap? = initial

    /** Bitmap cũ đã được xử lý xong (tự recycle thủ công trước khi gọi) — chuyển sang track [next]. */
    fun replace(next: Bitmap) {
        current = next
    }

    /** Bitmap đang track đã được recycle thủ công ở nhánh thành công — guard không đụng vào nữa. */
    fun release() {
        current = null
    }

    /** Recycle bitmap đang track nếu còn sống — gọi trong `finally`, an toàn khi gọi nhiều lần. */
    fun recycleIfOwned() {
        current?.let { if (!it.isRecycled) it.recycle() }
        current = null
    }
}
