---
id: FEAT-22
type: Feature
effort: S
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainActivity.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/LaunchView.kt
  - app/src/main/java/com/mckimquyen/watermark/utils/CameraCaptureHelper.kt
  - app/src/main/res/drawable/ic_camera.xml
  - app/src/main/res/menu/menu.xml
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-vi/strings.xml
  - app/src/main/res/xml/filepaths.xml
  - app/src/test/java/com/mckimquyen/watermark/utils/CameraCaptureHelperTest.kt
  - app/src/test/java/com/mckimquyen/watermark/ui/MainActivityCaptureCameraRoboTest.kt
---

# Chụp ảnh trực tiếp từ Camera rồi watermark ngay

## Mô tả
Chưa có `ACTION_IMAGE_CAPTURE`/CameraX trong source (đã grep xác nhận) — muốn watermark ảnh vừa chụp phải mở Camera app riêng, chụp xong, rồi quay lại app này chọn từ Gallery.

## Triển khai
Thêm nút "Chụp ảnh" cạnh nút chọn Gallery hiện có ở màn Launch — dùng `ACTION_IMAGE_CAPTURE` (đơn giản, không cần thêm dependency CameraX) lưu ảnh tạm rồi đẩy thẳng vào editor watermark, bỏ bước chụp-rồi-mở-lại-Gallery.

## Acceptance Criteria
- [x] Bấm nút Chụp ảnh, chụp 1 tấm — ảnh vào thẳng editor watermark ngay, không cần thao tác Gallery thêm.

## Kết quả kiểm chứng
- **Unit Tests:** `CameraCaptureHelperTest` (6/6 PASS: tạo tệp tạm đúng tiền tố/hậu tố, sinh URI FileProvider hợp lệ, dọn tệp rỗng 0 bytes, giữ tệp có nội dung, xử lý an toàn tệp null/không tồn tại, kiểm tra khả dụng camera).
- **Robolectric Integration Tests:** `MainActivityCaptureCameraRoboTest` (4/4 PASS: hiển thị action card `ivCaptureFromCamera` trên LaunchView, chụp thành công chuyển sang Editor và nạp ảnh vào ViewModel, chụp huỷ/thất bại giữ nguyên LaunchMode và dọn tệp rỗng, xử lý menu toolbar `actionCamera`).
- **Smoke test thiết bị thật:** Kiểm thử trên TECNO BG6 (`118743744X002560`): Cài đặt APK thành công, giao diện LaunchView hiển thị card Material 3 "Take a photo" / "Chụp ảnh từ máy ảnh" kèm biểu tượng camera, bấm vào kích hoạt trực tiếp ứng dụng máy ảnh hệ thống (`com.transsion.camera`) và mở viewfinder chụp ảnh thật.
- **Code style:** `./gradlew :app:ktlintCheck` PASS 100%.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-22`, file ticket = `todo/FEAT-22-chup-anh-truc-tiep-tu-camera-roi-watermark-ngay.md`.
