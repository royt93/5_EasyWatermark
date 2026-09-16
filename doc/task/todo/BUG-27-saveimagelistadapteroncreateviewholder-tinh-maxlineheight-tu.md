---
id: BUG-27
priority: P2
type: Bug
effort: S
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/adapter/SaveImageListAdapter.kt
---

# `SaveImageListAdapter.onCreateViewHolder` tính `maxLineHeight` từ `parent.height` lúc RecyclerView chưa layout xong

## Mô tả
BottomSheetDialog vừa show, `parent.height` có thể = 0 khi ViewHolder ĐẦU TIÊN được tạo (RecyclerView chưa đo/layout xong) — `maxLineHeight = 0` bị gán cố định cho `holder.ivIcon.height`, khiến vài thumbnail đầu tiên trong danh sách export co về 0dp (không thấy ảnh).

## Triển khai
Không tính height cố định lúc `onCreateViewHolder` dựa vào `parent.height` — dùng `ViewTreeObserver.OnGlobalLayoutListener` đo lại sau khi layout xong, hoặc dùng `wrap_content`/tỉ lệ cố định (aspect ratio) thay vì phụ thuộc `parent.height` tại thời điểm tạo ViewHolder.

## Acceptance Criteria
- [ ] Mở dialog Export với batch nhiều ảnh (>5) — mọi thumbnail đầu danh sách hiển thị đúng kích thước, không có ảnh nào co về 0dp.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-27`, file ticket = `todo/BUG-27-saveimagelistadapteroncreateviewholder-tinh-maxlineheight-tu.md`.
