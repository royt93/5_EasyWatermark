---
id: M3-01
type: Enhancement
priority: P0
effort: M
sources: Audit
files:
  - app/src/main/res/values/themes.xml
  - app/src/main/res/values/styles.xml
  - app/src/main/res/values/colors.xml
  - app/src/main/res/values-v35/themes.xml
  - app/src/main/res/values-night-v35/themes.xml
  - app/src/main/res/values-v31/themes.xml
  - app/src/main/res/values-night-v31/themes.xml
  - app/src/main/res/values-v23/themes.xml
  - app/src/main/res/values-v29/themes.xml
  - app/src/main/java/com/mckimquyen/watermark/BaseActivity.kt
  - app/src/main/java/com/mckimquyen/watermark/utils/ktx/ContextExtension.kt
verified: true
---

# Thiết kế nền tảng Material You M3: Color System, Typography, Shape & Edge-to-Edge

## Mô tả
Hiện tại ứng dụng bị khóa cứng vào giao diện giả lập "iOS Liquid Glass v2":
1. Theme kế thừa `Theme.Material3.Dark.NoActionBar` nhưng bị ép tối toàn bộ qua `values-v29/themes.xml` (`forceDarkAllowed=false`), và `BaseActivity.kt` hardcode `insetsController.isAppearanceLightStatusBars = false`.
2. Mặc dù có module `:cmonet` gọi `DynamicColors.applyToActivitiesIfAvailable()`, màu sắc thực tế bị ghi đè hoàn toàn bởi các token tĩnh `glass_*` và hex `#FF007AFF`.
3. Trong `values-v35/themes.xml` và `values-night-v35/themes.xml`, app đang bật `android:windowOptOutEdgeToEdgeEnforcement = true` để né tránh quy định Edge-to-Edge bắt buộc của Android 15 (API 35).
4. Hệ thống Shape tokens trong `styles.xml` bị méo mó khi gán mọi thành phần (`SmallComponent`, `MediumComponent`, `LargeComponent`, `FloatActionButton`) chung một giá trị `45dp`.

Cần tái cấu trúc nền tảng giao diện app theo chuẩn Material 3 / Material You, hỗ trợ Dynamic Color (Monet) từ hình nền người dùng trên Android 12+, đầy đủ 2 chế độ Light & Dark theme, hệ thống Shape & Typography phân tầng chuẩn M3, và hỗ trợ Edge-to-Edge nguyên bản trên Android 15+.

## Đề xuất giải pháp
1. **Material 3 Theme & Color Roles:**
   - Cập nhật `values/themes.xml`: Khai báo đầy đủ token màu M3 cho cả Light theme (`Theme.Material3.DayNight.NoActionBar`) và Dark theme.
   - Định nghĩa bảng màu fallback chuẩn M3 (Primary, OnPrimary, PrimaryContainer, Secondary, Tertiary, Surface, SurfaceContainer, SurfaceContainerHigh, SurfaceVariant, Outline...).
   - Bật dynamic colors qua `DynamicColors.applyToActivitiesIfAvailable` và bảo đảm các layout sử dụng `?attr/colorSurface`, `?attr/colorPrimary`, v.v. thay vì `@color/glass_*`.
2. **Gỡ bỏ né tránh Android 15 Edge-to-Edge:**
   - Xóa bỏ `android:windowOptOutEdgeToEdgeEnforcement = true` trong `values-v35` và `values-night-v35`.
   - Trong `BaseActivity.kt`: Cập nhật `applyEdgeToEdge()` sử dụng `WindowCompat.setDecorFitsSystemWindows(window, false)`, thiết lập màu system bars trong suốt, và tự động điều chỉnh `isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars` dựa trên theme đang là Light hay Dark.
3. **Chuẩn hoá Shape & Typography Scale:**
   - Shape: Small (8dp), Medium (12dp), Large (16dp), Extra Large (28dp), Full (Pill / 50%).
   - Typography: Liên kết với hệ thống `TextAppearance.Material3.*`.
4. **Cập nhật ContextExtension.kt:**
   - Mở rộng các thuộc tính tiện ích `Context.colorSurfaceContainer`, `colorSurfaceVariant`, `colorOutline`... đọc trực tiếp từ `?attr/` theo theme hiện tại.

## Acceptance Criteria
- [x] App kế thừa `Theme.Material3.DayNight.NoActionBar`, hỗ trợ chuyển đổi mượt mà giữa Light và Dark theme theo cài đặt hệ thống.
- [x] Trên Android 12+ (API 31+), màu `colorPrimary`, `colorSecondaryContainer`, `colorSurfaceContainer` tự động trích xuất từ hình nền người dùng (Dynamic Color / Monet).
- [x] Trên thiết bị < API 31, app hiển thị bảng màu fallback chuẩn M3 hài hòa, tương phản đạt chuẩn WCAG AA (>4.5:1 cho text).
- [x] Gỡ bỏ hoàn toàn `android:windowOptOutEdgeToEdgeEnforcement`. Chạy mượt mà trên Android 15 không bị lỗi hiển thị thanh trạng thái / điều hướng.
- [x] Icon trên thanh status bar và navigation bar tự động chuyển màu đen khi nền sáng (Light mode) và màu trắng khi nền tối (Dark mode).
- [x] Không gây lỗi biên dịch cho các test Robolectric và AndroidTest hiện có.

## Kiến trúc & Triển khai
- `themes.xml`:
  - `Theme.BaseTheme` parent đổi sang `Theme.Material3.DayNight.NoActionBar`.
  - Thiết lập các attribute: `colorSurfaceContainer`, `colorSurfaceContainerHigh`, `colorSurfaceContainerLow`, `colorOutlineVariant`.
- `BaseActivity.kt`:
  - `isAppearanceLightStatusBars = !resources.configuration.isNight()`
  - `isAppearanceLightNavigationBars = !resources.configuration.isNight()`
- `styles.xml`:
  - Điều chỉnh lại `ShapeAppearance.App.SmallComponent` (8dp), `MediumComponent` (12dp), `LargeComponent` (16dp), `ExtraLargeComponent` (28dp).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `M3-01`, file ticket = `todo/M3-01-material-you-foundation-theme-color-system.md`.
