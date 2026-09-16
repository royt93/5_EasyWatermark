---
id: FEAT-23
type: Feature
effort: M
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/data/repo/WaterMarkRepository.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
---

# Ghi nhớ vị trí watermark riêng theo tỉ lệ khung ảnh (portrait/landscape)

## Mô tả
Vị trí/anchor watermark hiện lưu CHUNG 1 config cho MỌI ảnh bất kể tỉ lệ khung hình — ảnh dọc và ảnh ngang thường cần vị trí khác nhau (vd logo góc dưới phải trên ảnh ngang có thể bị lệch/che nội dung khác trên ảnh dọc cùng config).

## Triển khai
Lưu 2 preset offset/anchor riêng theo orientation (dọc/ngang, phân loại bằng tỉ lệ width/height ảnh đang load) trong `WaterMarkRepository`, tự động áp preset đúng khi load ảnh mới theo orientation của ảnh đó.

## Acceptance Criteria
- [ ] Chỉnh vị trí watermark riêng cho 1 ảnh dọc, load 1 ảnh ngang khác — vị trí không bị áp nhầm theo preset dọc.
- [ ] Quay lại ảnh dọc — vị trí đã chỉnh trước đó cho orientation dọc vẫn giữ nguyên.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-23`, file ticket = `todo/FEAT-23-ghi-nho-vi-tri-watermark-rieng-theo-ti-le-khung-anh-portrait.md`.
