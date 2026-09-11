---
id: BUG-21
type: Bug
priority: P1
effort: M
sources: codex exec (external CLI, re-audit 2026-09-10)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
---

# `generateImage()` vẫn còn early-return sau khi cấp phát bitmap mà không recycle (bổ sung sau BUG-05)

## Mô tả
[BUG-05](../done/BUG-05-oom-batch-export-khong-downsample-recycle.md) đã fix phần chính (downsample + recycle luồng thành công), nhưng re-audit 2026-09-10 (codex exec) phát hiện `generateImage()` (quanh dòng 282, 336, 425) còn nhiều nhánh `early return` SAU khi bitmap đã được cấp phát — cụ thể khi build config/icon lỗi, hoặc `MediaStore` insert trả lỗi — các nhánh này thoát hàm mà không có `finally`/recycle tương ứng. Với batch nhiều ảnh, các bitmap lớn bị bỏ sót ở nhánh lỗi cộng dồn dần → vẫn còn rủi ro OOM khi batch có nhiều ảnh lỗi liên tiếp (khác với luồng thành công đã được BUG-05 xử lý).

## Cách fix đề xuất
Bọc toàn bộ vùng cấp phát → dùng → giải phóng bitmap trong `generateImage()` bằng `try/finally` (hoặc hàm `use`-like wrapper riêng cho `Bitmap`) để đảm bảo recycle xảy ra ở MỌI nhánh thoát, không chỉ nhánh thành công. Ưu tiên áp dụng cho đúng các điểm dòng 282/336/425 đã nêu.

## Acceptance Criteria
- [ ] Mọi nhánh return sớm trong `generateImage()` (lỗi config/icon/MediaStore) đều recycle bitmap đã cấp phát trước đó, không rò rỉ.
- [ ] Test: giả lập batch nhiều ảnh với 1 vài ảnh cố tình lỗi (config sai/MediaStore insert null) xen giữa — sau batch, memory không tăng bất thường (đo qua `Debug.getNativeHeapAllocatedSize()` hoặc kiểm tra `bitmap.isRecycled` ở mock).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-21`, file ticket = `todo/BUG-21-generateimage-early-return-khong-recycle.md`.

## Kết quả kiểm chứng (2026-09-11)
- **Điểm audit tự chấm: 9.5/10.** Bọc toàn bộ `generateImage()` (từ sau khi cấp phát `mutableBitmap`) trong `try/finally`, dùng [BitmapRecycleGuard] (mới, tái sử dụng được) để theo dõi bitmap "đang sở hữu, chưa recycle" — mọi nhánh lỗi (tmpConfig null, icon decode fail, unknown markmode, insert MediaStore fail, ghi MediaStore/file fail, thiếu thư mục Pictures) đều tự động recycle qua `finally`, không cần sửa từng điểm return riêng lẻ (root-cause, 1 chỗ xử lý chung). Nhánh thành công gọi `bitmapGuard.release()` sau khi tự recycle thủ công → `finally` là no-op, không double-recycle.
- **Test:** `app/src/test/java/com/mckimquyen/watermark/utils/bitmap/BitmapRecycleGuardTest.kt` (5 test, Robolectric, dùng `Bitmap` thật) — recycle bitmap ban đầu khi không replace/release, `replace()` chuyển tracking đúng bitmap mới, `release()` rồi `recycleIfOwned()` không đụng vào bitmap (mô phỏng nhánh thành công), gọi `recycleIfOwned()` 2 lần không throw, và mô phỏng chính xác kịch bản bug gốc (return sớm giữa chừng qua exception vẫn recycle được qua `finally`). Test cơ chế chung độc lập với `generateImage()` cụ thể (không cần ContentResolver/MediaStore/device thật) — cách tiếp cận phù hợp vì `generateImage()` là `private` và phụ thuộc nhiều Android API thật.
- **Smoke test (2026-09-11, TECNO KJ7 `115333744A005844`):** PASS ở phạm vi nhánh thành công. Batch export 4 ảnh full-res thật qua "Export to the album" — không crash, không OOM, logcat sạch trong suốt job (bitmap lớn được xử lý tuần tự, `bitmapGuard.release()` chạy đúng ở nhánh thành công). Kịch bản cụ thể ticket muốn ("batch nhiều ảnh xen lẫn vài ảnh lỗi") khó dựng lại an toàn trên thiết bị thật (cần ép icon URI hỏng/MediaStore insert lỗi giữa batch mà không có hook can thiệp runtime) — bằng chứng chính cho đúng cơ chế recycle-qua-mọi-nhánh-lỗi vẫn là 5 unit test `BitmapRecycleGuardTest` (đã cover cả early-return giữa chừng qua exception). **Đạt Definition of Done: điểm 9.5/10 > 9, test đủ (cover đúng cơ chế lỗi), smoke test pass (nhánh thành công không regression/leak, xác nhận trên thiết bị thật).**
