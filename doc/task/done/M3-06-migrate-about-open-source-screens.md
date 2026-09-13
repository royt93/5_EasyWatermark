---
id: M3-06
type: Enhancement
priority: P2
effort: S
sources: Audit
files:
  - app/src/main/res/layout/a_about.xml
  - app/src/main/res/layout/a_open_source.xml
  - app/src/main/java/com/mckimquyen/watermark/ui/about/AboutActivity.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/about/OpenSourceActivity.kt
verified: true
---

# Migrate Màn hình About & Open Source sang Material You M3

## Mô tả
`AboutActivity` (màn hình giới thiệu, đánh giá, chia sẻ app, điều khoản bảo mật) và `OpenSourceActivity` (danh sách thư viện mã nguồn mở) hiện đang được thiết kế theo giao diện kính tối màu iOS:
1. Cả 2 màn hình đều dùng nền tím `@drawable/bg_glass_gradient`.
2. Header của `AboutActivity` chứa 2 vòng hào quang phát sáng mờ `@drawable/bg_glass_shimmer` phía sau logo.
3. Các thẻ Identity Card, Info Group Card và Dev Containers đều dùng `androidx.cardview.widget.CardView` với màu nền kính đen cứng `#B2000000` hoặc `@color/glass_surface`.
4. Nút bấm "Rate Us" (`tvRating`) mang màu xanh iOS `@color/glass_cta_bg` (#FF007AFF), các nút "More Apps" và "Share App" dùng token `glass_overlay_medium`.
5. Màn hình `OpenSourceActivity` dùng thẻ CardView cũ với viền kính mỏng và thanh Toolbar phủ `bg_glass_card`.

Cần chuyển đổi cả 2 màn hình sang phong cách Material 3 sắc nét, trang nhã, với các thẻ `MaterialCardView` mang màu `colorSurfaceContainer` và màu nhấn Dynamic Color.

## Đề xuất giải pháp
1. **AboutActivity (`a_about.xml`):**
   - Nền layout: `?attr/colorSurface`.
   - Hero header: Loại bỏ 2 view phát sáng `bg_glass_shimmer`. Logo app dùng `ShapeableImageView` với bo góc M3 tròn mềm mại.
   - Thẻ thông tin ứng dụng (Identity card): Chuyển thành `MaterialCardView` (`style="?attr/materialCardViewFilledStyle"`) với nền `?attr/colorSurfaceContainer`.
   - Các nút hành động (Rate, More Apps, Share App): Sử dụng `Widget.Material3.Button.TonalButton` hoặc M3 `AssistChip` với icon và màu nền `?attr/colorSecondaryContainer`, màu chữ `?attr/colorOnSecondaryContainer`.
   - Nhóm danh mục thông tin (Version, VIP, Privacy): Sử dụng `MaterialCardView` chuẩn với các hàng danh sách M3, icon tint `?attr/colorOnSurfaceVariant`, chữ `?attr/colorOnSurface`.
   - Các công tắc Debug: Đảm bảo sử dụng `MaterialSwitch` chuẩn M3.
   - Thẻ thông tin Nhà phát triển & Thiết kế: Đổi sang `MaterialCardView` với elevation và màu M3.
2. **OpenSourceActivity (`a_open_source.xml`):**
   - Nền layout: `?attr/colorSurface`.
   - Toolbar: `MaterialToolbar` với nền trong suốt hoặc `?attr/colorSurfaceContainer`, icon back chuẩn.
   - Các mục thư viện: Đổi sang `MaterialCardView` (`Widget.Material3.CardView.Outlined` hoặc `Elevated`) với nền `?attr/colorSurfaceContainer` và viền `?attr/colorOutlineVariant`.

## Acceptance Criteria
- [x] Gỡ bỏ hoàn toàn `bg_glass_gradient`, `bg_glass_shimmer`, `bg_glass_card`, `glass_surface`, `glass_cta_bg` khỏi About và Open Source.
- [x] Các thẻ hiển thị nền `colorSurfaceContainer` sáng rõ trong Light mode và trang nhã trong Dark mode.
- [x] Nút "Rate Us", "Share App", "More Apps" áp dụng đúng màu M3 Tonal, bấm có hiệu ứng ripple chuẩn M3.
- [x] Các liên kết mở kho ứng dụng, điều khoản bảo mật, màn hình VIP, màn hình mã nguồn mở hoạt động chính xác.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `M3-06`, file ticket = `todo/M3-06-migrate-about-open-source-screens.md`.
