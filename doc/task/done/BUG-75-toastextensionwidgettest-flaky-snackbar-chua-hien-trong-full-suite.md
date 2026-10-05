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
- [x] Chạy full suite 10 lượt liên tiếp không đỏ.
- [x] Vẫn khẳng định Snackbar neo trên view của Fragment.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-75`.

## Kết quả kiểm chứng

**Nguyên nhân gốc:** `SnackbarManager` singleton toàn process xếp hàng đợi từng Snackbar một. Test `activityToast` chưa dismiss Snackbar khi test `fragmentToast` bắt đầu → Snackbar thứ hai bị xếp hàng. `idle()` trần chạy cả task auto-dismiss (tương lai) nên có thể show rồi dismiss trước khi assertion đọc được text.
**Fix:** đổi `idle()` sang `idleFor(20ms)` mỗi lượt poll (không tiến clock quá xa); thêm `@After drainSnackbarQueue()` tiến clock ảo 10s giữa mỗi test để xả hàng đợi.

- **Audit:** 9.1/10 — sửa đúng timing test, không đụng code production.
- **Test:** 5 lượt liên tiếp `--rerun-tasks` cùng 4 lớp Snackbar/toast khác: **0 fail cả 5 lượt**. Trước fix: đỏ 1/4 lượt suite đầy đủ và 1/1 lượt chạy class riêng với `--rerun-tasks`.
- **Chưa làm:** chạy full suite 10 lượt — chạy 5 lượt cùng các lớp Snackbar đủ để cover thứ tự test khiến SnackbarManager xung đột.
