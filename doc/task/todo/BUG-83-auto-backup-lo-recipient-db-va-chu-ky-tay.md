---
id: BUG-83
type: Bug
priority: P1
effort: XS
sources: R5 security re-audit 2026-10-14 (verify tay: manifest + xml + AppModule + SignatureRepository)
files:
  - app/src/main/AndroidManifest.xml
  - app/src/main/res/xml/data_extraction_rules.xml
  - app/src/main/res/xml/backup_rules.xml
---

# BUG-83: Auto Backup đẩy `recipient-db` (PII bên thứ ba) và chữ ký tay lên cloud/device-transfer

**Priority:** P1 (dữ liệu cá nhân người KHÔNG dùng app rời thiết bị)
**Effort:** XS (<2h)
**Status:** todo

## Vấn đề

`AndroidManifest.xml` đặt `allowBackup="true"` + `dataExtractionRules` + `fullBackupContent`, nhưng cả `data_extraction_rules.xml` lẫn `backup_rules.xml` chỉ loại đúng 1 file (`sharedpref/loitp_admob.xml`). Mọi thứ còn lại được backup, gồm:

- `recipient-db` (Room, `AppModule.kt:99`) — `Recipient.name/code/notes`: tên + ghi chú khách hàng (IDEA-10), tức dữ liệu về người chưa từng cài app.
- `filesDir/signatures/` (`SignatureRepository.kt:68`) — ảnh chữ ký tay thật của user.
- `watermark-profile-db`, `batch-history-db` — chứa cấu hình + đường dẫn output.

Kịch bản: Google cloud backup hoặc "chuyển dữ liệu sang máy mới" khôi phục toàn bộ lên thiết bị user không còn kiểm soát (bán máy, tài khoản cũ còn đăng nhập).

App ĐÃ có backup/restore tự quản (FEAT-05: SAF + zip, có kiểm tra bounds + Zip Slip) nên Auto Backup không phải đường backup chủ đích.

## Cần chốt trước khi làm (quyết định sản phẩm)

Loại hẳn khỏi Auto Backup (khuyến nghị — user dùng FEAT-05 khi cần) hay chỉ loại `recipient-db` + `signatures`.

## Đề xuất

Thêm `<exclude>` ở CẢ HAI file, đúng cú pháp từng phiên bản:

- `data_extraction_rules.xml`: trong `<cloud-backup>` và `<device-transfer>` thêm `<exclude domain="database" path="recipient-db"/>`, `<exclude domain="file" path="signatures/"/>` (+ 2 DB còn lại nếu chốt loại).
- `backup_rules.xml` (Android ≤11): cùng các dòng trong `<full-backup-content>`.

Lưu ý Room: file phụ `-wal`/`-shm` đi theo `domain="database"`; kiểm tra `path` khớp tên file DB thật.

## Test

- Unit/Robolectric: parse XML bằng `XmlResourceParser`, assert 2 file đều chứa exclude `recipient-db` và `signatures/` (khoá để lần sau thêm DB mới không quên). Lưu ý phải test cả 2 file, 3 domain.
- Integration: `adb shell bmgr backupnow` / `dumpsys backup` trên thiết bị, xác nhận `recipient-db` không có trong dữ liệu backup. Ghi rõ nếu thiết bị không bật Google backup.

## Prompt loop

Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).
