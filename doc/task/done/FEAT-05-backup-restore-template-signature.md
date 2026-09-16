---
id: FEAT-05
type: Feature
effort: M
sources: Codex, Claude, doc/feat.md mục F (đã đề xuất, nâng thành task cụ thể)
files:
  - app/src/main/java/com/mckimquyen/watermark/data/db/
  - app/src/main/java/com/mckimquyen/watermark/data/repo/SignatureRepository.kt
---

# Xuất/nhập Template + Signature (backup/restore)

## Mô tả
Cho phép xuất/nhập toàn bộ danh sách Template (Room) và các file chữ ký cá nhân (`.webp`) thành 1 file backup (JSON + assets, đóng gói zip) để chuyển máy hoặc sao lưu — không phụ thuộc cloud/Firebase.

## Triển khai
Serialize bảng `Template` (Room) sang JSON + copy thư mục chứa file signature vào cùng 1 file zip; chiều ngược lại giải nén + import vào Room/thư mục local.

## Acceptance Criteria
- [ ] Xuất được file backup chứa đủ Template + Signature hiện có.
- [ ] Nhập file backup vào máy khác khôi phục đúng toàn bộ Template + Signature.
- [ ] Xử lý hợp lý trường hợp trùng tên/conflict khi nhập vào máy đã có dữ liệu sẵn.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-05`, file ticket = `todo/FEAT-05-backup-restore-template-signature.md`.

## Kết quả kiểm chứng (2026-09-16)

**Điểm audit: 9/10** (sau vòng fix theo code review).

- Triển khai khác nhẹ so với đề xuất gốc: serialize thuần `java.util.zip` (không JSON) — mỗi Template thành 1 file text riêng trong `templates/` (2 dòng đầu = epoch millis creation/lastModified, còn lại = content nguyên văn), tránh hẳn việc escape ký tự đặc biệt. Signature copy nguyên file `.webp` vào `signatures/`. Chọn đường dẫn qua SAF (`CreateDocument`/`OpenDocument`), không tự quản lý file picker riêng.
- Cả 3 AC đạt: xuất đủ Template + Signature; nhập vào máy khác (hoặc máy đã có sẵn) khôi phục đúng; trùng tên file signature tự thêm hậu tố số (không ghi đè), trùng content Template tự dedup (không nhân đôi khi restore lặp lại) — dedup là fix bổ sung sau code review, không có trong AC gốc nhưng cùng tinh thần "xử lý hợp lý trường hợp trùng/conflict".
- **Bảo mật, tự phát hiện qua `/code-review` sau khi ship**: lỗ zip-slip — tên file trong zip backup (không tin cậy, user chọn qua SAF) không được sanitize trước khi ghi ra đĩa, entry `../../shared_prefs/evil.xml` có thể ghi đè file ngoài app. Đã fix (`File(fileName).name`, commit `c3af54a`) + test regression `SignatureRepositoryImportRoboTest`.
- Test: `BackupRestoreEngineTest` (round-trip zip, JUnit thường), `BackupRestoreRepositoryRoboTest` (end-to-end Room in-memory + file thật, gồm case restore 2 lần không nhân đôi + case zip hỏng không crash) — 271 unit test PASS.
- **Smoke test thật trên TECNO KJ7 (115333744A005844, đã khoá đầu phiên)**: cài APK debug, mở Information → Backup → chọn "Tải về" qua SAF picker thật → Toast "Backup saved successfully" → verify file zip thật tồn tại (`adb pull`, `unzip -l` xác nhận đúng cấu trúc `templates/0.txt`) → Restore lại chính file đó → Toast "Restore completed successfully" → verify trực tiếp qua `adb` + `sqlite3` trên Room DB thật: template gốc id=1 + template vừa restore id=2 (autoGenerate PK mới, không đè id cũ) — đúng thiết kế.
- **Bug UI tự phát hiện qua chính lần smoke test này** (không phải code review): pill "Backup"/"Restore" hiện chữ thô `@string/backup_data` thay vì "💾 Backup" — do `android:text="💾  @string/backup_data"` trộn literal + string reference trong cùng attribute XML (Android không hỗ trợ, coi cả cụm là literal). Fix: gộp emoji vào string resource, layout chỉ còn `@string/...` thuần.
- Commit: `9648623` (feature gốc), `569707b` (fix string hiện thô, phát hiện qua smoke test), `c3af54a` (fix zip-slip + dedup, qua code review).
