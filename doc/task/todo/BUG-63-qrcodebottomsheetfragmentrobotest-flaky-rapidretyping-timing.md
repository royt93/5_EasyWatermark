---
id: BUG-63
type: Bug
priority: P2
effort: XS
sources: quan sát lúc chạy full suite (2026-10-04, fail 1 lần; chạy riêng 3/3 và baseline sạch đều pass)
files:
  - app/src/test/java/com/mckimquyen/watermark/ui/dlg/QrCodeBottomSheetFragmentRoboTest.kt
---

# `QrCodeBottomSheetFragmentRoboTest.rapidRetyping_cancelsStaleJob_...` flaky trong full suite

## Mô tả
`rapidRetyping_cancelsStaleJob_onlyFinalContentGeneratesAfterItsOwnDelay` (dòng ~185-200) fail ngẫu nhiên khi chạy full `./gradlew testDebugUnitTest`: `previewDrawableIsSet(fragment)` trả `true` ở dòng ~195 trong khi test kỳ vọng `false` (job debounce của "ab" mới trôi 200ms < 250ms nên chưa được generate). Test dựa vào `idleFor(...)` của looper giả lập kết hợp debounce thật chạy trên dispatcher khác, nên khi full suite tải CPU cao thời gian thực trôi nhanh hơn thời gian giả lập và job vẫn kịp generate trước assertion.

Chạy riêng 3 lượt `--rerun-tasks` đều pass, baseline sạch (không có thay đổi nào khác) cũng pass → flaky do timing, không phải regression.

## Đề xuất
Inject `CoroutineDispatcher`/clock cho debounce để test điều khiển được thời gian (TestDispatcher), hoặc bỏ phụ thuộc đồng hồ thật: assert trực tiếp rằng job của "a" bị huỷ và chỉ có đúng 1 lần generate cho "ab".

## Acceptance Criteria
- [ ] Test chạy 10 lượt liên tiếp trong full suite không fail.
- [ ] Vẫn khẳng định: gõ nhanh huỷ job cũ, chỉ nội dung cuối được generate sau debounce của chính nó.
