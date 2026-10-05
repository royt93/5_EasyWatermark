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
- [x] Bật/tắt switch bằng `performClick()` vẫn gọi đúng toggle.
- [x] Set programmatic từ observer KHÔNG gọi toggle (không vòng lặp).
- [x] Test Robolectric cho cả 2 fragment.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-69`, file ticket = `todo/BUG-69-switch-exif-card-frame-chi-chay-khi-ispressed-hong-voi-talkback.md`.

## Kết quả kiểm chứng

**Tái hiện thật trước khi sửa:** `SwitchAccessibilityClickRoboTest` (4 test, dùng `performClick()` kiểu TalkBack không đặt `isPressed`) → RED 3/4: `cardFrame_accessibilityClick_togglesConfig`, `exif_accessibilityClick_togglesConfig`, `exif_accessibilityClickOnSerifAndAutoPalette_updatesConfig` fail; test chống vòng lặp pass.
**Fix:** `ExifPbFragment` (cờ `isBindingSwitches` cho `swExif`/`swExifSerifCaption`/`swExifAutoPalette`) và `CardFramePbFragment` (cờ `isBindingSwitch` cho `swCardFrame`): observer bật cờ khi đồng bộ UI từ repo; listener chỉ ghi repo khi KHÔNG đang bind. Bỏ hẳn cổng `buttonView.isPressed`.

- **Audit:** 9.3/10 — cờ bọc `try/finally` nên không kẹt `true` khi có exception; listener chạy đồng bộ nên cờ chính xác; đã grep xác nhận chỉ observer set `isChecked` của 4 switch (không đường gọi nào khác bị đổi hành vi).
- **Test cũ phải sửa (không hạ chuẩn):** `CardFramePbFragmentWidgetTest.programmaticChecked_doesNotWriteRepo` khẳng định "set `isChecked` từ code không ghi repo" — chỉ đúng nhờ cổng `isPressed`, chính là nguyên nhân bug. Thay bằng `syncFromRepo_doesNotToggleRepoBack` (nạp config bật, observer đồng bộ UI mà KHÔNG ghi ngược). Bảo đảm chống vòng lặp vẫn được giữ.
- **Test:** 22 lớp, **113 test, 0 fail**; `ktlintCheck` xanh (BUILD SUCCESSFUL).
- **Chưa làm:** smoke test thật với TalkBack trên máy (Pixel `2B051FDH3006MU` mất kết nối); unit test Robolectric `performClick()` mô phỏng đúng đường accessibility action nên rủi ro thấp. Ticket đóng với điều kiện này.
