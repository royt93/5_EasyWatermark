---
id: ENH-32
type: Enhancement
effort: M
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/adapter/SaveImageListAdapter.kt
---

# Preview export nên huỷ job và recycle bitmap khi ViewHolder bị tái sử dụng (RecyclerView recycle)

## Mô tả
`processUI()` launch coroutine render preview — có guard tag tránh SET NHẦM bitmap khi holder đã đổi sang item khác, nhưng bitmap đã tạo ra (job không bị huỷ) vẫn tiếp tục render xong rồi mới bị bỏ, không được `recycle()`. Cuộn nhanh qua nhiều ảnh trong batch lớn có thể tạo nhiều bitmap preview "mồ côi" cùng lúc trước khi GC dọn.

## Triển khai
Lưu `Job` theo holder (hoặc theo uri đang render), gọi `cancel()` ngay khi holder bind sang uri mới; nếu preview đã render xong nhưng không còn cần dùng (holder đã đổi), `recycle()` bitmap đó thay vì chỉ bỏ qua.

## Acceptance Criteria
- [ ] Cuộn nhanh qua batch ≥20 ảnh trong dialog Export — không tăng memory bất thường (kiểm tra qua `dumpsys meminfo` trước/sau cuộn).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-32`, file ticket = `todo/ENH-32-preview-export-nen-huy-job-va-recycle-bitmap-khi-viewholder.md`.
