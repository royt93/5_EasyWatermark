---
id: FEAT-21
type: Feature
effort: S
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainActivity.kt
---

# Dán ảnh từ Clipboard để watermark nhanh

## Mô tả
Đã có `ClipData`/`ClipboardManager` trong `MainActivity` nhưng chỉ dùng để copy text log lỗi — chưa có luồng NHẬN ảnh copy từ app khác (Chrome, Gallery, Messenger...) qua clipboard.

## Triển khai
Thêm entry point "Paste from clipboard" đọc `ClipboardManager.primaryClip` tìm URI ảnh (`ClipData.Item.uri`/`contentResolver` MIME kiểm tra `image/*`), đẩy thẳng vào luồng watermark hiện có (tái dùng pipeline `ACTION_SEND` đã có).

## Acceptance Criteria
- [ ] Copy 1 ảnh từ app khác (vd trình duyệt, save-image-as vào clipboard), mở app, bấm "Paste" — ảnh vào editor đúng như luồng `ACTION_SEND`.
- [ ] Clipboard không có ảnh (chỉ có text) — nút Paste disable hoặc báo rõ không có ảnh để dán.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-21`, file ticket = `todo/FEAT-21-dan-anh-tu-clipboard-de-watermark-nhanh.md`.
