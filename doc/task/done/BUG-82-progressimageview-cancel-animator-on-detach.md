---
id: BUG-82
type: Bug
priority: P2
effort: XS
sources: R5 lifecycle re-audit 2026-10-14
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/ProgressImageView.kt
  - app/src/test/java/com/mckimquyen/watermark/ui/widget/ProgressImageViewLifecycleRoboTest.kt
---

# BUG-82: ProgressImageView không huỷ animator khi detach

**Priority:** P2 (lifecycle leak ngắn hạn)
**Effort:** XS (<2h)
**Status:** done

## Vấn đề

`ProgressImageView.startAnimator` được khởi chạy trong `start()`/`finish()` và giữ `UpdateListener` tham chiếu view (`paint`, `curX`, `postInvalidateOnAnimation()`). View không override `onDetachedFromWindow()`. Nếu item export bị recycle hoặc Activity đóng trong lúc animator đang chạy, animation tiếp tục gọi view đã detach tới khi kết thúc hoặc có lệnh `ready()`/`failed()`/lifecycle callback khác cancel.

Duration hiện dùng mặc định của `ObjectAnimator` (không unbounded); tác động dự kiến ngắn, nhưng thiếu cleanup nhất quán với các custom view khác.

## Đề xuất

Override `onDetachedFromWindow()`, gọi `startAnimator.cancel()` rồi `super.onDetachedFromWindow()` theo convention của các view trong repo.

## Kết quả RED/GREEN (2026-10-14)

- `ProgressImageViewLifecycleRoboTest.detach_cancelsRunningAnimator`: RED xác nhận `isStarted` vẫn true ngay sau detach trên code cũ; sau thêm `onDetachedFromWindow()` test pass.
- `ProgressImageViewLifecycleRoboTest` tổng 4 case pass: detach, start, failed, ready.
- `:app:ktlintCheck` pass.
- Instrumentation `ProgressImageViewLifecycleIntegrationTest` pass trên Pixel 7 Pro (`2B051FDH3006MU`), 1 test.

## Test

Robolectric widget test và instrumentation test trên Android thật: start animator khi view attached, detach view, xác nhận animator không còn started/running. Unit assertions khóa hành vi `start()`/`failed()`/`ready()` hiện hữu.

## Prompt loop

Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).

## Kết quả kiểm chứng (2026-10-14)

- **Fix**: `ProgressImageView.onDetachedFromWindow()` gọi `startAnimator.cancel()` trước `super.onDetachedFromWindow()`.
- **Unit/widget**: `ProgressImageViewLifecycleRoboTest` 4 case pass (detach/start/failed/ready). Trên code cũ `detach_cancelsRunningAnimator` fail đúng `isStarted()` còn true khi view đã detach.
- **Integration**: `ProgressImageViewLifecycleIntegrationTest` pass trên Pixel 7 Pro (`2B051FDH3006MU`); package `ui` 5/5 instrumentation pass cùng ENH-48.
- **Regression**: toàn bộ `testDebugUnitTest` 1503 test, 0 fail; `:app:ktlintCheck` pass.
- **Smoke**: cài debug lên Pixel 7 Pro, mở app → gallery → editor → panel; không có `FATAL EXCEPTION`. Luồng export-progress không chạy vì sẽ lưu ảnh watermark vào gallery; hành vi detach được xác nhận bằng instrumentation trên thiết bị thật.
- **Tự audit**: 9.2/10 — diff production 5 dòng, test đủ unit/widget/integration; trừ điểm vì smoke tay không bao phủ export progress.
