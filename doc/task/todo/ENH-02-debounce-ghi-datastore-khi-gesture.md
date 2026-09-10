---
id: ENH-02
type: Enhancement
effort: S
sources: Codex, Agy (2/4) — mô tả gốc đã sửa sau khi verify lại lúc fix BUG-06/ENH-04 (xem "Đính chính")
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/EditTextContentFragment.kt
---

# Debounce ghi DataStore khi nhập text (KHÔNG phải khi pinch/kéo)

## Đính chính (sau khi verify lại lúc fix BUG-06/ENH-04)
Mô tả gốc của ticket này SAI ở phần pinch/kéo — đã đọc lại code xác nhận:
- **Pinch** (`WaterMarkImageView.onScale()`): chỉ mutate `config` (biến local trên View) mỗi frame để render trực tiếp, **KHÔNG** gọi ViewModel/DataStore. Việc persist (`viewModel.updateTextSize()`) chỉ xảy ra **1 lần** ở `onScaleEnd()` (khi nhả tay), không phải mỗi frame.
- **Kéo watermark** (`onTouchEvent` `ACTION_MOVE`): tương tự, chỉ gọi `onOffsetChanged()` (→ ghi DataStore) ở `ACTION_UP`/`ACTION_CANCEL` (nhả tay), không phải mỗi `ACTION_MOVE`.
- **Chỉ nhập text watermark** (`EditTextContentFragment`) thực sự có khả năng gọi update theo mỗi ký tự gõ (cần xác nhận thêm khi làm ticket).

**Lag khi pinch mà người dùng báo cáo (lúc test BUG-06/ENH-04) không phải do ghi DataStore** — đã xác định nguyên nhân thật là `buildIconBitmapShader`/`buildTextBitmapShader` cấp phát bitmap mới (`Bitmap.createScaledBitmap` + `Bitmap.createBitmap`) mỗi lần gọi, chạy mỗi frame `onScale`. Xem chi tiết & hướng fix trong `ENH-16` (ticket mới, tách riêng vì đây là vấn đề khác — throttle rebuild shader, không phải debounce ghi đĩa).

## Mô tả (còn lại, đã thu hẹp scope)
Nhập text watermark liên tục (gõ nhanh) có thể trigger update/re-render mỗi ký tự — cần xác nhận cụ thể tần suất gọi ViewModel khi vào làm ticket này, trước khi giả định cần debounce.

## Đề xuất
Nếu xác nhận đúng là ghi mỗi ký tự: debounce 100-300ms trước khi ghi DataStore, giữ preview text tức thời.

## Acceptance Criteria
- [ ] Xác nhận lại (đọc code) tần suất ghi DataStore thực tế khi nhập text watermark trước khi fix.
- [ ] Nếu cần: gõ liên tục chỉ ghi DataStore sau khi dừng gõ >100-300ms, preview vẫn tức thời.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-02`, file ticket = `todo/ENH-02-debounce-ghi-datastore-khi-gesture.md`.
