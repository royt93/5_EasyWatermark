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
- [x] Zip backup chứa 1 entry `.webp` giả (bytes ngẫu nhiên, không phải ảnh) — restore bỏ qua entry đó, không ghi ra đĩa, các signature hợp lệ khác trong cùng zip vẫn restore đúng.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-31`, file ticket = `todo/ENH-31-restore-backup-nen-validate-signature-la-anh-that-truoc-khi.md`.

## Kết quả kiểm chứng
- Thêm `SignatureRepository.isValidImageBytes()` (companion, `internal`) — decode `inJustDecodeBounds = true`, chấp nhận khi `outWidth`/`outHeight` nằm trong `1..8000` (chặn decompression bomb), từ chối phần còn lại. `importSignatureBytes()` gọi hàm này trước khi ghi bytes ra đĩa, trả `null` nếu không hợp lệ — `BackupRestoreRepository.forEach` gọi sẵn không quan tâm giá trị trả về nên tự động bỏ qua entry lỗi và tiếp tục entry kế tiếp trong cùng zip (không cần sửa call site).
- File ticket ghi `BackupRestoreEngine.kt` nhưng call site thật là `data/repo/BackupRestoreRepository.kt` (tên file cũ trong metadata ticket đã lệch thực tế, không phải bug).
- Test: `SignatureRepositoryImportRoboTest` (Robolectric, JVM) — sửa lại 2 test cũ đang dùng bytes giả (`byteArrayOf(1,2,3,4)`) thành WEBP thật (`Bitmap.compress`) vì giờ bị validate chặn; thêm case `importSignatureBytes_validImageBytes_isAcceptedByBoundsCheck`. Case "từ chối bytes rác" **không** verify bằng Robolectric — decode native shadow của Robolectric không thất bại đúng như Skia thật cho bytes hoàn toàn không phải ảnh (verify thực nghiệm: assertion tại Robolectric trả `true` sai, cùng lớp lý do `BitmapUtilsDecodeFailureIntegrationTest` đã là androidTest) — thêm `SignatureRepositoryImportIntegrationTest` (androidTest) verify đúng: bytes rác bị từ chối + entry hợp lệ ngay sau đó trong cùng lượt vẫn import đúng.
- `./gradlew :app:testDebugUnitTest` toàn bộ PASS. `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=...SignatureRepositoryImportIntegrationTest` PASS trên device khoá `115333744A005844` (TECNO KJ7) — xác nhận hành vi decode thật đúng AC.
- Audit: 9.5/10 — đúng scope, có bằng chứng thực nghiệm cho quyết định test ở tầng nào (không đoán).
- Smoke test: `installDebug` lên device khoá, `am start SplashActivity` — mở sạch, logcat không `FATAL EXCEPTION`.
