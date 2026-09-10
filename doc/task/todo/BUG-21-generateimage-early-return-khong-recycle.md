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
