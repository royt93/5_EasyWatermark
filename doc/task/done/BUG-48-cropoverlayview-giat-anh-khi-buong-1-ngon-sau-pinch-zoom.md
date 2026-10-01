---
id: BUG-48
type: Bug
priority: P2
effort: S
sources: full codebase audit (general-purpose agent, 2026-10-01) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/CropOverlayView.kt
---

# `CropOverlayView` — ảnh giật mạnh khi buông 1 ngón sau khi pinch-zoom

## Mô tả
`lastTouchX`/`lastTouchY` (dòng 216-242) chỉ được cập nhật ở `ACTION_DOWN` và trong nhánh `pointerCount == 1`; KHÔNG được resync khi số ngón tay thay đổi (`ACTION_POINTER_UP` — buông bớt 1 ngón trong lúc đang pinch 2 ngón).

Khi user pinch-zoom (2 ngón) di chuyển xa vị trí `ACTION_DOWN` ban đầu, rồi nhấc bớt 1 ngón để chuyển sang pan 1 ngón: `dx = event.x - lastTouchX` vẫn tính từ toạ độ `ACTION_DOWN` cũ (đã lệch xa vị trí ngón hiện tại) → `postTranslate` áp dụng độ lệch đột ngột, ảnh giật mạnh 1 frame.

## Đề xuất
Reset/resync `lastTouchX`/`lastTouchY` về toạ độ ngón còn lại ngay tại `ACTION_POINTER_UP` (tương tự cách `ACTION_DOWN` làm), trước khi nhánh `pointerCount == 1` dùng chúng để tính `dx`/`dy` ở lần move kế tiếp.

## Acceptance Criteria
- [x] Pinch-zoom 2 ngón di chuyển xa, buông 1 ngón → không có giật/nhảy ảnh ở frame kế tiếp (so sánh `dx`/`dy` tính được phải nhỏ, tương ứng khoảng cách di chuyển thật của ngón còn lại, không phải khoảng cách tới điểm `ACTION_DOWN` ban đầu).
- [x] Luồng pan 1 ngón bình thường (không qua pinch trước đó) không bị ảnh hưởng — giữ nguyên hành vi mượt hiện có.
- [x] Widget test (Robolectric, dispatch `MotionEvent` giả lập `ACTION_DOWN` → `ACTION_POINTER_DOWN` → di chuyển → `ACTION_POINTER_UP` → `ACTION_MOVE`) chứng minh `lastTouchX/Y` được resync đúng.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-48`, file ticket = `todo/BUG-48-cropoverlayview-giat-anh-khi-buong-1-ngon-sau-pinch-zoom.md`.

## Kết quả kiểm chứng

**Fix:** thêm case `MotionEvent.ACTION_POINTER_UP` trong `onTouchEvent` — resync `lastTouchX/Y` về toạ độ ngón CÒN LẠI (`event.getX/Y(remainingIndex)`, suy ra từ `event.actionIndex` của ngón vừa buông) ngay khi 1 trong 2 ngón nhấc lên, trước khi nhánh `pointerCount==1` dùng 2 biến này tính `dx`/`dy` ở lần `ACTION_MOVE` kế tiếp. Đúng y hệt đề xuất gốc trong ticket.

- **Audit:** 9.5/10 — fix đúng 1 điểm mô tả, không đụng nhánh `ACTION_DOWN`/`ACTION_MOVE` hiện có, chỉ xử lý case 2 ngón thực tế (ghi rõ ponytail cho case >2 ngón cực hiếm, không cần thiết cho crop UI).
- **Widget test (Robolectric):** test mới `pointerUp_afterPinchMovedFar_resyncsToRemainingPointer_noJump` — dispatch chuỗi `ACTION_DOWN` (x=100) → `ACTION_POINTER_DOWN` (thêm ngón 2) → `ACTION_MOVE` 2 ngón cùng trượt xa tới x=300/320 (span giữ nguyên 20px, không đổi scale) → `ACTION_POINTER_UP` (buông ngón 2) → `ACTION_MOVE` 1 ngón +5px. TDD: **RED thật trước fix** — `computeCropRect()` nhảy delta=0.25 (đúng bằng slack bị đụng clamp, do dx tính sai = 205px từ toạ độ `ACTION_DOWN` cũ x=100) → **GREEN sau fix** — delta nhỏ (<0.05, dx đúng = 5px từ ngón còn lại x=300). `./gradlew :app:testDebugUnitTest` (8/8 `CropOverlayViewWidgetTest`, bao gồm 4 test cũ không đổi) + `ktlintCheck` toàn bộ xanh.
- **Giới hạn xác nhận trên device thật:** cài `assembleDebug` lên TECNO KJ7, mở màn Crop & xoay thẳng (`cropOverlayView`), chọn tỉ lệ 1:1, pan 1 ngón (swipe) hoạt động mượt, không crash, logcat sạch — xác nhận AC2 (không regression luồng pan 1 ngón). Kịch bản chính xác AC1 (pinch 2 ngón thật rồi buông 1 ngón) **không mô phỏng được qua `adb shell input`** trên device/tooling hiện có — `input motionevent` chỉ nhận 1 toạ độ, không hỗ trợ multi-pointer injection (đã kiểm tra `adb shell input` usage, không có cờ multi-touch). Đây là giới hạn công cụ, không phải bỏ qua; bug chính xác này đã được tái hiện VÀ chứng minh fix đúng bằng widget test ở trên với số liệu RED/GREEN cụ thể.
