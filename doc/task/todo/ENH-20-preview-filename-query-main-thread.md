---
id: ENH-20
type: Enhancement
priority: P2
effort: S
sources: codex exec (external CLI, re-audit 2026-09-10)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainActivity.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
---

# Preview `{filename}` gọi `ContentResolver.query()` đồng bộ trên Main thread

## Mô tả
`MainActivity.kt:400`/`MainViewModel.kt:534` — `resolvePreviewText()` (tính năng preview token động, xem `doc/feat.md` mục 4) cache theo uri để tránh query lặp lại mỗi ký tự gõ, nhưng LẦN ĐẦU cho mỗi ảnh vẫn gọi `ContentResolver.query()` đồng bộ ngay trên Main thread (trong observer). Với URI từ nguồn chậm (SAF thư mục mạng, cloud provider như Google Drive/OneDrive được mount qua Storage Access Framework) có thể gây khựng UI hoặc ANR khi chuyển ảnh.

## Đề xuất
Chuyển query `DISPLAY_NAME` sang coroutine (`Dispatchers.IO`) trước khi cập nhật cache, giữ nguyên hành vi cache hiện có (chỉ đổi từ đồng bộ sang bất đồng bộ).

## Acceptance Criteria
- [ ] Đổi ảnh có URI chậm (giả lập delay trong test) không block Main thread lúc lấy `{filename}` lần đầu.
- [ ] Hành vi cache theo uri (query 1 lần, không lặp lại mỗi ký tự gõ) không đổi.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-20`, file ticket = `todo/ENH-20-preview-filename-query-main-thread.md`.
