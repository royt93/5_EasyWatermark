---
id: BUG-06
type: Bug
priority: P1
effort: M
sources: Internal, Claude (2/4, verify trực tiếp xác nhận chính xác điều kiện)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
verified: true
---

# Icon watermark cache luôn miss khi pinch-zoom + race coroutine

## Mô tả
Đã đọc trực tiếp `applyNewConfig()` (dòng 234-236), xác nhận đúng:
```kotlin
if (iconBitmap == null ||
    localIconUri != newConfig.iconUri ||
    (iconBitmap!!.width != newConfig.textSize.toInt() && iconBitmap!!.height != newConfig.textSize.toInt())
) { /* re-decode icon từ Uri */ }
```
Điều kiện thứ 3 so sánh **kích thước pixel của bitmap** với `textSize` (đơn vị sp, dùng cho watermark text, không liên quan icon) — hai đại lượng gần như không bao giờ bằng nhau, nên nhánh "reuse cache" gần như không bao giờ chạy. Vì `onScale` (pinch gesture) gọi `config = config?.copy(textSize = ...)` ở **mỗi frame**, mỗi lần kéo pinch trong chế độ Image/Signature app **decode lại bitmap từ Uri liên tục** → giật lag rõ rệt, và `iconBitmap` cũ bị ghi đè mà không `recycle()` trước.

Thêm: `WaterMarkImageView` implement `CoroutineScope` bằng `Dispatchers.Main` không gắn `Job` cha; `onDetachedFromWindow()` chỉ cancel `generateBitmapJob`, biến `iconBitmap`/`localIconUri` bị đọc/ghi ở dòng 234-255 **trước** khi vào `generateBitmapMutex.withLock` — nếu job cũ (từ frame pinch trước) chưa kịp dừng, có race ghi đè `iconBitmap` giữa 2 coroutine.

## Cách fix đề xuất
- Sửa điều kiện reuse cache: so kích thước icon mong muốn (không phải `textSize`) hoặc đơn giản chỉ check `iconBitmap == null || localIconUri != newConfig.iconUri` (bỏ hẳn điều kiện size sai).
- Debounce sự kiện pinch trước khi trigger `applyNewConfig` (xem `ENH-02`) để giảm tần suất decode.
- Đưa việc đọc/ghi `iconBitmap`/`localIconUri` vào trong `generateBitmapMutex.withLock`.
- Cân nhắc dùng `findViewTreeLifecycleOwner()?.lifecycleScope` thay vì tự implement `CoroutineScope` thủ công (xem thêm ENH liên quan coroutine lifecycle nếu tách task riêng).

## Acceptance Criteria
- [ ] Pinch-zoom lặp lại nhiều lần với cùng 1 icon không trigger decode lại từ Uri (verify qua log/breakpoint).
- [ ] Không còn thao tác đọc/ghi `iconBitmap` ngoài mutex.
- [ ] Pinch nhanh liên tục không gây crash/hình watermark sai (test thủ công trên thiết bị thật).
