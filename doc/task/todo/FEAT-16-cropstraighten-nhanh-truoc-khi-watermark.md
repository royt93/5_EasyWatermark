---
id: FEAT-16
type: Feature
effort: L
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt
  - app/src/main/java/com/mckimquyen/watermark/data/model/ImageInfo.kt
---

# Crop/straighten nhanh trước khi watermark

## Mô tả
Chưa có công cụ chỉnh crop tỉ lệ (1:1, 4:5, 16:9...) hay xoay thẳng nhẹ (deskew) trước khi đóng dấu — nhu cầu thực tế phổ biến trước khi watermark ảnh sản phẩm/tài liệu cho đúng khung chuẩn nền tảng đăng (khác FEAT-09 chỉ resize theo cạnh dài, không đổi khung hình/crop nội dung).

## Triển khai
Thêm màn hình crop đơn giản (thư viện crop có sẵn hoặc tự vẽ overlay) trước bước watermark, lưu crop rect + góc xoay vào `ImageInfo`, áp dụng transform này TRƯỚC khi build watermark shader (cả preview lẫn export dùng chung).

## Acceptance Criteria
- [ ] Crop ảnh theo 1 tỉ lệ chuẩn (vd 1:1) rồi export — file kết quả đúng tỉ lệ đã crop, watermark vẫn đúng vị trí tương đối theo khung mới.
- [ ] Xoay thẳng nhẹ vài độ rồi export — ảnh xuất ra đã xoay đúng, không mất watermark/méo hình.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-16`, file ticket = `todo/FEAT-16-cropstraighten-nhanh-truoc-khi-watermark.md`.
