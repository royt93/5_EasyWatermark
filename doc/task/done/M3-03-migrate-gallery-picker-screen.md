---
id: M3-03
type: Enhancement
priority: P1
effort: S
sources: Audit
files:
  - app/src/main/res/layout/f_gallery.xml
  - app/src/main/res/layout/item_image_gallery.xml
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/GalleryFragment.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/adapter/GalleryAdapter.kt
verified: true
---

# Migrate Màn hình chọn ảnh (Gallery Picker) sang Material You M3

## Mô tả
`GalleryFragment` (bộ chọn ảnh nội bộ của app) hiện đang mang giao diện pha trộn phong cách iOS:
1. Nền màn hình dùng `@drawable/bg_glass_gradient`.
2. Thanh AppBar được bọc kiểu viên thuốc lơ lửng bằng `@drawable/bg_floating_pill`.
3. Thanh trượt cuộn nhanh (`sliderCard`) dùng `androidx.cardview.widget.CardView` với nền đen tĩnh `#CC000000` và viền `#33FFFFFF`.
4. Viên thuốc đếm số ảnh đã chọn (`tvSelectionHint`) dùng `bg_floating_pill`.
5. Nút xác nhận `fab` (`ExtendedFloatingActionButton`) bị hardcode màu xanh iOS: `android:backgroundTint="#FF007AFF"`.
6. Ô ảnh (`item_image_gallery.xml`) dùng `background="#1A000000"` và radio icon chưa tích hợp token màu M3.

Cần chuyển đổi `GalleryFragment` thành màn hình Picker chuẩn Material Design 3 với dynamic color, toolbar chuẩn, và Extended FAB hài hòa.

## Đề xuất giải pháp
1. **Layout tổng thể `f_gallery.xml`:**
   - Nền màn hình: Đổi sang `?attr/colorSurface`.
   - Top Bar: Chuyển đổi sang `MaterialToolbar` chuẩn M3 đặt trong `AppBarLayout`, nền `?attr/colorSurfaceContainer` hoặc trong suốt khi cuộn (pinned/scrolled appearance), biểu tượng và tiêu đề màu `?attr/colorOnSurface`.
   - Thanh trượt cuộn (`sliderCard`): Đổi từ `CardView` sang `MaterialCardView` với `app:cardBackgroundColor="?attr/colorSurfaceContainerHigh"`, viền `?attr/colorOutlineVariant`.
   - Chip gợi ý số lượng chọn (`tvSelectionHint`): Chuyển thành M3 Badge hoặc M3 Assist/Suggestion Chip với nền `?attr/colorSecondaryContainer` và chữ `?attr/colorOnSecondaryContainer`.
   - Nút xác nhận `fab`:
     - Xóa bỏ `android:backgroundTint="#FF007AFF"`.
     - Áp dụng `app:backgroundTint="?attr/colorPrimary"` và `android:textColor="?attr/colorOnPrimary"`, icon tint `?attr/colorOnPrimary`.
     - Corner radius 16dp chuẩn M3 Large Shape.
2. **Item ô ảnh `item_image_gallery.xml`:**
   - Bo góc ảnh khi được chọn: Sử dụng M3 Shape token (12dp / Medium).
   - Checkmark chọn ảnh: Sử dụng icon check M3 với màu `?attr/colorPrimary` và viền tương phản.

## Acceptance Criteria
- [x] Gỡ bỏ toàn bộ `@drawable/bg_glass_gradient` và `@drawable/bg_floating_pill` khỏi `f_gallery.xml`.
- [x] Nút xác nhận FAB hiển thị màu chính của theme (Dynamic Color trên Android 12+), không còn màu xanh `#007AFF`.
- [x] Thanh Top Toolbar tuân thủ chuẩn M3, hiển thị sắc nét trong cả Light và Dark theme.
- [x] Hiển thị badge số lượng ảnh đã chọn rõ ràng, tương phản tốt.
- [x] Các test liên quan (`GalleryFragmentSelectionLabelRoboTest`, `GalleryFragmentFolderPickRoboTest`, `GalleryFragmentPhotoPickerRoboTest`, `GalleryFragmentLifecycleRoboTest`) tiếp tục pass 100%.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `M3-03`, file ticket = `todo/M3-03-migrate-gallery-picker-screen.md`.
