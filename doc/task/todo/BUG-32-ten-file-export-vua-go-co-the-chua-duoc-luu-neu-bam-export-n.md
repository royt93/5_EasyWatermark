---
id: BUG-32
priority: P2
type: Bug
effort: XS
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SaveImageBSDialogFragment.kt
---

# Tên file export vừa gõ có thể chưa được lưu nếu bấm Export ngay khi ô tên còn đang focus

## Mô tả
`etOutputName` chỉ gọi `saveOutputNamePattern()` khi ô input MẤT FOCUS (`onFocusChangeListener`). Nút Export chỉ lưu `copyright` rồi gọi thẳng `saveImage()` — nếu user gõ xong pattern tên file mới rồi bấm Export NGAY (không tap ra ngoài để blur trước), `outputNamePattern` cũ (chưa cập nhật) trong `UserConfigRepository` được Worker dùng, không phải giá trị vừa gõ.

## Triển khai
Gọi `saveOutputNamePattern()` (đọc trực tiếp từ `etOutputName.text`) trong handler nút Export, trước khi gọi `saveImage()` — không phụ thuộc sự kiện blur.

## Acceptance Criteria
- [ ] Gõ pattern tên file mới, bấm Export NGAY không tap ra ngoài trước — file xuất ra dùng đúng pattern vừa gõ.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-32`, file ticket = `todo/BUG-32-ten-file-export-vua-go-co-the-chua-duoc-luu-neu-bam-export-n.md`.
