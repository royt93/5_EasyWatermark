---
id: M3-09
type: Enhancement
priority: P2
effort: S
sources: Audit
files:
  - app/src/main/res/values/colors.xml
  - app/src/main/res/values/styles.xml
  - app/src/main/res/drawable/bg_glass_button.xml
  - app/src/main/res/drawable/bg_glass_button_checked.xml
  - app/src/main/res/drawable/bg_glass_card.xml
  - app/src/main/res/drawable/bg_glass_dot.xml
  - app/src/main/res/drawable/bg_glass_gradient.xml
  - app/src/main/res/drawable/bg_glass_gradient_sheet.xml
  - app/src/main/res/drawable/bg_glass_item.xml
  - app/src/main/res/drawable/bg_glass_shimmer.xml
  - app/src/main/res/drawable/bg_glassmorphism_panel.xml
  - app/src/main/res/drawable/bg_floating_circle.xml
  - app/src/main/res/drawable/bg_floating_pill.xml
  - app/src/main/res/drawable/bg_ios_switch_thumb.xml
  - app/src/main/res/drawable/bg_ios_switch_track.xml
  - app/src/main/res/drawable/bg_progress_glass.xml
  - app/src/main/res/drawable/bg_circle_logo.xml
  - app/src/main/res/drawable/bg_vip_card.xml
  - app/src/main/res/drawable/bg_vip_status_header_active.xml
  - app/src/main/res/drawable/bg_vip_status_header_free.xml
  - app/src/main/res/drawable/ic_settings_glass.xml
verified: true
---

# Dọn dẹp triệt để tài nguyên iOS Glass, Lint & Kiểm thử hồi quy toàn diện

## Mô tả
Sau khi hoàn thành migration các màn hình (từ M3-01 đến M3-08), trong source code sẽ còn lại tàn dư nợ kỹ thuật:
1. Hơn 15 tệp drawable chuyên dụng cho phong cách kính mờ iOS (`bg_glass_*`, `bg_floating_*`, `bg_ios_switch_*`) trở thành dead code.
2. Mục "iOS Liquid Glass — Design Tokens v2" trong `colors.xml` với khoảng 25 mã màu `glass_*` không còn nơi sử dụng.
3. Các style cũ trong `styles.xml` có thể chứa tham chiếu mồ côi.
4. Nguy cơ phát sinh lỗi unused resources hoặc cảnh báo lint mới.

Task này thực hiện dọn dẹp triệt để các asset lỗi thời, kiểm tra lint, style ktlint, chạy toàn bộ test suite và smoke test xác thực trên thiết bị thật.

## Đề xuất giải pháp
1. **Dọn dẹp Dead Resources:**
   - Xóa bỏ các tệp drawable kính mờ không còn tham chiếu:
     - `bg_glass_button.xml`, `bg_glass_button_checked.xml`, `bg_glass_card.xml`, `bg_glass_dot.xml`
     - `bg_glass_gradient.xml`, `bg_glass_gradient_sheet.xml`, `bg_glass_item.xml`, `bg_glass_shimmer.xml`
     - `bg_glassmorphism_panel.xml`, `bg_floating_circle.xml`, `bg_floating_pill.xml`
     - `bg_ios_switch_thumb.xml`, `bg_ios_switch_track.xml`, `bg_progress_glass.xml`
     - `bg_circle_logo.xml`, `bg_vip_card.xml`, `bg_vip_status_header_active.xml`, `bg_vip_status_header_free.xml`, `ic_settings_glass.xml`
   - Xóa toàn bộ khối màu `glass_*` trong `colors.xml`.
   - Dọn sạch các style không còn dùng trong `styles.xml`.
2. **Kiểm tra Lint & Style:**
   - Chạy `./gradlew lint` để đảm bảo không có cảnh báo nghiêm trọng hoặc resource missing.
   - Chạy `./gradlew ktlintCheck` để đảm bảo tuân thủ quy chuẩn style Kotlin của dự án.
3. **Kiểm thử hồi quy & Smoke test:**
   - Chạy toàn bộ JVM & Robolectric unit test: `./gradlew testAppReleaseDebugUnitTest`.
   - Smoke test trên thiết bị thật (Pixel 7 Pro / Samsung theo CLAUDE.md R3):
     - Mở app -> Splash -> Launch -> Chọn ảnh -> Màn hình Editor.
     - Kiểm tra đổi theme Light/Dark của hệ thống: màu sắc UI chuyển đổi mượt mà, icon/text tương phản tốt.
     - Kiểm tra Dynamic Color khi đổi wallpaper trên Android 12+.
     - Thao tác các tính năng: Đóng dấu Text/Icon, Chữ ký tay, EXIF Border, QR Code, Neo 9 ô, Xuất album.

## Acceptance Criteria
- [x] Không còn bất kỳ file drawable `bg_glass_*` hay token `glass_*` nào tồn tại trong repo.
- [x] `./gradlew lint` và `./gradlew ktlintCheck` chạy sạch sẽ.
- [x] Toàn bộ unit test (hơn 40 class JVM/Robolectric) chạy PASS 100%.
- [x] Smoke test trên thiết bị thật xác nhận giao diện chuẩn Material You (Dynamic Color, Light/Dark adaptive, M3 Shapes & Typography) hoạt động hoàn hảo, không có crash, không có rò rỉ bộ nhớ.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `M3-09`, file ticket = `todo/M3-09-cleanup-ios-glass-assets-lint-verification.md`.
