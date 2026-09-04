---
id: ENH-10
type: Enhancement
effort: M
sources: Agy (1/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/PickImageContract.kt
  - app/src/main/java/com/mckimquyen/watermark/utils/MultiPickContract.kt
---

# Chuyển sang Android Photo Picker thay `ACTION_PICK` legacy

## Mô tả
Chọn ảnh hiện dùng `Intent(Intent.ACTION_PICK)` kiểu cũ. Từ Android 13, `ActivityResultContracts.PickVisualMedia`/`PickMultipleVisualMedia` (Android Photo Picker) cho trải nghiệm mượt hơn và **không yêu cầu quyền `READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE`** — giảm ma sát permission cho người dùng, đặc biệt quan trọng với app cần quyền truy cập nhiều ảnh (batch watermark).

## Đề xuất
Thay `PickImageContract`/`MultiPickContract` bằng `ActivityResultContracts.PickVisualMedia`/`PickMultipleVisualMedia`, giữ fallback `ACTION_PICK` cho thiết bị/Android version không hỗ trợ Photo Picker (< Android 11 hoặc thiếu Google Play Services module).

## Acceptance Criteria
- [ ] Chọn ảnh (đơn + nhiều) dùng Android Photo Picker trên Android 13+.
- [ ] Không yêu cầu quyền `READ_MEDIA_IMAGES` trên thiết bị hỗ trợ Photo Picker.
- [ ] Có fallback hoạt động trên Android cũ hơn không hỗ trợ.
