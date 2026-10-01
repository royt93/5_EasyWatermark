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
- [ ] Pinch-zoom 2 ngón di chuyển xa, buông 1 ngón → không có giật/nhảy ảnh ở frame kế tiếp (so sánh `dx`/`dy` tính được phải nhỏ, tương ứng khoảng cách di chuyển thật của ngón còn lại, không phải khoảng cách tới điểm `ACTION_DOWN` ban đầu).
- [ ] Luồng pan 1 ngón bình thường (không qua pinch trước đó) không bị ảnh hưởng — giữ nguyên hành vi mượt hiện có.
- [ ] Widget test (Robolectric, dispatch `MotionEvent` giả lập `ACTION_DOWN` → `ACTION_POINTER_DOWN` → di chuyển → `ACTION_POINTER_UP` → `ACTION_MOVE`) chứng minh `lastTouchX/Y` được resync đúng.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-48`, file ticket = `todo/BUG-48-cropoverlayview-giat-anh-khi-buong-1-ngon-sau-pinch-zoom.md`.
