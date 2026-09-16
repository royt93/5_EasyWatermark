---
id: IDEA-14
type: Idea
effort: XL
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/ui
  - app/src/main/java/com/mckimquyen/watermark/export
---

# Smart Redaction + Watermark — tự phát hiện thông tin nhạy cảm trước khi đóng dấu

## Mô tả
Trước khi watermark, tự phát hiện thông tin nhạy cảm trong ảnh tài liệu/screenshot (email, số điện thoại, biển số xe, mặt người) rồi đề xuất blur/redact — SAU ĐÓ mới đóng dấu nguồn/copyright. Khác hẳn watermark/EXIF/QR hiện có (chỉ đóng dấu, không bảo vệ nội dung riêng tư).

## Đề xuất
Dùng ML Kit Text Recognition (phát hiện email/SĐT dạng text trong ảnh) + Face Detection có sẵn (Google ML Kit, on-device, miễn phí) — khoanh vùng đề xuất, user xác nhận rồi mới blur + watermark.

## Acceptance Criteria
- [ ] Ảnh screenshot chứa email/SĐT dạng text — app khoanh vùng đúng vị trí, user xác nhận blur, ảnh xuất ra đã che thông tin + có watermark.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-14`, file ticket = `todo/IDEA-14-smart-redaction-watermark-tu-phat-hien-thong-tin-nhay-cam-tr.md`.
