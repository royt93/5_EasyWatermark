---
id: BUG-34
priority: P2
type: Bug
effort: XS
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/data/repo/SignatureRepository.kt
---

# `saveSignature()` báo thành công dù `Bitmap.compress()` thất bại

## Mô tả
`saveSignature()` ghi WEBP qua `FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.WEBP, 100, out) }` — bỏ qua giá trị `Boolean` trả về từ `compress()` (biểu thị thành công/thất bại thật). Nếu encode thất bại (hiếm nhưng có thể xảy ra — bitmap hỏng, hết dung lượng), hàm vẫn trả `SignatureModel` hợp lệ trỏ tới file rỗng/hỏng, signature không render được khi dùng làm watermark icon. BUG-19 đã fix pattern y hệt cho nhánh ghi MediaStore export, chưa bao phủ luồng signature.

## Triển khai
Kiểm tra giá trị trả về của `compress()`, nếu `false` thì xoá file rỗng + trả `null` thay vì `SignatureModel` giả.

## Acceptance Criteria
- [ ] Mock/giả lập `compress()` trả `false` (hoặc dùng bitmap 0x0) — `saveSignature()` trả `null`, không để lại file rác.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-34`, file ticket = `todo/BUG-34-savesignature-bao-thanh-cong-du-bitmapcompress-that-bai.md`.
