---
id: IDEA-18
type: Idea
effort: XL
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/ExifBorderRenderer.kt
---

# Bộ sinh khung EXIF border mới theo bảng màu ảnh (không giới hạn 4 style cố định)

## Mô tả
Hiện chỉ có 4 style khung cố định (Classic/Polaroid/Film Strip/Minimal, tham số hoá thủ công ở FEAT-14) — thay vì chọn 1 trong 4 style có sẵn, phân tích palette chủ đạo của chính ảnh đang xử lý rồi TỰ SINH bố cục khung mới (màu nền, font accent, vị trí thông số EXIF) khớp tông màu ảnh, tạo cảm giác "khung riêng cho từng ảnh" thay vì lặp lại 4 template cố định.

## Đề xuất
Dùng `Palette` API (androidx.palette, có sẵn trong nhiều nơi code đã dùng Palette cho dynamic color) trích màu chủ đạo từ ảnh, sinh bảng màu khung (nền/chữ/viền) theo màu đó thay vì màu cố định trong 4 style hiện có — vẫn tái dùng layout logic (vị trí text, kích thước band) của `ExifBorderRenderer`.

## Acceptance Criteria
- [ ] 2 ảnh có tông màu khác biệt rõ (1 ảnh tối, 1 ảnh sáng rực) — khung EXIF border sinh ra có màu nền/chữ khác nhau phù hợp từng ảnh, không dùng chung 1 bảng màu cố định.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-18`, file ticket = `todo/IDEA-18-bo-sinh-khung-exif-border-moi-theo-bang-mau-anh-khong-gioi-h.md`.
