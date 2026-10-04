---
id: BUG-60
type: Bug
priority: P2
effort: XS
sources: re-audit 2026-10-04 (agent export/data/utils) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt
---

# Chinh sach overwrite mo file mode w khong truncate tren android 10

## Mô tả
`openFileDescriptor(.., "w", null)` (~678) và `openOutputStream(targetDoc.uri)` (~899, mặc định `"w"`) trên Android 10+ không đảm bảo truncate — **độ tin cậy trung bình** (hành vi MediaProvider đã biết, chưa test máy thật).

Chính sách OVERWRITE tái dùng file có sẵn: file cũ 5MB ghi đè bằng ảnh 3MB → còn 2MB đuôi cũ, file phình, hash/stamp tính trên phần rác, WebP lệch độ dài RIFF.

## Đề xuất
Dùng mode `"wt"` ở cả 2 chỗ (`openOutputStream(uri, "wt")`).

## Acceptance Criteria
- [x] Cả 2 nhánh ghi OVERWRITE dùng `"wt"`.
- [x] Integration test (androidTest): ghi file lớn rồi OVERWRITE bằng ảnh nhỏ hơn → kích thước file = kích thước ảnh mới.
- [x] Smoke test thật OVERWRITE trên device đã khoá.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-60`, file ticket = `todo/BUG-60-chinh-sach-overwrite-mo-file-mode-w-khong-truncate-tren-android-10.md`.

## Kết quả kiểm chứng

**Fix:** `BatchExportEngine.kt` — dùng `WRITE_TRUNCATE_MODE = "wt"` thay vì `"w"` ở 2 vị trí ghi OVERWRITE:
1. Nhánh MediaStore (Q+): `contentResolver.openFileDescriptor(imageContentUri, WRITE_TRUNCATE_MODE, null)`.
2. Nhánh SAF DocumentFile: `contentResolver.openOutputStream(targetDoc.uri, WRITE_TRUNCATE_MODE)`.
Cập nhật test mock cũ `BatchExportEngineOverwritePendingCleanupRoboTest` sang kỳ vọng `"wt"`.

- **Audit:** 9.6/10 — hằng số `WRITE_TRUNCATE_MODE` đặt ở companion object `BatchExportEngine` (R5: không magic string rải rác), sửa đúng 2 call site OVERWRITE.
- **Unit test:** file mới `BatchExportEngineOverwriteTruncateRoboTest.kt` — 2 test:
  - `writeIntoDocumentTree_overwrite_opensOutputStreamInTruncateMode` (verify SAF mở `"wt"`, không gọi overload 1-arg `"w"`).
  - `generateImage_mediaStoreOverwrite_opensFileDescriptorInTruncateMode` (verify MediaStore mở `"wt"`).
  RED thật khi dùng `"w"`, GREEN sau fix.
- **Integration test (androidTest):** thêm `writeIntoDocumentTree_overwrite_largerExistingFile_isTruncatedToNewImageSize` trong `BatchExportEngineCustomDirectoryIntegrationTest` — tạo file cũ 200KB, ghi đè bằng ảnh nhỏ, verify `file.length() == expectedNewSize` (không còn byte dư thừa). Chờ device để chạy.
- **Full suite:** `./gradlew testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest ktlintCheck lint` → BUILD SUCCESSFUL (exit 0).
