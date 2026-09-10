---
id: IDEA-10
type: Idea
priority: P2
effort: L
sources: codex exec (external CLI, re-audit 2026-09-10)
files: []
---

# Recipient Fingerprint Batch — watermark riêng theo từng người nhận, truy nguồn rò rỉ

## Mô tả
Khác [IDEA-02](IDEA-02-invisible-watermark-steganography.md) (invisible watermark/steganography chống xoá thuần kỹ thuật), ý tưởng này thêm hẳn 1 **workflow quản lý người nhận**: mỗi lần export batch cho 1 người/nhóm cụ thể, nhúng biến thể watermark vi mô (mã ID riêng, có thể kết hợp cùng hạ tầng QR/token đã có) và lưu mapping "ảnh nào → gửi cho ai" cục bộ trên máy. Nếu sau này 1 ảnh bị phát tán trái phép, đối chiếu ngược lại mapping để biết nguồn rò rỉ từ người nhận nào.

## Vì sao đáng làm
Giá trị thực dụng cho nhóm khách hàng B2B của app (nhiếp ảnh gia bán ảnh preview cho khách, studio gửi ảnh cho nhiều đối tác) — khác hẳn nhóm dùng cá nhân thông thường, có thể là hướng định vị sản phẩm cao cấp riêng.

## Acceptance Criteria
- [ ] Tạo được danh sách người nhận, mỗi người gắn 1 mã định danh riêng.
- [ ] Export batch cho 1 người nhận cụ thể → mọi ảnh trong batch mang watermark biến thể theo mã của người đó.
- [ ] Có màn hình tra cứu: nhập/chọn 1 ảnh đã export → biết ảnh đó thuộc batch gửi cho người nhận nào.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-10`, file ticket = `todo/IDEA-10-recipient-fingerprint-batch.md`.
