---
id: BUG-18
type: Bug
priority: P1
effort: S
sources: codex exec (external CLI, re-audit 2026-09-10)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/ExifPbFragment.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/base/BaseBindBSDFragment.kt
---

# `styleButtons by lazy` trong `ExifPbFragment` giữ view cũ qua tái tạo dialog

## Mô tả
`ExifPbFragment.kt:15` khai báo tập hợp 4 `MaterialButton` (style Classic/Polaroid/Film Strip/Minimal) bằng `by lazy`. `lazy` chỉ tính giá trị 1 lần rồi cache vĩnh viễn cho vòng đời `Fragment` instance — nhưng `BottomSheetDialogFragment` có thể tái tạo `View` (`onCreateView` gọi lại) nhiều lần trong cùng 1 Fragment instance (xoay màn hình, dialog bị hệ thống tái tạo). Khi đó `styleButtons` vẫn trỏ tới các `MaterialButton` của `View` CŨ đã bị gỡ khỏi cây UI — listener gắn trên chúng không còn tác dụng trên UI thật (nút mới không phản hồi đúng), đồng thời giữ tham chiếu mạnh tới view hierarchy cũ (leak nhẹ tới khi Fragment bị huỷ hẳn).

## Cách fix đề xuất
Khởi tạo lại `styleButtons` trong `onCreateView`/`onViewCreated` mỗi lần (không dùng `by lazy` cấp Fragment cho view reference), hoặc clear/null tham chiếu trong `onDestroyView()` theo đúng pattern ViewBinding đã dùng ở các Fragment khác trong `BaseBindBSDFragment`.

## Acceptance Criteria
- [ ] Dialog `ExifPbFragment` bị tái tạo view (test qua xoay màn hình hoặc force-recreate) vẫn bind đúng listener vào 4 nút style hiện tại trên UI.
- [ ] Không còn tham chiếu tới `View`/`MaterialButton` đã bị gỡ khỏi hierarchy sau `onDestroyView()`.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-18`, file ticket = `todo/BUG-18-exifpbfragment-lazy-button-leak.md`.
