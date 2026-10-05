---
id: BUG-76
type: Bug
priority: P1
effort: M
sources: phát hiện khi chạy connectedDebugAndroidTest trên Pixel 7 Pro (2026-10-05); đối chiếu A/B trên bản trước BUG-73
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/BitmapCache.kt
  - app/src/androidTest/java/com/mckimquyen/watermark/utils/bitmap/BitmapUtilsRealDecodeIntegrationTest.kt
---

# `BitmapCache.sizeOf()` ném `IllegalStateException: reporting inconsistent results` trên máy thật

## Mô tả (đã đo trên thiết bị thật)
Chạy `connectedDebugAndroidTest` toàn bộ (150 test) trên Pixel 7 Pro: 6 test của `BitmapUtilsRealDecodeIntegrationTest` đỏ với
`IllegalStateException: BitmapCache$memoryCache$2$1.sizeOf() is reporting inconsistent results!` tại `android.util.LruCache.trimToSize`.

**Đối chiếu A/B đã làm:** cùng 150 test trên bản TRƯỚC commit BUG-73 (`acquireFromCache` = 0) cho CÙNG lỗi → lỗi **có sẵn từ trước BUG-73**, không do BUG-73 gây ra.

**Điều bất thường chưa giải thích được:** chạy RIÊNG lớp 6 test này (kể cả trên mã có BUG-73) → 6/6 xanh. Chỉ đỏ khi chạy sau 144 test khác → lỗi phụ thuộc trạng thái singleton `BitmapCache` do test trước để lại.

## Giả thuyết (CHƯA kiểm chứng)
`sizeOf()` dùng `value.bitmap.allocationByteCount`. `LruCache` yêu cầu `sizeOf` ổn định giữa `put` và `remove`. Nếu một entry nằm trong cache nhưng bitmap của nó đã bị `recycle()` (qua `release()` về 0 sau khi `markEvictedAndRecycleIfUnused` đã đánh dấu evicted), `allocationByteCount` trên bitmap recycled ném hoặc trả giá trị khác → `trimToSize` thấy kích thước đổi → ném.

## Cách kiểm chứng đề xuất (trước khi sửa)
1. Chạy lại 150 test, bật log in ra test nào chạy ngay TRƯỚC test đỏ đầu tiên.
2. Viết androidTest tái hiện tất định: put 1 entry, recycle bitmap của nó thủ công, rồi put thêm entry khác → xem có ném không.
3. Chỉ khi RED được tái hiện mới sửa (vd cache kích thước tại thời điểm put, không đọc lại `allocationByteCount`).

## Acceptance Criteria
- [x] Tái hiện được lỗi bằng test tất định trên thiết bị thật (RED).
- [x] `connectedDebugAndroidTest` toàn bộ 150 test xanh trên Pixel.
- [x] Không đổi hành vi evict/recycle hiện có (`BitmapCacheTest`, `BitmapCacheAcquireRaceRoboTest`, `BitmapUtilsInputStreamCountRoboTest` vẫn xanh).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-76`.

## Kết quả kiểm chứng (2026-10-05, Galaxy S24 Ultra `R5CX613VZBR`, Android 16)
**Nguyên nhân (đã chứng minh, không còn là giả thuyết):** `sizeOf()` đọc lại `bitmap.allocationByteCount`; bitmap đã `recycle()` mà còn trong cache trả giá trị khác lúc `put` → `LruCache.trimToSize` ném. Test mới `BitmapCacheRecycledEntryIntegrationTest` (put 1 entry, `recycle()` thủ công, `clearCache()`) → RED `IllegalStateException: ... sizeOf() is reporting inconsistent results!`.
**Fix:** `BitmapValue.sizeKb` chốt MỘT lần lúc tạo value (bọc `runCatching`, bitmap null → 1KB như mặc định LruCache); `sizeOf` trả `value.sizeKb`. Gom `1024` thành `BYTES_PER_KB`.

- **Audit:** 9.2/10 — sửa nhỏ, đúng gốc, kích thước ổn định theo định nghĩa. Trừ điểm: kích thước cache không phản ánh nếu bitmap bị đổi sau khi cache (chưa có caller nào làm vậy).
- **A/B toàn suite trên cùng máy:** code CŨ → `BUILD FAILED`, 7 failure (6 test cũ + test mới, cùng lỗi `inconsistent`); code SỬA → `BUILD SUCCESSFUL`, **151 test, 0 fail, 0 error, 1 skip** (skip là `locationToken_realGeocoder_...`: `assumeTrue` do thiết bị không có Geocoder/offline, không liên quan).
- **Unit:** `BitmapCache*` + `BitmapUtils*` 10 lớp, 0 fail (XML mtime kiểm), ktlint xanh.
- **Giới hạn nói thẳng:** (1) test mới cố ý vi phạm hợp đồng (recycle bitmap còn trong cache) để tái hiện tất định — trong luồng thật chưa chỉ ra ai recycle trước; fix làm cache chịu được trường hợp đó chứ không ngăn nó xảy ra. (2) Chưa chạy lại toàn suite trên Pixel (máy mất kết nối) — kết quả là trên S24 Ultra. (3) Lỗi từng chỉ hiện khi chạy sau test khác (singleton); không xác định test nào gây ra đầu tiên.
