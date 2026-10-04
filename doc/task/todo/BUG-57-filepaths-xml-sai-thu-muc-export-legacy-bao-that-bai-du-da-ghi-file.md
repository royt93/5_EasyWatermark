---
id: BUG-57
type: Bug
priority: P1
effort: XS
sources: re-audit 2026-10-04 (agent export/data/utils) + verify tay
files:
  - app/src/main/res/xml/filepaths.xml
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt
---

# Filepaths xml sai thu muc export legacy bao that bai du da ghi file

## Mô tả
`filepaths.xml` khai root `Pictures/EasyWaterMark/` sau rebrand, nhưng thư mục xuất thật là `Pictures/WaterMarkCreator` (`FileUtils.outPutFolderName`).

Android 7-9 (nhánh `SDK_INT < Q`, `BatchExportEngine.kt` ~763 và ~814): file ghi xong, `FileProvider.getUriForFile` ném `IllegalArgumentException: Failed to find configured root` → ảnh bị đánh `Failure(SAVE_UNKNOWN)`, `shareUri` null (không chia sẻ/zip được), user export lại đẻ file trùng. Nhánh SKIP cũng vỡ cùng lý do.

## Đề xuất
Đổi path trong `filepaths.xml` thành `Pictures/WaterMarkCreator/` (hoặc dùng hằng đồng bộ với `outPutFolderName`).

## Acceptance Criteria
- [ ] `getUriForFile` cho file trong `Pictures/WaterMarkCreator/` không ném exception (Robolectric test với `FileProvider` thật + `filepaths.xml` thật).
- [ ] Test khoá: path trong `filepaths.xml` khớp `FileUtils.outPutFolderName` (chống rebrand lệch lần nữa).
- [ ] Smoke test nhánh legacy không thể chạy trên device API 34 — ghi rõ giới hạn, phủ bằng unit test.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-57`, file ticket = `todo/BUG-57-filepaths-xml-sai-thu-muc-export-legacy-bao-that-bai-du-da-ghi-file.md`.
