---
id: BUG-19
type: Bug
priority: P1
effort: M
sources: codex exec (external CLI, re-audit 2026-09-10)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
---

# Nhánh ghi MediaStore không guard đủ khi `openFileDescriptor`/`compress` thất bại

## Mô tả
`MainViewModel.kt` quanh dòng 425-431 (nhánh ghi ảnh qua MediaStore, Android Q+): 3 vấn đề cùng chỗ, cùng họ với [BUG-03](../done/BUG-03-batch-export-bao-thanh-cong-gia.md) (đã fix ở luồng khác) nhưng ở nhánh MediaStore riêng vẫn chưa được guard:
1. `contentResolver.openFileDescriptor(uri, "w")` có thể trả `null` — không được kiểm tra trước khi dùng.
2. `Bitmap.compress()` trả về `Boolean` báo thành công/thất bại — giá trị này bị bỏ qua, không kiểm tra.
3. Khi ghi thất bại (compress false hoặc exception), row `MediaStore` đã insert với `IS_PENDING=1` không được xoá/cleanup — để lại file rác 0-byte hoặc file lỗi hiển thị trong gallery hệ thống, đồng thời job vẫn có thể báo "thành công" giả (tái phát một phần vấn đề BUG-03 ở nhánh khác).

## Cách fix đề xuất
- Guard `openFileDescriptor` null → coi là lỗi, không tiếp tục ghi.
- Kiểm tra kết quả `Bitmap.compress()`, nếu `false` → coi là lỗi.
- Khi phát hiện lỗi ở bất kỳ bước nào: xoá row MediaStore vừa insert (`contentResolver.delete(uri, null, null)`) thay vì để lại row `IS_PENDING` treo, và propagate lỗi để job tổng batch báo đúng số ảnh thất bại (đã có cơ chế đếm từ ENH-13).

## Acceptance Criteria
- [ ] `openFileDescriptor` null → không crash, ảnh này được tính là thất bại trong batch.
- [ ] `Bitmap.compress()` trả `false` → ảnh này được tính là thất bại, không báo thành công giả.
- [ ] Ghi thất bại ở bước nào cũng không để lại row/file rác trong MediaStore.
- [ ] Test: mock `ContentResolver` trả `null`/`compress` trả `false`, xác nhận job đếm đúng thất bại và không còn row rác (integration test nếu khả thi).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-19`, file ticket = `todo/BUG-19-mediastore-ghi-that-bai-khong-guard.md`.
