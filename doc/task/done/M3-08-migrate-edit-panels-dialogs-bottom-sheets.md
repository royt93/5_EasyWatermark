---
id: M3-08
type: Enhancement
priority: P1
effort: M
sources: Audit
files:
  - app/src/main/res/layout/dlg_exif_border.xml
  - app/src/main/res/layout/f_qr_code_bottom_sheet.xml
  - app/src/main/res/layout/f_position_anchor_bottom_sheet.xml
  - app/src/main/res/layout/dlg_edit_text.xml
  - app/src/main/res/layout/dlg_edit_template.xml
  - app/src/main/res/layout/dlg_edit_text_template_list.xml
  - app/src/main/res/layout/item_template_list.xml
  - app/src/main/res/layout/f_tile_mode.xml
  - app/src/main/res/layout/f_text_content_display.xml
  - app/src/main/res/layout/f_text_style.xml
  - app/src/main/res/layout/f_base_pb.xml
  - app/src/main/res/layout/item_text_effect.xml
  - app/src/main/res/layout/item_typeface_style.xml
  - app/src/main/res/layout/dlg_compress_img.xml
  - app/src/main/res/layout/a_recovery.xml
verified: true
---

# Migrate toàn bộ Dialogs, Bottom Sheets và Panels chỉnh sửa sang Material You M3

## Mô tả
Hệ thống các popup, dialogs và panels chức năng con của ứng dụng đang sử dụng rất nhiều thành phần phong cách iOS Glass:
1. **Khung viền EXIF / Leica (`dlg_exif_border.xml`):** Nền `bg_glass_gradient_sheet`, 2 công tắc dùng `SwitchCompat` + `bg_ios_switch_*`, hàng nút chọn style bọc viền kính.
2. **QR Code Sheet (`f_qr_code_bottom_sheet.xml`):** Nền `bg_glassmorphism_panel`, khung xem trước dùng `bg_floating_pill`, nút apply mang màu `#007AFF`.
3. **Neo vị trí 9 ô (`f_position_anchor_bottom_sheet.xml`):** Nền kính mờ, 9 ô button dùng viền `glass_text_hint`.
4. **Soạn thảo Text Watermark & Template (`dlg_edit_text.xml`, `dlg_edit_template.xml`, `dlg_edit_text_template_list.xml`):** Ô nhập văn bản bọc hộp đen mờ `#80000000`, nút bấm xác nhận mang màu `@color/glass_cta_bg` (#007AFF) với bo góc 45dp, các hàng template dùng `bg_glass_item`.
5. **Panels trong Editor Mode (`f_tile_mode.xml`, `f_text_content_display.xml`, `f_text_style.xml`, `f_base_pb.xml`):** Dùng viên thuốc kính `bg_floating_pill`. Tab Tile Mode dùng `RadioGroup` cổ điển thay vì Segmented Button M3. Item hiệu ứng chữ (`item_text_effect.xml`) và kiểu font (`item_typeface_style.xml`) dùng `bg_glass_button`.
6. **Dialog nén ảnh (`dlg_compress_img.xml`) & Màn hình khôi phục sự cố (`a_recovery.xml`):** Toàn bộ dùng card kính mờ `#B2000000` và nút bấm hardcode màu xanh/đỏ iOS.

Cần đồng bộ toàn bộ cụm thành phần này sang chuẩn Material 3: M3 BottomSheetDialog, MaterialSwitch, M3 Segmented Button, FilterChip, và dynamic color roles.

## Đề xuất giải pháp
1. **Khung viền EXIF (`dlg_exif_border.xml`):**
   - Đổi nền sang `?attr/colorSurfaceContainerLow`.
   - Thay 2 `SwitchCompat` bằng `com.google.android.material.materialswitch.MaterialSwitch`.
   - 4 nút style khung: Chuyển sang M3 Single-select Chips hoặc `MaterialButtonToggleGroup`.
   - Slider độ dày viền: M3 `Slider`.
2. **QR Code Sheet (`f_qr_code_bottom_sheet.xml`):**
   - Nền M3 SurfaceContainerLow, ô nhập liệu M3 `TextInputLayout.OutlinedBox`.
   - Khung xem trước QR: `MaterialCardView` với nền `?attr/colorSurfaceContainerHigh`.
   - Nút `btnUseQrCode`: M3 `FilledButton` với `?attr/colorPrimary`.
3. **9-Grid Position Anchor (`f_position_anchor_bottom_sheet.xml`):**
   - 9 nút chọn ô neo: M3 `OutlinedButton` với trạng thái checked dùng `?attr/colorPrimaryContainer` và viền `?attr/colorPrimary`.
4. **Soạn văn bản & Template:**
   - Hàng chip token nhanh: M3 `SuggestionChip` / `AssistChip` ăn màu `colorSecondaryContainer`.
   - Ô nhập văn bản: M3 `TextInputLayout.OutlinedBox` với góc bo 16dp và dynamic focus stroke.
   - Nút "OK": M3 `FilledButton` với `?attr/colorPrimary`.
   - Danh sách template: Từng dòng template nằm trong `MaterialCardView` hoặc SurfaceContainer item có ripple effect chuẩn.
5. **Các Panels trong Editor Mode:**
   - Thay thế `bg_floating_pill` bằng container mang màu `?attr/colorSurfaceContainerHigh` với bo góc 28dp.
   - `f_tile_mode.xml`: Thay `RadioGroup` bằng `MaterialButtonToggleGroup` (Segmented Button M3) cho 2 chế độ "Repeat" và "Single/Decal".
   - `item_text_effect.xml`: Dùng M3 `FilterChip` cho 3 hiệu ứng Outline/Shadow/Pill với checked color `?attr/colorPrimary`.
   - `item_typeface_style.xml`: M3 Selectable Card với viền nhấn dynamic color khi được chọn.
6. **Dialog nén ảnh & Màn hình khôi phục sự cố:**
   - `dlg_compress_img.xml`: Chuyển sang M3 `MaterialCardView` với nút M3 Filled / TextButton.
   - `a_recovery.xml`: Nền `?attr/colorSurface`, thẻ thông tin lỗi dùng `MaterialCardView` (`?attr/colorErrorContainer`), nút tắt recovery mode dùng M3 Button màu `?attr/colorError`.

## Acceptance Criteria
- [x] Tất cả các Bottom Sheets con mở lên đều có nền `colorSurfaceContainerLow` đồng nhất, tay kéo M3 chuẩn.
- [x] Không còn bất kỳ instance nào của `bg_ios_switch_*`, `bg_floating_pill`, `bg_glass_item`, `#007AFF`.
- [x] Các chip token nhanh, chip hiệu ứng text và ô neo 9 vị trí phản hồi tương tác chuẩn M3.
- [x] Tính năng chọn kiểu khung EXIF, sinh mã QR, chọn anchor 9 ô, sửa template, đổi font/size/opacity hoạt động chính xác 100%.
- [x] Bộ unit test liên quan (`QrCodeBottomSheetFragmentRoboTest`, `EditTextContentFragmentDebounceRoboTest`, `ExifFrameStyleHighlighterRoboTest`, `TextEffectRendererTest`) tiếp tục pass 100%.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `M3-08`, file ticket = `todo/M3-08-migrate-edit-panels-dialogs-bottom-sheets.md`.
