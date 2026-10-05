---
id: BUG-73
type: Bug
priority: P1
effort: M
sources: re-audit 2026-10-04 (2 agent đọc code) + verify tay hình dạng code; chưa tái hiện trên máy trừ khi ghi khác
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/BitmapCache.kt
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt
---

# Bitmapcache tra value chua retain co the bi recycle truoc khi caller dung

## Mô tả
`getFromCache()` trả `BitmapValue` khi `refCount` còn 0; caller (`BatchExportEngine.kt` ~987-999) chỉ `retain()` SAU khi hàm suspend trả về. Giữa 2 thao tác, thread khác có thể `evictAll()`/evict → `markEvictedAndRecycleIfUnused()` thấy refCount 0 và recycle bitmap; caller retain một value đã chứa bitmap recycled. (Đã verify hình dạng code; **race hẹp, chưa tái hiện**.)

**Kịch bản:** Preview/export lấy cache hit đúng lúc batch khác kết thúc và gọi `clearCache()` → `copy()`/`drawBitmap()` kế tiếp ném `IllegalStateException`/"recycled bitmap".

## Đề xuất
Thay `getFromCache()` bằng thao tác acquire nguyên tử: retain thành công mới trả; trạng thái evicted chặn retain để caller decode lại (synchronized/CAS).

## Acceptance Criteria
- [x] Có test đồng thời (nhiều thread get/evict) không bao giờ trả bitmap recycled.
- [x] Không đổi hành vi cache hit bình thường.
- [x] Hiệu năng cache không giảm đáng kể.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-73`, file ticket = `todo/BUG-73-bitmapcache-tra-value-chua-retain-co-the-bi-recycle-truoc-khi-caller-dung.md`.

## Kết quả kiểm chứng

**Tái hiện thật (một nhánh):** `BitmapCacheAcquireRaceRoboTest` mô phỏng TẤT ĐỊNH khe hở: caller `getFromCache()` rồi `clearCache()` xen vào TRƯỚC `retain()` → bitmap bị recycle dưới chân caller → RED (`isRecycled expected false`). Ca ép evict bằng nhét 400 entry lớn KHÔNG tái hiện được (PASS) — không chứng minh được nhánh evict trong Robolectric. Ca đối chứng (retain trước) PASS.
**Fix (đổi hợp đồng, đã chọn "sửa đầy đủ 11 call site"):**
- `BitmapCache.acquireFromCache` / `addToCacheAndAcquire`: lấy/đặt value rồi `retain()` trong cùng khối `synchronized(cacheLock)` với `clearCache()`.
- `decodeSampledBitmapFromResource` trả value LUÔN đã retain (cả cache miss lẫn hit); caller phải `release()` đúng 1 lần và KHÔNG tự `retain()`.
- 11 call site production (`BatchExportEngine` 6, `AutoPlacementEngine` 2, `WaterMarkImageView` 3): bỏ `retain()` muộn; thêm `release()` ở 3 đường thoát sớm khi `bitmap == null` (ảnh chính của `WaterMarkImageView`, 2 nhánh icon preview/compare của `BatchExportEngine`) — thiếu thì rò tham chiếu mỗi lần decode lỗi.

- **Audit:** 8.8/10 — fix đóng đúng khe hở đã tái hiện, nhưng đổi hợp đồng lõi bộ nhớ của 11 nơi mà không kiểm chứng được trên máy. Chữ ký hàm không đổi nên trình biên dịch KHÔNG bắt được caller quên cập nhật — dựa vào rà tay từng chỗ + toàn bộ suite.
- **Test:** `BitmapCacheAcquireRaceRoboTest` viết lại 6 test (acquire rồi clearCache không recycle, chỉ recycle sau release; `addToCacheAndAcquire`; key vắng → null; test tài liệu chứng minh luồng thô vẫn không an toàn); `BitmapUtilsInputStreamCountRoboTest` +1 test hợp đồng (refCount = 1 khi miss, = 2 khi hit, `clearCache` sau khi trả về không recycle). Toàn bộ suite lượt cuối: **1283 test, 0 fail, 268 lớp**, ktlint xanh.
- **Phát sinh khi chạy suite (không do BUG-73):** (1) test `QrCodeGeneratorTest.saveToCache_...` của BUG-66 đỏ khi chạy trong suite vì `FileProvider.sCache` tĩnh — đã thêm đoạn reset như các test khác; (2) `ToastExtensionWidgetTest.fragmentToast_showsSnackbarAnchoredOnFragmentView` đỏ 1 lượt suite (Snackbar chưa hiện), pass 3/3 riêng lẻ và pass lượt kế — chập chờn, đã ghi BUG-75.
- **Chưa làm / không chứng minh được:** nhánh evict (không tái hiện được); hành vi trên máy thật; smoke test preview + export (Pixel mất kết nối). Đây là thay đổi rủi ro cao nhất của đợt re-audit — nên smoke test preview/export TRƯỚC khi phát hành.

## Smoke test thật (2026-10-05)
- **Máy:** Pixel 7 Pro `2B051FDH3006MU`, APK debug từ HEAD `3d2f3650`. Chọn 3 ảnh, đổi thumbnail 18 lần liên tiếp (đường preview), rồi xuất batch 3 ảnh (đường export).
- **Kết quả:** không có `FATAL EXCEPTION`/`OutOfMemory`/`recycled bitmap`/`inconsistent` trong logcat; preview đúng ảnh; xuất 3 file JPEG thành công, Lịch sử xuất có entry "3 thành công · 0 lỗi". Mở lại trên Galaxy S24 Ultra `R5CX613VZBR` (3 ảnh test) cũng không lỗi.
- **Giới hạn nói thẳng:** chỉ chứng minh không hồi quy trong luồng thường; KHÔNG tái hiện nhánh evict/race trên máy, không so A/B với code cũ. Điểm audit giữ 8.8/10. `BitmapCache.sizeOf` vẫn là BUG-76 (chưa sửa).
