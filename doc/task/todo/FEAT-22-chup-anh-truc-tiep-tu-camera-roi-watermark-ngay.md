---
id: FEAT-22
type: Feature
effort: S
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainActivity.kt
---

# Chụp ảnh trực tiếp từ Camera rồi watermark ngay

## Mô tả
Chưa có `ACTION_IMAGE_CAPTURE`/CameraX trong source (đã grep xác nhận) — muốn watermark ảnh vừa chụp phải mở Camera app riêng, chụp xong, rồi quay lại app này chọn từ Gallery.

## Triển khai
Thêm nút "Chụp ảnh" cạnh nút chọn Gallery hiện có ở màn Launch — dùng `ACTION_IMAGE_CAPTURE` (đơn giản, không cần thêm dependency CameraX) lưu ảnh tạm rồi đẩy thẳng vào editor watermark, bỏ bước chụp-rồi-mở-lại-Gallery.

## Acceptance Criteria
- [ ] Bấm nút Chụp ảnh, chụp 1 tấm — ảnh vào thẳng editor watermark ngay, không cần thao tác Gallery thêm.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-22`, file ticket = `todo/FEAT-22-chup-anh-truc-tiep-tu-camera-roi-watermark-ngay.md`.
