---
id: IDEA-12
type: Idea
effort: XL
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/export
  - app/src/main/java/com/mckimquyen/watermark/ui/widget
---

# On-device Style Coach — gợi ý font/màu/opacity/vị trí theo phong cách ảnh cá nhân

## Mô tả
Phân tích loạt ảnh của user (màu chủ đạo, độ sáng, thể loại ảnh) để tự đề xuất preset watermark (font, màu, opacity, vị trí) hợp "gu" cá nhân qua thời gian — khác IDEA-01 (né mặt/chủ thể theo ML) và IDEA-06 (auto-contrast/opacity theo TỪNG ảnh riêng lẻ, không học xu hướng chung theo thời gian).

## Đề xuất
Model on-device nhẹ (hoặc heuristic dựa palette + EXIF thể loại) học từ lịch sử preset user hay chọn/chỉnh tay, đề xuất preset mới khi bắt đầu batch mới.

## Acceptance Criteria
- [ ] Sau N lần dùng cùng 1 kiểu preset (font/màu/vị trí tương tự), app tự đề xuất preset gần giống khi mở batch mới — user có thể chấp nhận hoặc bỏ qua gợi ý.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-12`, file ticket = `todo/IDEA-12-on-device-style-coach-goi-y-fontmauopacityvi-tri-theo-phong.md`.
