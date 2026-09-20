---
id: ENH-28
type: Enhancement
effort: XS
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SignatureBottomSheetFragment.kt
---

# `SignatureBottomSheetFragment.saveBitmapToCache()` leak file descriptor khi `compress()` ném exception

## Mô tả
Dùng `FileOutputStream(file); bitmap.compress(...); fos.close()` thủ công (không `try/finally`/`.use{}`) — nếu `compress()` ném exception giữa chừng, `fos.close()` không bao giờ được gọi, leak file descriptor. So sánh `QrCodeBottomSheetFragment.saveBitmapToCache()` cùng pattern nhưng đã dùng đúng `.use{}`.

## Triển khai
Đổi sang `FileOutputStream(file).use { fos -> bitmap.compress(...) }` giống `QrCodeBottomSheetFragment`, đảm bảo đóng stream kể cả khi exception.

## Acceptance Criteria
- [ ] Giả lập `compress()` ném exception (bitmap hỏng) — không leak file descriptor (kiểm tra qua `lsof`/StrictMode ResourceLeak detector).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-28`, file ticket = `todo/ENH-28-signaturebottomsheetfragmentsavebitmaptocache-leak-file-desc.md`.
