---
id: BUG-69
type: Bug
priority: P1
effort: S
sources: re-audit 2026-10-04 (2 agent đọc code) + verify tay hình dạng code; chưa tái hiện trên máy trừ khi ghi khác
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/ExifPbFragment.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/CardFramePbFragment.kt
---

# Switch exif card frame chi chay khi ispressed hong voi talkback

## Mô tả
Listener dùng `if (buttonView.isPressed)` để phân biệt người dùng với set programmatic (`ExifPbFragment.kt:74,111,117`, `CardFramePbFragment.kt:37`). Hành động accessibility gọi `performClick()` và không đặt `isPressed`.

**Kịch bản:** Bật `swExif` bằng TalkBack/Switch Access: `groupFrameStyle` hiện (vì `isVisible = isChecked` nằm ngoài guard) nhưng `toggleExifBorder()` không được gọi → UI lệch state, preview không đổi.

## Đề xuất
Dùng cờ `isBinding` quanh phần set programmatic hoặc gỡ listener khi bind; bỏ điều kiện `isPressed`.

## Acceptance Criteria
- [ ] Bật/tắt switch bằng `performClick()` vẫn gọi đúng toggle.
- [ ] Set programmatic từ observer KHÔNG gọi toggle (không vòng lặp).
- [ ] Test Robolectric cho cả 2 fragment.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-69`, file ticket = `todo/BUG-69-switch-exif-card-frame-chi-chay-khi-ispressed-hong-voi-talkback.md`.
