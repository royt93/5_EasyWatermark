---
id: FEAT-18
type: Feature
effort: M
sources: Codex + Claude (2 nguồn đồng thuận)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SaveImageBSDialogFragment.kt
---

# So sánh trước/sau bằng slider (before/after compare) — cả trong editor lẫn preview batch

## Mô tả
Không có cách nào xem NHANH watermark che nội dung quan trọng tới đâu so với ảnh gốc — phải nhìn ảnh đã áp watermark rồi tự nhớ/tưởng tượng ảnh gốc. Hữu ích cả lúc chỉnh sửa 1 ảnh (editor) lẫn duyệt nhanh cả batch trong preview grid.

## Triển khai
Thêm 1 slider kéo che/lộ nửa ảnh gốc vs nửa ảnh có watermark — trong editor dùng ngay trên `WaterMarkImageView` hiện tại (không cần layer riêng, chỉ clip canvas theo vị trí slider); trong preview batch mở từ card ra view full-screen tương tự.

## Acceptance Criteria
- [ ] Kéo slider trong editor từ 0% tới 100% — thấy rõ ranh giới watermark/không-watermark di chuyển mượt theo tay.
- [ ] Mở preview 1 ảnh từ grid batch, dùng slider tương tự — không cần export thật mới thấy so sánh.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-18`, file ticket = `todo/FEAT-18-so-sanh-truocsau-bang-slider-beforeafter-compare-ca-trong-ed.md`.
