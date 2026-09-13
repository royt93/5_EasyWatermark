---
id: M3-02
type: Enhancement
priority: P1
effort: M
sources: Audit
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/LaunchView.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/MainActivity.kt
  - app/src/main/res/layout/item_func_panel.xml
  - app/src/main/res/menu/top_app_bar.xml
verified: true
---

# Migrate Màn hình chính (MainActivity & LaunchView) sang Material You M3

## Mô tả
`LaunchView.kt` và `MainActivity.kt` là màn hình trung tâm của ứng dụng nhưng hiện chứa đậm nét phong cách iOS Glass:
1. Nền `bg_glass_gradient` kết hợp hiệu ứng 6 vòng tròn trôi nổi (`floatingCircles` với `bg_floating_circle`).
2. Logo bao quanh bởi 3 tầng hiệu ứng phát sáng mờ (`bg_glass_shimmer`).
3. Các nút chọn ảnh (`ivSelectedPhotoTips`) và About (`ivGoAboutPage`) dùng màu kính đục `glass_surface` + viền `glass_border`, bo góc 32f thô cứng.
4. Ở chế độ Editor: thanh công cụ `toolbar` và thanh tính năng `rvPanel` dùng nền viên thuốc kính `bg_floating_pill`, biểu tượng tính năng (`item_func_panel.xml`) dùng nền kính tròn `bg_glass_button` kèm bóng đổ mờ `#80000000`.
5. Thanh Tabs (`tabLayout`) chưa tích hợp M3 TabLayout token.

Cần chuyển đổi toàn bộ `LaunchView` và màn hình Editor sang phong cách Material You M3 thanh lịch, hiện đại, tận dụng Dynamic Surface Color và M3 Component Tokens.

## Đề xuất giải pháp
1. **Launch Mode (Màn hình mở đầu):**
   - Loại bỏ toàn bộ `floatingCircles` và hiệu ứng vòng xoay ánh sáng `bg_glass_shimmer` (tiết kiệm CPU/GPU, loại bỏ giao diện lòe loẹt cũ).
   - Đặt nền layout là `?attr/colorSurface`.
   - Logo app: Sử dụng `ShapeableImageView` với góc bo chuẩn M3 Medium/Large.
   - Nút `ivSelectedPhotoTips` ("Choose Images"): Chuyển sang M3 `FilledButton` / `ElevatedButton` với nền `?attr/colorPrimary` và chữ `?attr/colorOnPrimary`, góc bo `ShapeAppearance.App.MediumComponent`.
   - Nút `ivGoAboutPage` ("Settings & About"): Chuyển sang M3 `TonalButton` với nền `?attr/colorSecondaryContainer` và chữ `?attr/colorOnSecondaryContainer`.
2. **Editor Mode (Màn hình chỉnh sửa):**
   - Nền canvas `ivPhoto`: Tương phản nhẹ nhàng trên nền `?attr/colorSurface`.
   - `toolbar`: M3 `MaterialToolbar`, tiêu đề và icon mang màu `?attr/colorOnSurface`.
   - `tabLayout`: Áp dụng style `Widget.Material3.TabLayout`, chỉ báo `tabIndicatorColor` dùng `?attr/colorPrimary`, màu chữ tab đã chọn là `?attr/colorPrimary`.
   - Thanh công cụ nổi `rvPanel`: Chuyển từ `bg_floating_pill` sang `MaterialCardView` hoặc Drawable M3 với màu nền `?attr/colorSurfaceContainerHigh`, góc bo 28dp, độ nổi nhẹ (tonal elevation 3dp) chuẩn Material 3.
   - Micro-cards `item_func_panel.xml`: Icon nằm trong ô M3 Tonal Circle hoặc pill tinh tế, chữ hiển thị rõ ràng bằng `?attr/colorOnSurface` / `?attr/colorOnSurfaceVariant`, bỏ thuộc tính text shadow cứng.
   - Dải danh sách ảnh `rvPhotoList`: Đặt trên nền `colorSurfaceContainerLow` hoặc trong suốt với padding chuẩn.

## Acceptance Criteria
- [x] Không còn bóng dáng của `floatingCircles`, `bg_glass_gradient`, `bg_floating_pill`, `bg_glass_shimmer` trong `LaunchView.kt`.
- [x] Các nút CTA trong Launch Mode sử dụng màu trích xuất động từ hệ thống (Dynamic Color) trên Android 12+.
- [x] Các thanh panel ở Editor Mode đồng bộ màu `colorSurfaceContainerHigh`, hòa hợp tự nhiên giữa Light và Dark theme.
- [x] Tab switching ("Content", "Style", "Layout") hiển thị đúng chỉ báo M3 với animation đàn hồi (elastic indicator).
- [x] Thao tác chuyển đổi mượt mà giữa Launch Mode và Editor Mode không bị giật, không lỗi layout animation.
- [x] Các unit test `FuncPanelAdapterRoboTest` và test liên quan tiếp tục pass 100%.

## Kiến trúc & Triển khai
- `LaunchView.kt`:
  - Xóa bỏ `floatingCircles` trong `init` và hàm `startFloatingAnimation()`.
  - Thay thế `ContextCompat.getColor(context, R.color.glass_surface)` bằng `context.getColorFromAttr(com.google.android.material.R.attr.colorPrimary)`.
  - Cập nhật `shapeAppearanceModel` theo chuẩn M3.
- `item_func_panel.xml`:
  - Thay `bg_glass_button` bằng M3 ripple container.
  - Xóa bỏ `shadowColor`, `shadowDx`, `shadowDy`.
- `top_app_bar.xml`:
  - Đổi `app:iconTint` sang `?attr/colorOnSurfaceVariant` hoặc `?attr/colorOnSurface`.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `M3-02`, file ticket = `todo/M3-02-migrate-main-screen-launchview-editor.md`.
