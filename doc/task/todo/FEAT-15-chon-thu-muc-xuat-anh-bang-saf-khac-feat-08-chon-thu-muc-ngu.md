---
id: FEAT-15
type: Feature
effort: M
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt
  - app/src/main/java/com/mckimquyen/watermark/export/ExportNaming.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SaveImageBSDialogFragment.kt
---

# Chọn thư mục XUẤT ảnh bằng SAF (khác FEAT-08 — chọn thư mục NGUỒN ảnh vào batch)

## Mô tả
Hiện tại ảnh xuất ra luôn cố định `Pictures/WaterMarkCreator/` (MediaStore RELATIVE_PATH) — không cho user chọn thư mục đích khác (vd lưu thẳng vào thư mục dự án cụ thể, thư mục đồng bộ cloud cục bộ). FEAT-08 đã cho chọn thư mục làm NGUỒN ảnh batch — đây là chiều ngược lại: chọn thư mục ĐÍCH lưu kết quả.

## Triển khai
Thêm tuỳ chọn "Chọn thư mục lưu" trong dialog Export (SAF `OpenDocumentTree`), lưu Uri thư mục đã chọn vào `UserConfigRepository`, dùng `DocumentFile`/`ContentResolver` ghi file thay vì MediaStore RELATIVE_PATH cố định khi user đã chọn thư mục riêng.

## Acceptance Criteria
- [ ] Chọn thư mục đích khác `Pictures/WaterMarkCreator/` — file export ra đúng thư mục đã chọn, xác nhận qua trình quản lý file thật.
- [ ] Không chọn thư mục riêng (mặc định) — hành vi giữ nguyên như cũ, lưu vào `Pictures/WaterMarkCreator/`.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-15`, file ticket = `todo/FEAT-15-chon-thu-muc-xuat-anh-bang-saf-khac-feat-08-chon-thu-muc-ngu.md`.
