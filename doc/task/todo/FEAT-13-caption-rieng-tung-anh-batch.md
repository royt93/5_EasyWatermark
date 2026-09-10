---
id: FEAT-13
type: Feature
effort: M
sources: Codex, Internal (2/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
---

# Nhập caption/text riêng theo từng ảnh trong batch (CSV)

## Mô tả
Mở rộng cơ chế token `{seq}`/`{filename}` đã có — cho phép người dùng dán/nhập danh sách caption (mỗi dòng ứng với 1 ảnh theo đúng thứ tự), map 1-1 vào batch thay vì dùng chung 1 text watermark cho mọi ảnh.

## Triển khai
Thêm màn hình nhập multi-line/paste CSV, map theo index vào `ImageInfo` tương ứng trong `waterMarkRepo.imageInfoList`, override text watermark riêng cho từng ảnh khi generate.

## Acceptance Criteria
- [ ] Nhập được danh sách caption nhiều dòng, khớp đúng thứ tự ảnh trong batch.
- [ ] Export batch áp đúng caption riêng cho từng ảnh.
- [ ] Số dòng caption không khớp số ảnh có cảnh báo rõ ràng (không âm thầm sai lệch).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-13`, file ticket = `todo/FEAT-13-caption-rieng-tung-anh-batch.md`.
