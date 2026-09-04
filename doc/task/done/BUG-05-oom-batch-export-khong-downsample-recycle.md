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
- [x] Không còn bitmap ARGB_8888 trung gian bị giữ sau khi `generateImage()` trả kết quả — recycle tường minh tại 4 điểm: (1) `rect.data.bitmap` gốc ngay sau khi copy sang `mutableBitmap`, (2) `mutableBitmap` sau khi vẽ sang `expandedBitmap` (nhánh EXIF border), (3) `finalExportBitmap` sau `resizeIfNeeded` nếu đã tạo instance mới, (4) `exportBitmap` sau khi `compress()` xong (cả 2 nhánh Android Q+/pre-Q).
- [ ] ~~Downsample lúc decode thay vì full-res~~ — **tách thành [ENH-14](../todo/ENH-14-downsample-truc-tiep-khi-decode-export.md)** (đổi độ phân giải decode ảnh hưởng toàn bộ phép tính toạ độ watermark, rủi ro cao, cần làm riêng có test kỹ vị trí watermark không lệch).
- [ ] ~~`BitmapCache` recycle bitmap khi evict~~ — **tách thành [ENH-15](../todo/ENH-15-bitmapcache-recycle-an-toan-khi-evict.md)** — **PHÁT HIỆN RỦI RO THẬT**: `WaterMarkImageView.iconBitmap` giữ tham chiếu trực tiếp (không copy) tới bitmap trong cache, tự động recycle khi evict có thể crash "trying to use a recycled bitmap" ngay trong preview đang chạy. Cần thiết kế reference-counting hoặc đổi cơ chế trước khi bật recycle-on-evict — không an toàn để làm vội.
- [ ] Batch nhiều ảnh độ phân giải cao không OOM — **giảm đáng kể rủi ro nhờ phần đã fix (recycle bitmap trung gian)**, nhưng chưa loại bỏ hoàn toàn root cause "decode full-res" (còn ở ENH-14). Chưa test được batch 50+ ảnh >20MP thật trên thiết bị RAM thấp trong phạm vi loop này (giới hạn công cụ smoke test hiện có).

## Kết quả kiểm chứng
- Compile sạch, không lint violation mới.
- Full regression test: 41/41 unit test pass, không ảnh hưởng.
- Smoke test thật trên Pixel 7 Pro: export ảnh thật qua UI với resize=1080 (exercise đúng nhánh `finalExportBitmap.recycle()` vì tạo bitmap mới) → thành công, checkmark xanh, logcat sạch, không crash.
- Nhánh EXIF border (`mutableBitmap.recycle()` giữa hàm): smoke test thật trên Pixel 7 Pro qua route `ACTION_SEND` + data URI (né picker UI không ổn định trên máy lúc test) — ảnh camera thật `IMG_20240603_112730_409.jpg` (4608x3456, có EXIF thật) → bật "Leica EXIF Border" → export với resize=1080 (exercise ĐỒNG THỜI cả nhánh `mutableBitmap.recycle()` VÀ `finalExportBitmap.recycle()` do resize) → thành công 1/1, checkmark xanh, logcat sạch, không FATAL EXCEPTION, pid ổn định.
- Cả 2 path chính (thường + EXIF border/resize) đã có bằng chứng device thật, không chỉ phân tích tĩnh.
