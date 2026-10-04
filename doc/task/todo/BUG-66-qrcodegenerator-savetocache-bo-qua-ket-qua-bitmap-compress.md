---
id: BUG-66
type: Bug
priority: P2
effort: XS
sources: re-audit 2026-10-04 (2 agent đọc code) + verify tay hình dạng code; chưa tái hiện trên máy trừ khi ghi khác
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/QrCodeGenerator.kt
---

# Qrcodegenerator savetocache bo qua ket qua bitmap compress

## Mô tả
`saveToCache()` (~dòng 72-84) bỏ qua `Boolean` trả về của `bitmap.compress()`; nếu `false` vẫn tạo `FileProvider` URI và báo thành công.

**Kịch bản:** Encoder/ghi PNG thất bại → file rỗng/hỏng nhưng `updateIcon()` vẫn lưu URI, watermark không render, user không nhận lỗi.

## Đề xuất
Kiểm tra giá trị `compress`; `false` thì xoá file và trả `null` (caller đã toast `save_failed`). Cùng pattern BUG-34.

## Acceptance Criteria
- [ ] `compress()` trả false → `saveToCache` trả null và không để lại file.
- [ ] Unit test (bitmap giả compress false).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-66`, file ticket = `todo/BUG-66-qrcodegenerator-savetocache-bo-qua-ket-qua-bitmap-compress.md`.
