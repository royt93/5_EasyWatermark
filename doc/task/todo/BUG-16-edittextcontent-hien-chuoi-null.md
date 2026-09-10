---
id: BUG-16
type: Bug
priority: P2
effort: XS
sources: claude -p (external CLI, re-audit 2026-09-10)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/EditTextContentFragment.kt
---

# `EditTextContentFragment` hiện chuỗi `"null"` literal khi `waterMark` chưa emit

## Mô tả
Dòng ~34: `shareViewModel.waterMark.value?.text.toString()`. Nếu `waterMark.value` còn null lúc dialog mở (LiveData chưa kịp emit lần đầu), biểu thức `null?.text` = `null` (kiểu `String?`), gọi `.toString()` trên `String?` null trả về chuỗi literal `"null"` — hiện thẳng vào ô nhập text watermark, người dùng thấy chữ "null" thay vì ô trống.

## Cách fix đề xuất
Đổi `shareViewModel.waterMark.value?.text.toString()` → `shareViewModel.waterMark.value?.text.orEmpty()`.

## Acceptance Criteria
- [ ] Mở dialog sửa text khi `waterMark` LiveData chưa có giá trị → ô nhập trống (không hiện chữ "null").
- [ ] Hành vi khi `waterMark` đã có giá trị không đổi.
- [ ] Test: Robolectric/unit cho case `waterMark.value == null` (mock ViewModel/LiveData).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-16`, file ticket = `todo/BUG-16-edittextcontent-hien-chuoi-null.md`.
