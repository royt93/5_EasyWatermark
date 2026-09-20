---
id: BUG-33
priority: P2
type: Bug
effort: S
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SaveImageBSDialogFragment.kt
---

# Nút mở Gallery/Share sau batch xử lý sai khi có ảnh lỗi trong danh sách

## Mô tả
`openGallery()` lấy `list.first().shareUri` — nếu ảnh ĐẦU TIÊN trong batch fail nhưng ảnh sau thành công, mở gallery với URI null (ảnh lỗi không có `shareUri`). `openShare()` cũng duyệt từ toàn bộ list gốc — nhánh nhiều ảnh chỉ set `ACTION_SEND_MULTIPLE` khi `uriList.isNotEmpty()` sau khi lọc, nhưng nếu TOÀN BỘ batch fail, `startActivity(intent)` vẫn có thể chạy với intent chưa gán action nào.

## Triển khai
Lọc danh sách CHỈ ảnh success trước khi build URI cho cả 2 nút; disable/ẩn nút khi không có ảnh thành công nào; chọn ảnh success ĐẦU TIÊN (không phải index 0 thô) cho `openGallery()`.

## Acceptance Criteria
- [ ] Batch có ảnh đầu tiên lỗi (URI không tồn tại) + ảnh sau thành công — nút Gallery/Share vẫn hoạt động đúng, không mở URI null.
- [ ] Batch toàn bộ lỗi — 2 nút bị disable hoặc không crash khi bấm.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-33`, file ticket = `todo/BUG-33-nut-mo-galleryshare-sau-batch-xu-ly-sai-khi-co-anh-loi-trong.md`.
