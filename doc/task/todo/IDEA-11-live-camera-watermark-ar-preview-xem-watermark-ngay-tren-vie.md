---
id: IDEA-11
type: Idea
effort: XL
sources: Codex + Claude (2 nguồn đồng thuận)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui
---

# Live Camera Watermark / AR preview — xem watermark ngay trên viewfinder trước khi chụp

## Mô tả
Mọi app watermark trên thị trường (kể cả app này) đều theo luồng "chụp/chọn ảnh rồi mới watermark" — chưa có app nào cho xem watermark overlay TRỰC TIẾP trên khung ngắm camera (kiểu AR) trước khi bấm chụp. Khác biệt rõ so với luồng hậu kỳ hiện tại, và khác IDEA-01 (auto-placement ML né mặt người — vẫn là xử lý hậu kỳ sau khi có ảnh).

## Đề xuất
Tích hợp CameraX với 1 overlay layer vẽ watermark (dùng lại `WaterMarkImageView`/`buildTextBitmapShader` hiện có) đè lên `PreviewView`, chụp xong burn thẳng watermark vào ảnh theo đúng vị trí đã thấy trên viewfinder.

## Acceptance Criteria
- [ ] Mở chế độ Camera trong app, thấy watermark hiện đúng vị trí/kích thước trên khung ngắm trước khi chụp.
- [ ] Chụp xong, ảnh lưu ra có watermark đúng khớp với những gì đã thấy trên viewfinder (không lệch vị trí).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-11`, file ticket = `todo/IDEA-11-live-camera-watermark-ar-preview-xem-watermark-ngay-tren-vie.md`.
