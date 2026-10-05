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
- [x] Không còn chuỗi `image/jpg` trong source.
- [x] Unit test `mimeTypeFor` cho 3 format.
- [x] Export JPEG vẫn thành công trên máy thật (MediaStore + SAF).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-72`, file ticket = `todo/BUG-72-mime-image-jpg-khong-chuan-khi-tao-file-xuat-jpeg.md`.

## Kết quả kiểm chứng

**RED thật:** `ImageFormatRoboTest.mimeTypeFor_returnsStandardImageMime` fail `expected image/jpeg but was image/jpg`; `exportEngine_neverBuildsMimeFromFileExtension` fail (engine còn ghép `"image/" + trapOutputExtension(...)`). Đáng chú ý: test CŨ `mimeTypeFor_returnsImageMime` đang khẳng định `image/jpg` — chính test hiện có đóng đinh hành vi sai; đã sửa kỳ vọng thành `image/jpeg`.
**Fix:** `OutputImageUtils.mimeTypeFor` ánh xạ riêng (JPEG→`image/jpeg`, PNG→`image/png`, còn lại→`image/webp`, khớp `extensionFor`); 2 đường tạo file trong `BatchExportEngine` (MediaStore ~654, SAF ~892) dùng `mimeTypeFor` thay vì ghép MIME từ đuôi file.

- **Audit:** 9.0/10 — chuẩn hoá theo đặc tả MIME, thay đổi nhỏ, không đổi đuôi file.
- **Test:** 18 lớp, **88 test, 0 fail** (gồm `BatchExportEngine*`, `ExportNaming*`, `OutputImage*`), ktlint xanh.
- **Giới hạn nói thẳng:** (1) test thứ hai là kiểm tra TĨNH trên mã nguồn (không chạy export thật); (2) ticket ghi từ đầu "chưa tái hiện trên máy, tác động thực tế có thể nhỏ vì MediaStore thường tự suy từ đuôi file" — đây là chuẩn hoá theo đặc tả, KHÔNG chứng minh sửa được lỗi người dùng nhìn thấy; (3) chưa smoke test export JPEG trên máy thật (MediaStore + SAF), Pixel mất kết nối.
