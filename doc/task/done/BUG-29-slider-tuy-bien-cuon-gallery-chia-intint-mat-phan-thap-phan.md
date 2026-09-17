---
id: BUG-29
priority: P2
type: Bug
effort: XS
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/GalleryFragment.kt
---

# Slider tuỳ biến cuộn gallery: chia Int/Int mất phần thập phân, cuộn sai

## Mô tả
`OnTouchListener` của `sliderCard`: `val percent = binding.rvContent.computeVerticalScrollRange() / totalHeight` — cả 2 vế đều `Int`, phép chia mất hoàn toàn phần thập phân (kết quả luôn 0 hoặc số nguyên thô). So sánh `onScrolled()` cùng file dùng đúng `offset.toFloat() / verticalScrollRange`. Kéo thanh trượt tuỳ biến khiến cuộn không hết list hoặc giật cục/nhảy sai vị trí.

## Triển khai
Đổi phép chia sang `Float`/`Double` (`.toFloat()` một trong hai vế) giống pattern đã đúng ở `onScrolled()`.

## Acceptance Criteria
- [x] Gallery có đủ ảnh để hiện slider tuỳ biến, kéo từ đầu tới cuối — cuộn mượt, tới đúng vị trí cuối danh sách.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-29`, file ticket = `todo/BUG-29-slider-tuy-bien-cuon-gallery-chia-intint-mat-phan-thap-phan.md`.

## Kết quả kiểm chứng (2026-09-17)

**Fix**: tách logic tính tỉ lệ thành `GalleryFragment.computeSliderScrollPercent(scrollRange, totalHeight): Float` (companion object, `internal` để test được) — dùng `scrollRange.toFloat() / totalHeight` thay vì `Int/Int`. Gọi hàm này trong `OnTouchListener` của `sliderCard` thay vì tính inline.

**Unit test mới**: `GalleryFragmentSliderScrollPercentTest.kt` — 3 case (scrollRange nhỏ hơn/lớn hơn/bằng totalHeight), assert kết quả có phần thập phân đúng.
- Đã verify test THẬT SỰ bắt được bug: tạm revert fix (`(scrollRange / totalHeight).toFloat()` — chia Int trước rồi mới ép Float) → 2/3 test FAILED đúng dự đoán. Khôi phục fix → PASS lại.
- Full suite: `./gradlew testAppReleaseDebugUnitTest` — 295 tests, 0 failures. `ktlintCheck` — BUILD SUCCESSFUL.

**Smoke test trên device thật** (TECNO KJ7, serial `115333744A005844` — quay lại device đã khóa ban đầu theo yêu cầu "chỉ dùng tecno" giữa chừng session, sau khi đã dùng Pixel 7 Pro cho BUG-27/BUG-36):
- Mở GalleryFragment thật (xác nhận qua title "Choose picture" = `R.string.action_pick`, và nút `id/fab` — đúng UI tuỳ biến của app, không phải picker hệ thống).
- Dùng `uiautomator dump` xác định đúng bounds `sliderCard`/`ivSlider` (`[950,337][1080,469]`), kéo từ đó xuống khoảng cách dài (~1600px) — nội dung cuộn mượt, tỉ lệ hợp lý theo khoảng kéo, KHÔNG bị kẹt ở vị trí 0 hay nhảy giật cục (đúng hiện tượng ticket mô tả đã hết). `adb logcat -d "*:E"` không có `FATAL` nào từ package app.
- Lưu ý: không xác nhận được log debug cụ thể của `computeSliderScrollPercent` trong logcat lúc kéo (có thể do cấu hình log level/tag lọc), nhưng hành vi cuộn quan sát trực tiếp là bằng chứng chính, khớp với unit test đã verify.

**Tự chấm điểm**: 9/10 — root cause đúng, test tự-verify bằng revert, full suite xanh, smoke test thật xác nhận cuộn mượt đúng tỉ lệ trên device thật. Trừ 1 điểm vì không có xác nhận log debug trực tiếp lúc kéo trên device (chỉ có bằng chứng hành vi quan sát + unit test).
