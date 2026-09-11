---
id: BUG-20
type: Bug
priority: P2
effort: XS
sources: codex exec (external CLI, re-audit 2026-09-10)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/EditTextContentFragment.kt
---

# `EditTextContentFragment` collect Flow theo Fragment lifecycle thay vì view lifecycle

## Mô tả
Dòng ~79: dùng `lifecycleScope` (gắn với vòng đời `Fragment` instance) thay vì `viewLifecycleOwner.lifecycleScope` (gắn với vòng đời `View` hiện tại) để collect Flow. Với `DialogFragment`/`BottomSheetDialogFragment`, `View` có thể bị tạo lại nhiều lần trong 1 Fragment instance sống — collector cũ gắn với `lifecycleScope` không tự huỷ khi view cũ mất, có thể tích luỹ nhiều collector cùng cập nhật vào `ViewBinding` mới (đã bị null hoặc trỏ view cũ) → cùng họ lỗi với [BUG-10](../done/BUG-10-galleryfragment-observe-sai-lifecycle.md) đã fix ở `GalleryFragment`, nhưng chưa được áp dụng nhất quán ở `EditTextContentFragment`.

## Cách fix đề xuất
Đổi `lifecycleScope` → `viewLifecycleOwner.lifecycleScope` cho việc collect Flow trong `EditTextContentFragment`, đúng theo pattern đã fix ở BUG-10.

## Acceptance Criteria
- [x] Collector Flow chỉ sống trong vòng đời `View` hiện tại, không tích luỹ qua nhiều lần tái tạo view.
- [x] Không còn cập nhật nhầm vào ViewBinding của view đã bị huỷ.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-20`, file ticket = `todo/BUG-20-edittextcontent-collect-fragment-lifecycle.md`.

## Kết quả kiểm chứng (2026-09-11)

- **Fix:** Đổi `lifecycleScope.launch { ... flowWithLifecycle(this@EditTextContentFragment.lifecycle, ...) }` → `viewLifecycleOwner.lifecycleScope.launch { ... flowWithLifecycle(viewLifecycleOwner.lifecycle, ...) }`, đúng pattern đã dùng để fix BUG-10 ở `GalleryFragment`.
- **Điểm tự audit:** 9.5/10 — cùng root cause, cùng fix pattern đã validate ở BUG-10, không đổi hành vi khi View còn sống.
- **Test:** `EditTextContentFragmentLifecycleRoboTest` (mới, Robolectric, theo đúng khuôn `GalleryFragmentLifecycleRoboTest` đã dùng cho BUG-10) — add Fragment thật vào `FragmentManager`, xác nhận `uiState` (MutableStateFlow nội bộ, truy cập qua reflection vì `uiStateFlow` public chỉ export `StateFlow` read-only) có ≥1 subscriber; `detach()` (huỷ View, giữ Fragment instance sống) → xác nhận subscriber về 0. Toàn bộ `testAppReleaseDebugUnitTest` (29 class sau khi thêm test này) PASS.
- **Smoke test thật:** device Samsung SM_A115F (R9JN61LDLFJ). Mở/đóng dialog "Edit watermark" nhiều lần liên tiếp (3 vòng) trong cùng phiên editor — không crash, không thấy tích luỹ hiệu ứng lạ (template áp nhầm text cũ...). Xác nhận hành vi thực tế khớp fix.
