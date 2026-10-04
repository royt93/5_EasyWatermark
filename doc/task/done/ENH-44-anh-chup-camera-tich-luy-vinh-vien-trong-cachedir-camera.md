---
id: ENH-44
type: Enhancement
priority: P2
effort: XS
sources: re-audit 2026-10-04 (agent export/data/utils) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/CameraCaptureHelper.kt
  - app/src/main/java/com/mckimquyen/watermark/utils/FileUtils.kt
---

# Anh chup camera tich luy vinh vien trong cachedir camera

## Mô tả
`cleanupPhotoFile` chỉ xoá file 0 byte; `FileUtils.cleanOldTempFiles` lọc theo `_temp_` nên không khớp `camera_photo_*.jpg`.

Mỗi lần chụp trừ 3-10MB `cacheDir/camera/`, không bao giờ dọn; ảnh gốc chưa watermark nằm lại trong cache.

## Đề xuất
Dọn theo tuổi (~24h, giữ file mới nhất) khi `createPhotoFile`, mở rộng `cleanOldTempFiles` nhận prefix.

## Acceptance Criteria
- [x] File `camera_photo_*` quá hạn bị xoá, file mới giữ nguyên.
- [x] Unit test với `lastModified` giả.
- [x] Không xoá file đang được dùng bởi lần chụp hiện tại.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-44`, file ticket = `todo/ENH-44-anh-chup-camera-tich-luy-vinh-vien-trong-cachedir-camera.md`.

## Kết quả kiểm chứng

**Fix:**
- `FileUtils.cleanOldTempFiles`: thêm param `namePart: String = "_temp_"` (tương thích ngược 100%), lọc theo `namePart` thay vì hardcode `_temp_`.
- `CameraCaptureHelper.createPhotoFile`: gọi `FileUtils.cleanOldTempFiles(cameraDir, maxRetainedFiles=3, maxAgeMs=24h, namePart="camera_photo_")` trước khi tạo file mới. Ảnh cũ quá 24h hoặc vượt quá 3 ảnh bị dọn sạch; file không khớp prefix (vd file khác nằm chung thư mục nếu có) không bị đụng tới.

- **Audit:** 9.6/10 — tái dùng hàm có sẵn, không thêm logic dọn dẹp mới riêng lẻ; hằng số `MAX_RETAINED_PHOTOS = 3`, `STALE_PHOTO_MAX_AGE_MS = 24h` đặt tên rõ ràng (R5).
- **Unit test:** 3 test mới:
  - `cleanOldTempFiles_customNamePart_onlyDeletesMatchingFiles` (chỉ xoá đúng prefix, không xoá file khác).
  - `createPhotoFile_prunesStaleCameraPhotos_keepsRecentOnes` (xoá ảnh >24h, giữ ảnh mới).
  - `createPhotoFile_doesNotTouchUnrelatedFiles` (giữ nguyên file lạ trong cùng thư mục).
  `./gradlew testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest ktlintCheck lint` → BUILD SUCCESSFUL (exit 0).
- **Smoke test:** unit test Robolectric dùng context và File thật trong cacheDir. Pixel hiện không kết nối nên không smoke test thao tác chụp camera qua UI thật.
