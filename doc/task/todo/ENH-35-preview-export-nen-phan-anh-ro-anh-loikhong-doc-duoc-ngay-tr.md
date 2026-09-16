---
id: ENH-35
type: Enhancement
effort: S
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/adapter/SaveImageListAdapter.kt
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt
---

# Preview export nên phản ánh rõ ảnh lỗi/không đọc được ngay trong grid (trước khi export thật)

## Mô tả
`generatePreview()` trả `null` khi decode lỗi → adapter fallback hiển thị ảnh gốc qua Glide, TRÔNG NHƯ MỌI THỨ ỔN dù preview watermark/decode thực ra đã fail — user không biết ảnh này sẽ lỗi cho tới khi export thật xong mới thấy trong danh sách kết quả.

## Triển khai
Đổi `generatePreview()` trả kiểu sealed `Success/DecodeFailure/Unsupported` thay vì `null` đơn thuần; card hiển thị icon lỗi rõ ràng ngay ở bước preview khi phát hiện `DecodeFailure`, trước khi user bấm Export.

## Acceptance Criteria
- [ ] 1 ảnh trong batch có URI không đọc được (file đã xoá/hỏng) — card preview của ảnh đó hiện icon lỗi rõ ràng NGAY khi mở dialog Export, không đợi tới lúc export xong.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-35`, file ticket = `todo/ENH-35-preview-export-nen-phan-anh-ro-anh-loikhong-doc-duoc-ngay-tr.md`.
