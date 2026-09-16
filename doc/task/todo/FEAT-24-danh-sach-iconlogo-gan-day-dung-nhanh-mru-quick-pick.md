---
id: FEAT-24
type: Feature
effort: S
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg
---

# Danh sách icon/logo gần đây dùng nhanh (MRU quick-pick)

## Mô tả
Mỗi lần đổi logo/icon watermark phải mở lại Gallery chọn từ đầu — không có danh sách N icon/logo gần nhất đã dùng để chọn nhanh (khác FEAT-06 watermark profile đầy đủ có ĐẶT TÊN/quản lý — đây chỉ là MRU nhẹ, tự động, không cần thao tác đặt tên).

## Triển khai
Lưu N (vd 5-10) URI icon gần nhất user đã chọn làm watermark (kèm thumbnail nhỏ) trong DataStore, hiển thị thành hàng ngang quick-pick trong màn chọn icon watermark.

## Acceptance Criteria
- [ ] Đổi icon watermark 3 lần khác nhau — mở lại màn chọn icon thấy đúng 3 icon gần nhất theo thứ tự dùng, bấm chọn nhanh không cần mở Gallery.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-24`, file ticket = `todo/FEAT-24-danh-sach-iconlogo-gan-day-dung-nhanh-mru-quick-pick.md`.
