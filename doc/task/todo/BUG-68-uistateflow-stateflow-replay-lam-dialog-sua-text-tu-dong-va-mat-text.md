---
id: BUG-68
type: Bug
priority: P1
effort: S
sources: re-audit 2026-10-04 (2 agent đọc code) + verify tay hình dạng code; chưa tái hiện trên máy trừ khi ghi khác
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/TextWatermarkBSDFragment.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/EditTextContentFragment.kt
---

# Uistateflow stateflow replay lam dialog sua text tu dong va mat text

## Mô tả
`uiStateFlow` là `StateFlow` (giữ giá trị cuối `GoEdit`/`UseTemplate`); `flowWithLifecycle(STARTED)` chạy lại khi STOP→START và nhận lại giá trị cũ như event mới → `dialog?.onBackPressed()` chạy. (Đã verify đọc code; **chưa tái hiện trên máy** và chưa kiểm có chỗ nào reset về `None` sau khi xử lý — cần xác nhận trước khi sửa.)

**Kịch bản:** Mở dialog → vào Template → back/chọn template → bấm Home rồi quay lại → dialog tự đóng; với `UseTemplate` còn ghi đè `etWaterText` bằng nội dung template, text user vừa sửa mất.

## Đề xuất
Biến điều hướng thành one-shot (`SharedFlow(replay=0)`/`Channel`) hoặc consume rồi emit `None` sau khi xử lý.

## Acceptance Criteria
- [ ] Tái hiện được lỗi bằng test/smoke trước khi sửa (RED).
- [ ] Sau sửa: STOP→START không replay điều hướng cũ.
- [ ] Không regression luồng Template bình thường.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-68`, file ticket = `todo/BUG-68-uistateflow-stateflow-replay-lam-dialog-sua-text-tu-dong-va-mat-text.md`.
