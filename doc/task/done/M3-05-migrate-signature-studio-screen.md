---
id: M3-05
type: Enhancement
priority: P1
effort: S
sources: Audit
files:
  - app/src/main/res/layout/activity_signature.xml
  - app/src/main/res/layout/f_signature_bottom_sheet.xml
  - app/src/main/java/com/mckimquyen/watermark/ui/SignatureActivity.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SignatureBottomSheetFragment.kt
verified: true
---

# Migrate Màn hình Chữ ký (Signature Studio) sang Material You M3

## Mô tả
Tính năng chữ ký tay (Signature Studio) gồm 2 màn hình: `SignatureActivity` (màn hình vẽ chữ ký toàn trang) và `SignatureBottomSheetFragment` (dialog vẽ nhanh). Cả 2 hiện tại chứa rất nhiều thành phần giả lập iOS:
1. `activity_signature.xml` dùng nền tím `@drawable/bg_glass_gradient`.
2. Thanh tiêu đề phía trên (`llTopBar`) tự dựng bằng `LinearLayout` với `paddingTop="48dp"` cứng, không thích ứng chuẩn với `WindowInsets` của từng thiết bị.
3. Khung vẽ chữ ký (`flCanvasContainer`) dùng màu xám đen thô `#242424`.
4. Bảng điều khiển dưới (`llBottomControls`) dùng `@drawable/bg_glassmorphism_panel`.
5. Công tắc bật đèn neon (`swGlow`) sử dụng `SwitchCompat` với drawable giả lập công tắc iOS: `bg_ios_switch_thumb.xml` và `bg_ios_switch_track.xml`.
6. Thanh kích thước bút vẽ (`sbSize`) dùng `SeekBar` Android cũ thay vì `Slider` của Material 3.
7. Nút áp dụng chữ ký `btnApply` mang màu xanh iOS `#FF007AFF` và bo góc `45dp`.
8. `f_signature_bottom_sheet.xml` cũng dùng các drawable kính mờ và nút `#007AFF`.

Cần chuyển đổi cả hai màn hình sang phong cách Material 3 với `MaterialToolbar`, `MaterialSwitch`, `Slider` và màu sắc đồng bộ Dynamic Color.

## Đề xuất giải pháp
1. **SignatureActivity (`activity_signature.xml`):**
   - Nền layout: Đổi sang `?attr/colorSurface`.
   - Top Bar: Thay `llTopBar` bằng `MaterialToolbar` chuẩn M3 (navigation icon là back, title căn giữa hoặc start, menu action cho Undo và Clear), tích hợp `WindowInsets` tự động từ `BaseActivity`.
   - Khung vẽ canvas (`flCanvasContainer`): Bọc bằng `MaterialCardView` với nền `?attr/colorSurfaceContainer` hoặc `?attr/colorSurfaceContainerHigh`, góc bo 16dp.
   - Bảng điều khiển chân trang: Nền `?attr/colorSurfaceContainer`, phân cách tinh tế bằng viền `?attr/colorOutlineVariant` hoặc elevation.
   - Switch Neon Glow: Thay `SwitchCompat` bằng `com.google.android.material.materialswitch.MaterialSwitch` (`Widget.Material3.CompoundButton.MaterialSwitch`), loại bỏ hoàn toàn `bg_ios_switch_*`.
   - Size control: Thay `SeekBar` bằng M3 `Slider` (`style="@style/Widget.Material3.Slider"`).
   - Nút `btnApply`: Chuyển sang M3 `FilledButton` với nền `?attr/colorPrimary` và chữ `?attr/colorOnPrimary`.
2. **SignatureBottomSheetFragment (`f_signature_bottom_sheet.xml`):**
   - Nền BottomSheet: `?attr/colorSurfaceContainerLow`.
   - Tay nắm kéo M3, nút `btnSaveSignature` dùng `?attr/colorPrimary`, nút `btnClear` dùng M3 `TextButton` với `?attr/colorError`.

## Acceptance Criteria
- [x] Gỡ bỏ hoàn toàn `bg_glass_gradient`, `bg_glassmorphism_panel`, `bg_ios_switch_thumb`, `bg_ios_switch_track` khỏi cụm tính năng Chữ ký.
- [x] Công tắc Neon Glow hiển thị chuẩn Material 3 Switch với dynamic color tint khi bật/tắt.
- [x] Nút áp dụng chữ ký mang màu `colorPrimary` chuẩn theo theme hệ thống, không còn màu xanh iOS `#007AFF`.
- [x] Thanh AppBar hiển thị đẹp mắt, không bị lệch lề trên các dòng máy có notch hay camera nốt ruồi.
- [x] Nét vẽ chữ ký, tính năng undo/clear, lưu bitmap WebP và truyền sang luồng đóng dấu watermark hoạt động trơn tru 100%.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `M3-05`, file ticket = `todo/M3-05-migrate-signature-studio-screen.md`.
