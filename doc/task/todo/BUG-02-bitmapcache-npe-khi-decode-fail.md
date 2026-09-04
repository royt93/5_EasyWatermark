---
id: BUG-02
type: Bug
priority: P0
effort: XS
sources: Codex, Agy (2/4, verify trực tiếp xác nhận đúng)
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/BitmapCache.kt
verified: true
---

# BitmapCache NPE khi decode ảnh lỗi

## Mô tả
`BitmapCache.addToCache(info: BitmapInfo, bitmapValue: BitmapValue?)` (dòng 32-34) gọi thẳng `memoryCache.put(info, bitmapValue)` — `bitmapValue` khai báo nullable nhưng không guard. Android `LruCache.put()` ném `NullPointerException` ngay lập tức nếu `value == null`. Khi decode ảnh thất bại (file lỗi/bị xoá giữa chừng, URI mất quyền), `bitmapValue` là `null` → app crash ngay tại chỗ lẽ ra phải xử lý lỗi gracefully.

## Cách fix đề xuất
Guard null trước khi put, hoặc đổi signature không nhận null:
```kotlin
fun addToCache(info: BitmapInfo, bitmapValue: BitmapValue?) {
    if (bitmapValue != null) memoryCache.put(info, bitmapValue)
}
```

## Acceptance Criteria
- [ ] Decode ảnh lỗi (file không tồn tại, URI mất quyền) không crash, trả về lỗi qua `Result.failure` như luồng bình thường.
- [ ] Unit test: gọi `addToCache` với `bitmapValue = null` không throw.
