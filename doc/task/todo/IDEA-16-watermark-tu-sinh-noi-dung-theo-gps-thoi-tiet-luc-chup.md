---
id: IDEA-16
type: Idea
effort: L
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark
---

# Watermark tự sinh nội dung theo GPS + thời tiết lúc chụp

## Mô tả
Đọc EXIF GPS của ảnh, reverse-geocode ra địa danh + (tuỳ chọn) gọi weather API theo timestamp+toạ độ, tự điền vào token watermark text (địa danh, nhiệt độ lúc chụp) — hữu ích cho travel/food blogger, nhiếp ảnh gia bất động sản muốn ghi chú ngữ cảnh tự động thay vì gõ tay.

## Đề xuất
Thêm token mới `{location}`/`{weather}` vào `TextTokenResolver` — resolve bằng Geocoder (Android built-in, không cần API key cho reverse-geocode cơ bản) đọc EXIF GPS; weather cần API key ngoài (tuỳ chọn, tắt mặc định nếu chưa có key).

## Acceptance Criteria
- [ ] Ảnh có EXIF GPS hợp lệ, dùng token `{location}` trong watermark text — export ra đúng tên địa danh (thành phố/khu vực) tương ứng toạ độ.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-16`, file ticket = `todo/IDEA-16-watermark-tu-sinh-noi-dung-theo-gps-thoi-tiet-luc-chup.md`.
