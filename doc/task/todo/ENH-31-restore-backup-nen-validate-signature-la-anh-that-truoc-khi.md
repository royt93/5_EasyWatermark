---
id: ENH-31
type: Enhancement
effort: M
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/data/repo/SignatureRepository.kt
  - app/src/main/java/com/mckimquyen/watermark/data/backup/BackupRestoreEngine.kt
---

# Restore backup nên validate signature là ảnh thật trước khi ghi ra đĩa

## Mô tả
`importSignatureBytes()` hiện chỉ sanitize TÊN FILE (`File(fileName).name` — fix zip-slip ở FEAT-05), sau đó ghi bytes trực tiếp không kiểm tra NỘI DUNG. Một file zip backup giả mạo có thể chứa bytes bất kỳ đặt tên `.webp` — ghi thẳng vào `signatureDir`, sau đó `WaterMarkImageView` cố decode làm bitmap có thể lỗi/crash không rõ ràng thay vì báo lỗi restore rõ ràng ngay lúc import.

## Triển khai
Trước khi ghi, decode thử bằng `BitmapFactory.Options.inJustDecodeBounds = true` để xác nhận đúng là ảnh hợp lệ + giới hạn kích thước pixel hợp lý, từ chối entry không phải ảnh thật.

## Acceptance Criteria
- [ ] Zip backup chứa 1 entry `.webp` giả (bytes ngẫu nhiên, không phải ảnh) — restore bỏ qua entry đó, không ghi ra đĩa, các signature hợp lệ khác trong cùng zip vẫn restore đúng.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-31`, file ticket = `todo/ENH-31-restore-backup-nen-validate-signature-la-anh-that-truoc-khi.md`.
