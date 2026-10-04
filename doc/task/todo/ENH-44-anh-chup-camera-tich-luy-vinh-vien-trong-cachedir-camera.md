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
- [ ] File `camera_photo_*` quá hạn bị xoá, file mới giữ nguyên.
- [ ] Unit test với `lastModified` giả.
- [ ] Không xoá file đang được dùng bởi lần chụp hiện tại.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-44`, file ticket = `todo/ENH-44-anh-chup-camera-tich-luy-vinh-vien-trong-cachedir-camera.md`.
