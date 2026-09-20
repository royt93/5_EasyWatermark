---
id: FEAT-19
type: Feature
effort: S
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/export/ExportNaming.kt
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt
---

# Chính sách xử lý trùng tên file khi export lại cùng batch/naming pattern

## Mô tả
Với naming template đã có (FEAT-02), user rất dễ export lại cùng 1 batch (chỉnh nhẹ rồi export lần 2) và tạo nhiều file trùng tên hoặc bị MediaStore tự đổi tên khó phân biệt (`_1`, `_2`...) không theo ý muốn.

## Triển khai
Thêm lựa chọn khi phát hiện trùng tên: giữ cả 2 (mặc định, hành vi hiện tại qua MediaStore), ghi đè, hoặc tự thêm counter theo định dạng rõ ràng (`_v2`, `_v3`) do app kiểm soát thay vì để MediaStore tự quyết.

## Acceptance Criteria
- [x] Export cùng 1 ảnh 2 lần với cùng pattern tên — theo lựa chọn đã chọn (giữ cả 2/ghi đè/counter), không có hành vi bất ngờ ngoài ý muốn.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-19`, file ticket = `todo/FEAT-19-chinh-sach-xu-ly-trung-ten-file-khi-export-lai-cung-batchnam.md`.
