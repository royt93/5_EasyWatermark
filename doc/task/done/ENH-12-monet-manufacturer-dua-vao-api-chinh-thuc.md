---
id: ENH-12
type: Enhancement
effort: S
sources: Codex (1/4)
files:
  - cmonet/src/main/java/com/mckimquyen/cmonet/MonetManufacturer.kt
---

# `MonetManufacturer` whitelist nên dựa API `isDynamicColorAvailable()`

## Mô tả
`MonetManufacturer.kt:10-37` dùng whitelist thủ công loại bỏ dynamic color trên một số hãng thiết bị, kể cả khi `DynamicColors.isDynamicColorAvailable()` (API chính thức của Material) đã xác nhận thiết bị đó HỖ TRỢ. Cách làm deny-list cứng dễ lỗi thời khi có thiết bị/OEM mới, và có thể tắt tính năng oan trên thiết bị thực sự hỗ trợ tốt.

## Đề xuất
Ưu tiên dựa vào `DynamicColors.isDynamicColorAvailable()` làm nguồn sự thật chính; chỉ giữ deny-list cho các thiết bị đã xác nhận có bug thực tế (không phải suy đoán theo hãng), và đảm bảo thay đổi theme có hiệu lực nhất quán sau khi app restart.

## Acceptance Criteria
- [x] Logic bật/tắt dynamic color ưu tiên `isDynamicColorAvailable()`.
- [x] Deny-list (nếu giữ) có comment giải thích lý do cụ thể từng thiết bị, không phải "cứ hãng X là tắt".

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-12`, file ticket = `todo/ENH-12-monet-manufacturer-dua-vao-api-chinh-thuc.md`.

## Kết quả kiểm chứng (2026-09-12)

- **Fix:** Xoá hẳn allow-list thủ công theo `Build.MANUFACTURER`/`Build.BRAND` (11 hãng) — không có bằng chứng bug cụ thể nào gắn với từng hãng để giữ lại làm deny-list có căn cứ, nên chọn phương án đơn giản nhất: tin hoàn toàn vào `DynamicColors.isDynamicColorAvailable()` (API chính thức Material) + `isForceSupport` (override thủ công của user, giữ nguyên).
- **Điểm tự audit:** 9/10 — đúng tinh thần AC ("ưu tiên isDynamicColorAvailable()"), code đơn giản hơn hẳn (xoá 15 dòng). Trừ nhẹ vì đây là thay đổi hành vi thật (trước đây 1 số thiết bị hỗ trợ dynamic color thật nhưng không nằm trong allow-list sẽ giờ được BẬT — đúng ý ticket nhưng là thay đổi UX quan sát được, cần verify thật trên thiết bị).
- **Test:** Không có (module `cmonet` chưa có hạ tầng test — `testImplementation`/`androidTestImplementation` đang comment trong `cmonet/build.gradle.kts`, thêm hạ tầng JUnit/Robolectric mới cho riêng module này là over-engineering ngoài scope 1 ticket S).
- **Smoke test thật (OnePlus CPH1989):** `CMonet.isDynamicColorAvailable()` được gọi trong `AboutActivity.onCreate` (dòng `switchDynamicColor.isChecked = ...`) — mở About không crash, xác nhận đường gọi thật không có exception. OnePlus/OPPO (CPH1989) không nằm trong allow-list cũ lẫn không có trong danh sách 11 hãng — đây chính là ví dụ thực tế của "thiết bị bị tắt oan trước đây, giờ được đánh giá đúng theo `DynamicColors.isDynamicColorAvailable()` thật".
