---
id: ENH-33
type: Enhancement
effort: M
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/FileUtils.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/GalleryFragment.kt
---

# Hỗ trợ quét thư mục SAF đệ quy có giới hạn (tuỳ chọn "Include subfolders")

## Mô tả
`listImagesInTree()` (FEAT-08) chỉ lấy file ảnh TRỰC TIẾP dưới thư mục gốc theo đúng AC gốc — comment code hiện tại xác nhận cố ý không đệ quy subfolder. Nhiều user tổ chức ảnh theo album con (vd `Camera/2026/Trip/`), muốn gom hết vào 1 batch mà không phải chọn từng subfolder.

## Triển khai
Thêm switch "Include subfolders" (mặc định TẮT — giữ hành vi cũ) trong dialog chọn thư mục, khi bật thì đệ quy có giới hạn số tầng/số ảnh tối đa (tránh quét quá sâu/quá nhiều gây treo UI).

## Acceptance Criteria
- [ ] Bật switch, chọn thư mục có 2-3 tầng subfolder chứa ảnh — toàn bộ ảnh (kể cả trong subfolder) vào batch, không vượt giới hạn đã đặt.
- [ ] Tắt switch (mặc định) — hành vi giống hệt FEAT-08 cũ, không đệ quy.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-33`, file ticket = `todo/ENH-33-ho-tro-quet-thu-muc-saf-de-quy-co-gioi-han-tuy-chon-include.md`.
