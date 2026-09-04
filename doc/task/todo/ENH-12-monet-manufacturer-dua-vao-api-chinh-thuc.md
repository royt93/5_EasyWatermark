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
- [ ] Logic bật/tắt dynamic color ưu tiên `isDynamicColorAvailable()`.
- [ ] Deny-list (nếu giữ) có comment giải thích lý do cụ thể từng thiết bị, không phải "cứ hãng X là tắt".
