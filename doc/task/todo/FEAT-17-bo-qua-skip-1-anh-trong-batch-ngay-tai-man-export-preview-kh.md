---
id: FEAT-17
type: Feature
effort: M
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/adapter/SaveImageListAdapter.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SaveImageBSDialogFragment.kt
---

# Bỏ qua (skip) 1 ảnh trong batch ngay tại màn export preview, không cần quay lại Gallery

## Mô tả
Grid preview (FEAT-07) hiện chỉ để XEM trước, không cho tạm bỏ 1-2 ảnh khỏi batch ngay tại đó — muốn loại ảnh nào phải quay lại Gallery bỏ chọn rồi mở lại dialog Export từ đầu.

## Triển khai
Thêm toggle "active/skip" trên mỗi card preview — Worker chỉ export item đang active, `SaveImageListAdapter` giữ trạng thái để user bật lại nếu đổi ý, không mất phần cấu hình watermark đã chỉnh.

## Acceptance Criteria
- [ ] Skip 1 ảnh giữa batch 5 ảnh rồi Export — chỉ 4 ảnh active được xuất ra, ảnh bị skip không có trong kết quả.
- [ ] Bật lại ảnh vừa skip trước khi export — ảnh đó export bình thường.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-17`, file ticket = `todo/FEAT-17-bo-qua-skip-1-anh-trong-batch-ngay-tai-man-export-preview-kh.md`.
