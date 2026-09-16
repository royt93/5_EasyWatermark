---
id: ENH-30
type: Enhancement
effort: XS
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/TextWatermarkBSDFragment.kt
---

# `TextWatermarkBSDFragment.et` field không bao giờ được gán, auto-focus không hoạt động

## Mô tả
`private var et: TextInputEditText? = null` không có nơi nào gán giá trị thật (đã grep xác nhận toàn file) — `et?.requestFocus()` trong `onViewCreated()` luôn no-op vì `et` luôn null. Auto-focus vào ô nhập text khi mở bottom sheet (trải nghiệm mong đợi: mở lên là gõ được ngay) không hoạt động.

## Triển khai
Gán `et = binding.<id ô nhập thật>` đúng chỗ (thường ngay sau khi có `binding` trong `onViewCreated()`), hoặc xoá field chết này và gọi `requestFocus()` trực tiếp trên view binding nếu chỉ dùng 1 lần.

## Acceptance Criteria
- [ ] Mở `TextWatermarkBSDFragment` — bàn phím tự bật, con trỏ nhấp nháy sẵn trong ô nhập text, gõ được ngay không cần chạm thêm.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-30`, file ticket = `todo/ENH-30-textwatermarkbsdfragmentet-field-khong-bao-gio-duoc-gan-auto.md`.
