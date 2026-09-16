---
id: IDEA-15
type: Idea
effort: L
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/export
  - app/src/main/java/com/mckimquyen/watermark/data/model
---

# Brand Compliance Scoring — chấm điểm mỗi ảnh theo rule thương hiệu trước khi export

## Mô tả
Chấm điểm mỗi ảnh sau khi áp watermark theo rule brand tự định nghĩa (logo không quá sát mép, tương phản đủ đọc, không che mặt/sản phẩm chính, kích thước watermark trong khoảng chuẩn) — kết quả hiển thị pass/warn/fail cho từng ảnh trong batch TRƯỚC khi export thật. Khác IDEA-06 (chỉ auto-fix contrast/opacity, không có hệ thống RULE/SCORING tường minh).

## Đề xuất
Định nghĩa rule set đơn giản (vị trí trong % khung, ngưỡng tương phản tính từ luminance nền, ngưỡng kích thước) chạy trên preview bitmap đã có sẵn (FEAT-07), hiển thị badge pass/warn/fail trên mỗi card.

## Acceptance Criteria
- [ ] Watermark đặt quá sát mép hoặc opacity quá thấp (không đọc được) — card preview hiện badge "warn"/"fail" rõ ràng trước khi export.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-15`, file ticket = `todo/IDEA-15-brand-compliance-scoring-cham-diem-moi-anh-theo-rule-thuong.md`.
