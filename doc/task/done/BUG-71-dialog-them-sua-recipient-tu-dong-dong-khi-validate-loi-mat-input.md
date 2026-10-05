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
- [x] Validate lỗi → dialog giữ nguyên, input còn.
- [x] Mã trùng → hiện lỗi tại ô code, dialog giữ nguyên.
- [x] Test Robolectric với touch thật (không `performClick()` trên nút disabled).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-71`, file ticket = `todo/BUG-71-dialog-them-sua-recipient-tu-dong-dong-khi-validate-loi-mat-input.md`.

## Kết quả kiểm chứng

**Tái hiện thật:** `RecipientEditDialogValidationRoboTest` (3 test) → RED 2/3: `confirmWithEmptyName_dialogStaysOpen_andKeepsOtherInput` và `confirmWithEmptyCode_dialogStaysOpen` fail `isShowing() expected to be true`; `confirmWithValidInput_dialogDismisses` pass. (Lượt RED đầu không chạy được vì test lỗi biên dịch — `ShadowAlertDialog` trả `android.app.AlertDialog`, Material3 dùng `androidx.appcompat.app.AlertDialog`; đã đổi sang `ShadowDialog.getLatestDialog()`.)
**Fix:** `RecipientManagementActivity.showEditDialog` dùng `create()` + `setOnShowListener` gắn click thủ công cho nút Xác nhận thay vì `setPositiveButton` (luôn tự dismiss). Lỗi tên/mã rỗng và mã trùng hiện trên `TextInputLayout.error`, dialog + input được giữ; chỉ `dismiss()` khi `viewModel.save` trả `true`. Gỡ import `toast` không còn dùng.

- **Audit:** 9.3/10 — lỗi mã trùng giờ hiện tại đúng ô `tilCode` thay vì toast sau khi dialog đã đóng.
- **Test:** `RecipientEditDialogValidationRoboTest` 3/3, `RecipientViewModelSaveRoboTest` 2/2 không hồi quy, ktlint xanh (lô gộp 48 test, 0 fail).
- **Chưa làm:** test riêng cho nhánh mã trùng (cần repo thật hoặc fake) và smoke test trên máy (Pixel mất kết nối).
