---
id: BUG-71
type: Bug
priority: P2
effort: S
sources: re-audit 2026-10-04 (2 agent đọc code) + verify tay hình dạng code; chưa tái hiện trên máy trừ khi ghi khác
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/recipient/RecipientManagementActivity.kt
---

# Dialog them sua recipient tu dong dong khi validate loi mat input

## Mô tả
`setPositiveButton` luôn tự dismiss; nhánh name/code rỗng chỉ `toast` + `return`, và mã trùng (`viewModel.save` callback `false`) toast SAU khi dialog đã đóng.

**Kịch bản:** Nhập code + notes, quên name, bấm OK → dialog biến mất, phải nhập lại từ đầu. Mã trùng cũng mất toàn bộ input.

## Đề xuất
Lấy `dialog.getButton(BUTTON_POSITIVE)` sau `show()` và `setOnClickListener` thủ công; báo lỗi bằng `TextInputLayout.error`; chỉ `dismiss()` khi `save` trả `true`.

## Acceptance Criteria
- [ ] Validate lỗi → dialog giữ nguyên, input còn.
- [ ] Mã trùng → hiện lỗi tại ô code, dialog giữ nguyên.
- [ ] Test Robolectric với touch thật (không `performClick()` trên nút disabled).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-71`, file ticket = `todo/BUG-71-dialog-them-sua-recipient-tu-dong-dong-khi-validate-loi-mat-input.md`.
