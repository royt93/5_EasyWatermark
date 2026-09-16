---
id: FEAT-20
type: Feature
effort: M
sources: Codex + Claude (2 nguồn đồng thuận)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SaveImageBSDialogFragment.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
---

# Chia sẻ ngay các ảnh vừa export xong (share sheet/gói ZIP)

## Mô tả
Sau khi batch export xong (đã có ENH-13 hiện số thành công/thất bại), không có cách nào share ngay các ảnh vừa xuất — phải rời app vào Gallery tìm lại thủ công. Với batch nhiều ảnh, `ACTION_SEND_MULTIPLE` trực tiếp có thể kém ổn định trên 1 số app nhận (Zalo, Messenger) — cân nhắc thêm lựa chọn gói thành 1 file ZIP để share 1 URI duy nhất.

## Triển khai
Thêm nút "Chia sẻ ngay" sau khi batch export xong — mặc định `ACTION_SEND_MULTIPLE` các URI vừa export (tái dùng `shareUri` đã có trong `ImageInfo`/`Result`); tuỳ chọn phụ "Share as ZIP" gom vào 1 file tạm rồi share 1 URI.

## Acceptance Criteria
- [ ] Export batch 3 ảnh thành công, bấm "Chia sẻ ngay" — share sheet mở với đúng 3 ảnh vừa xuất (không phải ảnh gốc).
- [ ] Bật tuỳ chọn ZIP — share sheet mở với 1 file zip chứa đủ 3 ảnh.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-20`, file ticket = `todo/FEAT-20-chia-se-ngay-cac-anh-vua-export-xong-share-sheetgoi-zip.md`.
