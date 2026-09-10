---
id: FEAT-07
type: Feature
effort: M
sources: Codex, Internal (2/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
---

# Preview grid trước khi export cả batch

## Mô tả
Trước khi chạy `generateList` cho cả batch, hiện 1 lưới (grid) thumbnail preview watermark áp lên từng ảnh để người dùng phát hiện lỗi trước (chữ tràn khung, EXIF thiếu, watermark che mất chi tiết quan trọng...) trước khi tốn thời gian export cả loạt lớn.

## Đề xuất bổ sung (từ Codex)
Kết hợp hiển thị số ảnh, kích thước dự kiến, định dạng, dung lượng gần đúng, cảnh báo bộ nhớ — người dùng có thể đổi preset resize/compression ngay tại đây trước khi bắt đầu batch dài.

## Acceptance Criteria
- [ ] Trước khi export, hiển thị grid thumbnail có watermark áp sẵn cho từng ảnh trong batch.
- [ ] Hiển thị ước tính dung lượng/kích thước output.
- [ ] Người dùng có thể quay lại chỉnh sửa trước khi xác nhận export.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-07`, file ticket = `todo/FEAT-07-preview-grid-truoc-khi-export.md`.
