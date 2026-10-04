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
- [x] Test chạy 10 lượt liên tiếp trong full suite không fail.
- [x] Vẫn khẳng định: gõ nhanh huỷ job cũ, chỉ nội dung cuối được generate sau debounce của chính nó.

## Kết quả kiểm chứng

**Fix:** `QrCodeBottomSheetFragmentRoboTest.rapidRetyping_cancelsStaleJob_...` — bản cũ assert "chưa có bitmap" ở mốc 200ms thời gian ẢO của Looper, nhưng `delay(250)` + `Dispatchers.Default` chạy theo đồng hồ THẬT nên khi tải CPU cao job vẫn kịp generate trước assertion. Giờ khẳng định hành vi không phụ thuộc đồng hồ: gõ "a" rồi "ab" thì bitmap cuối phải bằng QR của "ab" (`sameAs`) và KHÁC QR của "a" — chứng minh job của "a" bị huỷ, không ghi đè kết quả.

- **Audit:** 9.3/10 — giữ đúng ý đồ gốc (huỷ job cũ, chỉ nội dung cuối được sinh), bỏ phụ thuộc timing. Trừ điểm: không còn kiểm chứng riêng "chưa generate trước khi hết 250ms" (không kiểm chứng được ổn định bằng đồng hồ ảo).
- **Test:** class `QrCodeBottomSheetFragmentRoboTest` 10/10 pass; chạy lặp 3 lượt cùng `SignatureActivityApplyRoboTest` + ktlint đều xanh. Full `testDebugUnitTest assembleDebug assembleDebugAndroidTest ktlintCheck lint` → BUILD SUCCESSFUL.
- **Smoke test:** không áp dụng — chỉ sửa test, không đổi code production.

## Ghi chú phát sinh (BUG-56)
Full suite từng đỏ vì `SignatureActivityApplyRoboTest.apply_doubleTap_...` của BUG-56: `View.performClick()` BỎ QUA `isEnabled` (đã verify bằng probe: listener vẫn chạy trên nút disabled) nên tap đúp không bị chặn trong test, dù code thật chặn đúng. Đã sửa test dùng chạm thật (`dispatchTouchEvent` DOWN+UP). Chứng minh lại RED khi bỏ dòng `isEnabled = false` và GREEN khi có.
