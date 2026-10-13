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
**Status:** skipped by product decision (2026-10-14)

## Finding đã xác minh

`AndroidManifest.xml` đặt `allowBackup="true"` + `dataExtractionRules` + `fullBackupContent`, nhưng cả `data_extraction_rules.xml` lẫn `backup_rules.xml` chỉ loại `sharedpref/loitp_admob.xml`. Mọi thứ còn lại được backup, gồm:

- `recipient-db` (Room, `AppModule.kt:99`) — `Recipient.name/code/notes`: tên + ghi chú khách hàng (IDEA-10), dữ liệu về người chưa từng cài app.
- `filesDir/signatures/` (`SignatureRepository.kt:68`) — ảnh chữ ký tay thật của user.
- `watermark-profile-db`, `batch-history-db` — cấu hình + đường dẫn output.

Kịch bản: cloud backup/device-transfer khôi phục dữ liệu lên thiết bị user không còn kiểm soát. Đây là risk description đã kiểm chứng; không khẳng định backup Android luôn không mã hoá.

## Quyết định user (2026-10-14)

**Không loại trừ dữ liệu nào khỏi Auto Backup**; giữ `allowBackup=true`, cả hai backup rule XML không đổi. Đây là quyết định sản phẩm có chủ ý dù finding vẫn có rủi ro riêng tư. Không tiếp tục triển khai ticket nếu user không đổi quyết định.

## Kết quả

- Production code/rules: không sửa.
- Test: không thêm vì không có hành vi code thay đổi.
- Status: `skipped by product decision`; giữ ở `done/` như ticket đã xử lý theo quyết định, đồng thời giữ risk note ở đây.
