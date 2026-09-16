---
id: IDEA-13
type: Idea
effort: XL
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/export
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg
---

# Client Proofing Mode — xuất album proof cho khách chọn ảnh (dành cho photographer/freelancer)

## Mô tả
Biến app từ công cụ đóng dấu đơn thuần thành luồng duyệt ảnh bán hàng cho photographer/freelancer: xuất 1 album proof tạm với watermark LỚN (chống dùng ảnh chưa mua) kèm mã số mỗi ảnh + file index (HTML/PDF) để khách chọn ảnh cần mua.

## Đề xuất
Thêm export mode riêng "Proofing": watermark cố định lớn + số thứ tự trên mỗi ảnh, sinh kèm file HTML/PDF liệt kê số thứ tự + thumbnail để gửi khách.

## Acceptance Criteria
- [ ] Export 1 batch ở chế độ Proofing — mỗi ảnh có watermark lớn + số thứ tự riêng biệt, kèm 1 file index liệt kê đúng thứ tự khớp với ảnh.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-13`, file ticket = `todo/IDEA-13-client-proofing-mode-xuat-album-proof-cho-khach-chon-anh-dan.md`.
