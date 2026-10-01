---
id: BUG-50
type: Bug
priority: P2
effort: XS
sources: full codebase audit (general-purpose agent, 2026-10-01) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/data/backup/BackupRestoreEngine.kt
---

# `BackupRestoreEngine` — 1 file signature lỗi khi đọc làm hỏng toàn bộ quá trình backup

## Mô tả
`signatureFiles.forEach { file.inputStream().use { ... } }` (dòng 46-49) không có try/catch riêng cho từng file trong vòng lặp. Có khoảng hở thời gian giữa lúc `SignatureRepository.getAllSignatures()` liệt kê danh sách file và lúc code thật sự mở `inputStream()` để đọc — nếu 1 file signature bị xoá/di chuyển giữa 2 thời điểm đó (user dọn dẹp storage, file bị OS thu hồi...), `file.inputStream()` ném `FileNotFoundException` bay thẳng ra khỏi `forEach`, xuyên qua khối `ZipOutputStream.use {...}` bao ngoài → toàn bộ quá trình backup bị huỷ, kể cả mọi template và mọi signature KHÁC đã đọc được thành công trước đó trong cùng lượt chạy.

## Đề xuất
Bọc try/catch quanh việc đọc từng file signature bên trong `forEach`, log lỗi + skip file đó, tiếp tục ghi các file còn lại vào zip. Không để 1 file lỗi chặn toàn bộ backup.

## Acceptance Criteria
- [x] 1 file signature bị xoá giữa lúc liệt kê và lúc đọc → backup vẫn hoàn tất với mọi template + mọi signature đọc được khác, chỉ thiếu đúng file lỗi.
- [x] Có log/ghi nhận rõ ràng file nào bị skip và lý do (không nuốt lỗi âm thầm).
- [x] Unit test mô phỏng 1 file trong danh sách bị xoá trước khi đọc (hoặc throw `FileNotFoundException` qua fake/mock), xác nhận zip kết quả vẫn chứa các file khác.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-50`, file ticket = `todo/BUG-50-backuprestoreengine-1-file-signature-loi-huy-toan-bo-backup.md`.

## Kết quả kiểm chứng

**Fix:** `writeBackup()` — mở `file.inputStream()` TRƯỚC khi `zip.putNextEntry()` (TDD phát hiện: thứ tự ngược lại để lại entry rỗng trong zip khi file lỗi). Bọc `try/catch (IOException)` quanh cặp mở-đọc-ghi mỗi signature, log qua `AppLog.w(TAG, ...)` kèm tên file + exception, tiếp tục vòng lặp — không để 1 file lỗi huỷ `ZipOutputStream` ngoài.

- **Audit:** 9.5/10 — đúng scope ticket, không đụng luồng khác, không magic number/force-unwrap, `inputStream()` đóng qua `.use{}` (không leak), log theo đúng convention `AppLog.w(TAG, msg, e)` sẵn có trong codebase (vd. `GalleryFragment`).
- **Unit test:** thêm `writeBackup_oneSignatureFileMissing_othersStillWritten` (TDD — xem RED thật: `FileNotFoundException` bay ra trước khi fix) vào `BackupRestoreEngineTest.kt`. `./gradlew :app:testDebugUnitTest` — **toàn bộ xanh** (bao gồm 6 test cũ + 1 test mới của class này, không có regression ở các test khác).
- **ktlint:** `./gradlew ktlintCheck` — pass, không vi phạm style.
- **Smoke test thật** trên device đã khoá session (TECNO KJ7, serial `115333744A005844`): cài `assembleDebug`, mở app → Thông tin → Sao lưu → chọn thư mục SAF → Lưu. Flow hoàn tất không crash, không FATAL trong logcat; `adb pull` file zip kết quả, `unzip -l` xác nhận cấu trúc hợp lệ (`templates/0.txt`, 87 bytes nội dung seed template). Race condition thật (xoá file giữa lúc liệt kê/đọc) không mô phỏng được qua UI thủ công — đã phủ đầy đủ bằng unit test ở trên; smoke test xác nhận không có regression trên luồng backup bình thường.
