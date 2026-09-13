---
id: M3-07
type: Enhancement
priority: P2
effort: S
sources: Audit
files:
  - app/src/main/res/layout/activity_vip_management.xml
  - app/src/main/res/drawable/bg_vip_card.xml
  - app/src/main/res/drawable/bg_vip_status_header_active.xml
  - app/src/main/java/com/mckimquyen/watermark/feature/vip/VipManagementActivity.kt
verified: true
---

# Migrate Màn hình Quản lý VIP (VipManagementActivity) sang Material You M3

## Mô tả
`VipManagementActivity` (màn hình quản lý VIP, kích hoạt mã quà tặng, xem quảng cáo thưởng và gói VIP) hiện vẫn dùng các asset của phong cách kính tối:
1. Nền màn hình dùng `@drawable/bg_glass_gradient`.
2. Header trạng thái VIP (`statusHeader`) dùng `@drawable/bg_vip_status_header_active` với viền kính `glass_border`.
3. Biểu tượng vương miện dùng nền kính tròn `bg_glass_button`.
4. Các thẻ đếm ngược (`cardTimer`), kích hoạt key (`cardRedeem`), nhận thưởng (`cardReward`), chi tiết gói VIP (`cardActiveVip`) đều dùng nền kính `@drawable/bg_vip_card` (chứa solid `glass_surface` và stroke `glass_border`).
5. Ô nhập key VIP dùng `boxBackgroundColor="@color/glass_overlay_light"` và `boxStrokeColor="@color/glass_border"`.
6. Các thuộc tính text color đều gán tĩnh `@color/glass_text_primary` hoặc `@color/glass_text_secondary`.

Cần chuyển đổi giao diện màn hình VIP sang chuẩn Material 3 sang trọng, dùng `MaterialCardView`, `colorSurfaceContainer`, `colorPrimaryContainer` kết hợp điểm nhấn màu vàng gold tinh tế cho VIP.

## Đề xuất giải pháp
1. **Layout & Cards:**
   - Nền màn hình: `?attr/colorSurface`.
   - Header trạng thái VIP: Chuyển sang `MaterialCardView` (`Widget.Material3.CardView.Filled`) với nền `?attr/colorPrimaryContainer` hoặc Surface Container có điểm nhấn VIP Gold tinh tế, chữ mang màu `?attr/colorOnPrimaryContainer`.
   - Các thẻ chức năng (Timer, Redeem, Rewarded, Pricing): Chuyển thành `MaterialCardView` với nền `?attr/colorSurfaceContainer` và bo góc 16dp chuẩn M3 Medium/Large.
2. **Text Fields & Buttons:**
   - Ô nhập key: Chuyển sang `Widget.Material3.TextInputLayout.OutlinedBox` chuẩn M3 với start icon tint và stroke màu `?attr/colorPrimary`.
   - Nút đổi key `btnRedeemKey`: M3 `FilledButton` kích hoạt tự động theo trạng thái input.
   - Nút xem quảng cáo nhận VIP `btnWatchRewarded`: M3 `FilledTonalButton` hoặc Gold Accent Button nổi bật với icon M3.
   - Nút thu hồi `btnRevoke`: M3 `OutlinedButton` với icon tint chuẩn `?attr/colorOnSurface`.
   - Các nút mua gói bị khóa: M3 `OutlinedButton` với alpha disabled chuẩn M3.
3. **Tiến độ & Hiệu ứng:**
   - `LinearProgressIndicator` (`progressVip`): `indicatorColor` dùng màu vàng gold VIP hoặc `?attr/colorPrimary`, `trackColor` dùng `?attr/colorSurfaceContainerHighest`.

## Acceptance Criteria
- [x] Gỡ bỏ `bg_glass_gradient`, `bg_vip_card`, `bg_glass_button`, `bg_vip_status_header_active` khỏi `activity_vip_management.xml`.
- [x] Màn hình VIP mang phong cách hiện đại, thanh lịch, đồng bộ với toàn bộ hệ thống Material You của app.
- [x] Thao tác kích hoạt VIP bằng key, xem rewarded ad và animation pháo hoa Konfetti hoạt động chính xác không bị ảnh hưởng.
- [x] Tương phản màu sắc giữa chữ và nền thẻ đạt chuẩn WCAG AA trên cả Light và Dark mode.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `M3-07`, file ticket = `todo/M3-07-migrate-vip-management-screen.md`.
