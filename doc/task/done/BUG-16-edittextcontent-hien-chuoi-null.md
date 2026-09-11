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
- [x] Mở dialog sửa text khi `waterMark` LiveData chưa có giá trị → ô nhập trống (không hiện chữ "null").
- [x] Hành vi khi `waterMark` đã có giá trị không đổi.
- [x] Test: Robolectric/unit cho case `waterMark.value == null` (mock ViewModel/LiveData).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-16`, file ticket = `todo/BUG-16-edittextcontent-hien-chuoi-null.md`.

## Kết quả kiểm chứng (2026-09-11)

- **Fix:** Tách `EditTextContentFragment.initialText(text: String?): String = text.orEmpty()` (companion, `internal` để test truy cập trực tiếp — cùng pattern `MainViewModel.resolvePreviewText`), gọi tại `setText(initialText(shareViewModel.waterMark.value?.text))` thay vì `.toString()` trực tiếp trên chuỗi nullable.
- **Điểm tự audit:** 9.5/10 — fix đúng 1 dòng, không đổi hành vi khi có giá trị, tách hàm pure giúp test được mà không cần launch Fragment/Hilt.
- **Test:** `EditTextContentFragmentRoboTest` (mới, JUnit thuần không cần Robolectric) — `nullText_returnsEmptyString_notNullLiteral` và `nonNullText_returnsSameValue`. Toàn bộ `testAppReleaseDebugUnitTest` (28 class) PASS.
- **Smoke test thật:** device Samsung SM_A115F (R9JN61LDLFJ). Mở dialog "Edit watermark" nhiều lần (test luôn BUG-20) khi `waterMark` đã có giá trị sẵn — hiện đúng text hiện tại ("👋 DO NOT REDISTRIBUTE"), không có literal "null". Không tái hiện được case `waterMark.value == null` qua UI thật (LiveData luôn có giá trị ngay khi vào editor bình thường) — case này được cover đầy đủ bằng unit test.
