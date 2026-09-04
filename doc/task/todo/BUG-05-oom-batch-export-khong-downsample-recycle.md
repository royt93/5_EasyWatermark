---
id: BUG-05
type: Bug
priority: P0
effort: M
sources: Codex, Claude, Agy, Internal (4/4 — đồng thuận cao nhất trong toàn bộ review)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/BitmapCache.kt
verified: partial
---

# OOM khi export batch: không downsample + không recycle bitmap trung gian

## Mô tả
Cả 4 agent độc lập đều chỉ ra cùng 1 cụm vấn đề gây `OutOfMemoryError` trong đúng tính năng lõi "batch watermark" (đã có `catch (OutOfMemoryError)` ở `generateList` — tức là hiện tượng này đã từng/đang xảy ra thật, code chỉ đang "hứng" chứ chưa phòng tránh):

1. **Không downsample lúc export**: khác với preview trong editor (dùng `decodeSampledBitmapFromResource` có `inSampleSize`), luồng export batch decode ảnh gốc gần như full-resolution rồi `.copy(ARGB_8888, true)`. Với ảnh 12-48MP hiện đại + batch nhiều ảnh tuần tự, đây là nguyên nhân chính gây OOM.
2. **Không recycle bitmap trung gian**: `generateImage()` tạo nhiều bitmap tạm (bitmap gốc, `expandedBitmap` cho EXIF border, bitmap resize) nhưng không gọi `.recycle()` sau khi dùng xong — giữ nhiều bitmap ARGB_8888 full-size chờ GC dọn.
3. **`BitmapCache` không override `entryRemoved()`**: `LruCache` khi evict entry cũ không chủ động `recycle()` bitmap bị loại — trên API < 26 (minSdk 24), pixel data Bitmap nằm ở native heap ngoài Java heap, không recycle chủ động thì chỉ giải phóng khi GC/finalizer chạy (chậm, không đảm bảo).

## Cách fix đề xuất
- Tính kích thước đích trước khi decode (dựa trên `maxOutputLongEdge` đã có trong `UserPreferences`), truyền `inSampleSize` phù hợp thay vì decode full rồi resize sau.
- Gọi `.recycle()` tường minh cho bitmap trung gian ngay sau bước dùng cuối cùng trong `generateImage()`.
- Override `entryRemoved(evicted, key, oldValue, newValue)` trong `BitmapCache` để `oldValue?.bitmap?.recycle()` khi thực sự bị evict (không phải khi `replaced` — tránh recycle bitmap đang dùng).

## Acceptance Criteria
- [ ] Batch 50+ ảnh độ phân giải cao (>20MP) trên thiết bị RAM thấp không OOM (test thủ công trên máy cấu hình yếu hoặc giả lập giới hạn heap).
- [ ] Không còn bitmap ARGB_8888 trung gian bị giữ sau khi `generateImage()` trả kết quả (kiểm tra qua Android Studio Memory Profiler).
- [ ] `BitmapCache` recycle bitmap khi bị evict khỏi `LruCache`.
