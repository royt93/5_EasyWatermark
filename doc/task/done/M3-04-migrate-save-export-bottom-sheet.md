---
id: M3-04
type: Enhancement
priority: P1
effort: M
sources: Audit
files:
  - app/src/main/res/layout/dlg_save_file.xml
  - app/src/main/res/layout/item_saving_image.xml
  - app/src/main/res/values/styles.xml
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SaveImageBSDialogFragment.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/adapter/SaveImageListAdapter.kt
verified: true
---

# Migrate Bottom Sheet xuất ảnh (Save/Export Dialog) sang Material You M3

## Mô tả
`SaveImageBSDialogFragment` ("Export to Album") là một trong những dialog quan trọng nhất của ứng dụng, nơi người dùng cấu hình định dạng (JPEG/PNG/WEBP), resize, naming pattern, copyright EXIF, chất lượng và xem trước tiến độ batch export.
Hiện tại giao diện dialog này bị bao phủ bởi kiểu thiết kế iOS Glass:
1. Nền container bọc `@drawable/bg_glass_gradient`.
2. Tay kéo (Drag Handle) vẽ bằng View trắng thô sơ thay vì `BottomSheetDragHandleView` của Material 3.
3. Các dropdown format và resize dùng `bg_glassmorphism_panel` với `boxStrokeWidth="0dp"`.
4. Ô nhập tên file và copyright cũng bọc kính mờ, text field không có viền chuẩn.
5. Thẻ thanh trượt chất lượng và lưới danh sách xuất bọc trong `androidx.cardview.widget.CardView` với nền kính đen `#B2000000` và viền kính mỏng.
6. Nút bấm xuất ảnh chính `btnSave` mang màu xanh iOS cứng: `android:backgroundTint="#FF007AFF"` và `cornerRadius="45dp"`.
7. `item_saving_image.xml` có nhãn preview dùng `background="@color/glass_surface"`.

Cần chuyển đổi `SaveImageBSDialogFragment` thành Bottom Sheet chuẩn Material Design 3, hài hòa, thẩm mỹ cao và tận dụng trọn vẹn Dynamic Color.

## Đề xuất giải pháp
1. **Container & Drag Handle:**
   - Đổi nền Bottom Sheet sang `?attr/colorSurfaceContainerLow` (chuẩn M3 cho modal bottom sheet).
   - Sử dụng `com.google.android.material.bottomsheet.BottomSheetDragHandleView` hoặc View handle M3 tự động ăn màu `?attr/colorOnSurfaceVariant` với alpha 0.4.
2. **Text Input & Dropdowns:**
   - Format & Resize: Dùng `Widget.Material3.TextInputLayout.OutlinedBox.ExposedDropdownMenu`, bỏ `bg_glassmorphism_panel`, viền nét theo `?attr/colorOutline`, nền trong suốt hoặc `?attr/colorSurfaceContainer`.
   - Name pattern & Copyright: Chuyển sang `Widget.Material3.TextInputLayout.OutlinedBox` chuẩn với `hintTextColor`, `boxStrokeColor` đồng bộ dynamic color.
3. **Cards & Sliders:**
   - Thay toàn bộ `CardView` cũ bằng `MaterialCardView` (`style="?attr/materialCardViewFilledStyle"` hoặc `OutlinedStyle`) với nền `?attr/colorSurfaceContainer`.
   - Slider chất lượng `slideQuality`: Tích hợp style `Widget.Material3.Slider` với tooltip nổi khi kéo.
4. **Action Buttons:**
   - Nút `btnSave` ("Export to Album"): Chuyển sang M3 `FilledButton` (`Widget.Material3.Button`) với nền `?attr/colorPrimary`, chữ `?attr/colorOnPrimary`, góc bo chuẩn `ShapeAppearance.App.MediumComponent` (12-16dp thay vì 45dp pill).
   - Nút `btnOpenGallery`: M3 `TextButton` với màu `?attr/colorPrimary`.
5. **Item Export Preview `item_saving_image.xml`:**
   - Nhãn thông tin kích thước `tvPreviewInfo` dùng nền `?attr/colorSurfaceContainerHigh` với chữ `?attr/colorOnSurface`.

## Acceptance Criteria
- [x] Bottom Sheet xuất ảnh hiển thị nền M3 Surface Container sạch sẽ, không còn `bg_glass_gradient` và `#B2000000`.
- [x] Nút CTA "Export to Album" hiển thị màu `colorPrimary` chuẩn theo dynamic color hệ thống.
- [x] Các trường nhập liệu và menu dropdown hiển thị viền M3 Outlined sắc nét, rõ ràng trên cả Light và Dark mode.
- [x] Giữ nguyên khả năng scroll mượt mà và không bị che khuất trên các màn hình nhỏ (bảo toàn fix BUG-22).
- [x] Toàn bộ luồng batch export, preview grid (FEAT-07), naming template (FEAT-02) và unit test liên quan hoạt động ổn định 100%.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `M3-04`, file ticket = `todo/M3-04-migrate-save-export-bottom-sheet.md`.
