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
- [ ] Collector Flow chỉ sống trong vòng đời `View` hiện tại, không tích luỹ qua nhiều lần tái tạo view.
- [ ] Không còn cập nhật nhầm vào ViewBinding của view đã bị huỷ.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-20`, file ticket = `todo/BUG-20-edittextcontent-collect-fragment-lifecycle.md`.
