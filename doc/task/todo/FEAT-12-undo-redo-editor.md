---
id: FEAT-12
type: Feature
effort: M
sources: Internal (1/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/data/repo/WaterMarkRepository.kt
---

# Undo/Redo chỉnh sửa watermark trong editor

## Mô tả
Thêm Undo/Redo cho chỉnh sửa watermark trong editor (vị trí, size, màu, góc xoay...) — kiến trúc state hiện tại (Flow tập trung 1 điểm qua `WaterMarkRepository`) khá phù hợp để thêm nhanh cơ chế này.

## Triển khai
Lưu stack snapshot `WaterMark` mỗi lần thay đổi có ý nghĩa (không phải mỗi frame gesture — nên kết hợp debounce từ `ENH-02` để tránh stack quá dày), thêm 2 nút Undo/Redo trên toolbar editor.

## Acceptance Criteria
- [ ] Undo hoàn tác đúng thay đổi gần nhất (vị trí/size/màu/góc xoay).
- [ ] Redo khôi phục lại thay đổi vừa undo.
- [ ] Stack không phình to bất thường khi thao tác kéo/pinch liên tục (nhờ debounce).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-12`, file ticket = `todo/FEAT-12-undo-redo-editor.md`.
