---
id: BUG-72
type: Bug
priority: P2
effort: XS
sources: re-audit 2026-10-04 (2 agent đọc code) + verify tay hình dạng code; chưa tái hiện trên máy trừ khi ghi khác
files:
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/OutputImageUtils.kt
---

# Mime image jpg khong chuan khi tao file xuat jpeg

## Mô tả
`BatchExportEngine.kt:654` và `:892` ghép `"image/${trapOutputExtension(...)}"` → JPEG ra `image/jpg` (MIME chuẩn là `image/jpeg`); `OutputImageUtils.mimeTypeFor()` cũng suy MIME từ đuôi file.

**Kịch bản:** DocumentsProvider/MediaStore nghiêm ngặt có thể từ chối hoặc phân loại sai `createFile("image/jpg", ...)`. (Chưa tái hiện trên máy — tác động thực tế có thể nhỏ vì MediaStore thường tự suy từ đuôi; ghi để chuẩn hoá.)

## Đề xuất
Ánh xạ riêng format → MIME (`JPEG→image/jpeg`, `PNG→image/png`, `WEBP→image/webp`), dùng cho cả nhánh MediaStore và SAF.

## Acceptance Criteria
- [ ] Không còn chuỗi `image/jpg` trong source.
- [ ] Unit test `mimeTypeFor` cho 3 format.
- [ ] Export JPEG vẫn thành công trên máy thật (MediaStore + SAF).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-72`, file ticket = `todo/BUG-72-mime-image-jpg-khong-chuan-khi-tao-file-xuat-jpeg.md`.
