---
id: BUG-75
type: Bug
priority: P2
effort: XS
sources: quan sát lúc chạy full suite BUG-73 (2026-10-05, đỏ 1/4 lượt suite đầy đủ; pass 3/3 chạy riêng và pass lượt kế tiếp)
files:
  - app/src/test/java/com/mckimquyen/watermark/utils/ktx/ToastExtensionWidgetTest.kt
---

# `ToastExtensionWidgetTest.fragmentToast_showsSnackbarAnchoredOnFragmentView` chập chờn trong full suite

## Mô tả
Test đỏ `value of: snackbarTextIn(...) expected: fragment msg but was: null` — Snackbar chưa hiện lúc kiểm tra. Chỉ xảy ra khi chạy full `testDebugUnitTest` (1 trong 4 lượt), pass khi chạy riêng 3/3 và ở lượt full kế tiếp. Không liên quan thay đổi `BitmapCache` (test chỉ đụng Snackbar/Fragment).

## Đề xuất
Test đang assert ngay sau `show()`; Snackbar hiển thị bất đồng bộ qua Looper. Chờ có điều kiện (`idleUntil { snackbarTextIn(...) != null }` với timeout) thay vì assert tức thời.

## Acceptance Criteria
- [ ] Chạy full suite 10 lượt liên tiếp không đỏ.
- [ ] Vẫn khẳng định Snackbar neo trên view của Fragment.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-75`.
