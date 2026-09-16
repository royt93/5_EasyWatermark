---
id: BUG-31
priority: P1
type: Bug
effort: M
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/data/backup/BackupRestoreEngine.kt
---

# Restore backup đọc nguyên từng zip entry vào RAM, không giới hạn — rủi ro OOM/zip-bomb

## Mô tả
`readBackup()` gọi `zip.readBytes()` cho MỌI entry (dòng 54) trước khi phân loại template/signature, không giới hạn kích thước/số lượng entry. Một file zip người dùng chọn qua SAF (input không tin cậy — đã có tiền lệ zip-slip fix ở FEAT-05) có thể chứa entry rất lớn hoặc zip-bomb (entry nhỏ nén nhưng giải nén ra khổng lồ), khiến `readBytes()` OOM ngay trong quá trình restore.

## Triển khai
Giới hạn kích thước tối đa/entry (vd 20MB, đủ cho ảnh signature lớn nhất hợp lý) + giới hạn tổng số entry — từ chối/bỏ qua entry vượt ngưỡng thay vì đọc hết vào RAM.

## Acceptance Criteria
- [ ] Tạo file zip test với 1 entry giả rất lớn (vd 100MB) — `readBackup()` từ chối/bỏ qua entry đó, không OOM, các entry hợp lệ khác vẫn restore đúng.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-31`, file ticket = `todo/BUG-31-restore-backup-doc-nguyen-tung-zip-entry-vao-ram-khong-gioi.md`.
