---
id: ENH-10
type: Enhancement
effort: M
sources: Agy (1/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/PickImageContract.kt
  - app/src/main/java/com/mckimquyen/watermark/utils/MultiPickContract.kt
verified: true
---

# Chuyển sang Android Photo Picker thay `ACTION_PICK` legacy

## Mô tả
Chọn ảnh hiện dùng `Intent(Intent.ACTION_PICK)` kiểu cũ. Từ Android 13, `ActivityResultContracts.PickVisualMedia`/`PickMultipleVisualMedia` (Android Photo Picker) cho trải nghiệm mượt hơn và **không yêu cầu quyền `READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE`** — giảm ma sát permission cho người dùng, đặc biệt quan trọng với app cần quyền truy cập nhiều ảnh (batch watermark).

## Đề xuất
Thay `PickImageContract`/`MultiPickContract` bằng `ActivityResultContracts.PickVisualMedia`/`PickMultipleVisualMedia`, giữ fallback `ACTION_PICK` cho thiết bị/Android version không hỗ trợ Photo Picker (< Android 11 hoặc thiếu Google Play Services module).

## Acceptance Criteria
- [x] Chọn ảnh (đơn + nhiều) dùng Android Photo Picker trên Android 13+ — thêm `pickIconVisualMediaLauncher` (`ActivityResultContracts.PickVisualMedia`, đơn, cho icon watermark) và `pickImageVisualMediaLauncher` (`ActivityResultContracts.PickMultipleVisualMedia`, nhiều, cho nút "pick via system" trong `GalleryFragment`). Không đụng luồng chọn ảnh CHÍNH ("Choose Images"/`GalleryFragment` tự query MediaStore hiển thị grid trong app) — ticket chỉ nêu đúng 2 file `PickImageContract`/`MultiPickContract` (2 chỗ dùng `ACTION_PICK` bên ngoài), không phải toàn bộ luồng chọn ảnh.
- [x] Không yêu cầu quyền `READ_MEDIA_IMAGES` trên thiết bị hỗ trợ Photo Picker — call site `FuncTitleModel.FuncType.Icon` chỉ gọi `preCheckStoragePermission` khi `ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(this)` trả `false`; khi Photo Picker khả dụng, `performFileSearch(REQ_PICK_ICON)` được gọi thẳng, bỏ qua hoàn toàn bước xin quyền.
- [x] Có fallback hoạt động trên Android cũ hơn không hỗ trợ — giữ nguyên `PickImageContract`/`MultiPickContract` (ACTION_PICK) làm nhánh `else`, không xoá code cũ.

## Kết quả kiểm chứng
- Unit test (Robolectric, real device-equivalent vì SDK giả lập >= 33 nên `isPhotoPickerAvailable()` trả `true` giống thiết bị thật hiện đại): `MainActivityPhotoPickerRoboTest` verify `performFileSearch(REQ_PICK_ICON)` khởi tạo đúng Intent `action=android.provider.action.PICK_IMAGES type=image/*` KHÔNG có extra `EXTRA_PICK_IMAGES_MAX` (pick đơn). `GalleryFragmentPhotoPickerRoboTest` verify nút "pick via system" khởi tạo đúng Intent cùng action NHƯNG CÓ extra `EXTRA_PICK_IMAGES_MAX` (multi-select) — 2 test này ban đầu viết sai giả định (tưởng Robolectric không có Photo Picker nên sẽ luôn fallback `ACTION_PICK`), thực tế đo được `isPhotoPickerAvailable()=true` trong Robolectric SDK hiện tại — sửa lại test để verify đúng nhánh Photo Picker thay vì nhánh fallback.
- Nhánh fallback (`ACTION_PICK` khi Photo Picker KHÔNG khả dụng) không tái tạo được trong Robolectric (không hạ được `isPhotoPickerAvailable()` xuống `false` mà không làm sai lệch môi trường test khác) — nhưng code fallback tái dùng NGUYÊN `PickImageContract`/`MultiPickContract` gốc không sửa gì, rủi ro hồi quy thấp.
- Smoke test thật trên thiết bị (xem chi tiết BACKLOG.md phần sprint): mở "Icon" watermark mode → chọn ảnh icon, mở gallery batch picker → bấm "pick via system" — cả 2 đều mở đúng Android Photo Picker (UI hệ thống, không phải Samsung/OEM gallery ACTION_PICK cũ), không có dialog xin quyền `READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE` nào hiện ra trước khi picker mở.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-10`, file ticket = `todo/ENH-10-android-photo-picker.md`.
