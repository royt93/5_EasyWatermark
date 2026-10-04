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
- [x] `getUriForFile` cho file trong `Pictures/WaterMarkCreator/` không ném exception (Robolectric test với `FileProvider` thật + `filepaths.xml` thật).
- [x] Test khoá: path trong `filepaths.xml` khớp `FileUtils.outPutFolderName` (chống rebrand lệch lần nữa).
- [x] Smoke test nhánh legacy không thể chạy trên device API 34 — ghi rõ giới hạn, phủ bằng unit test.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-57`, file ticket = `todo/BUG-57-filepaths-xml-sai-thu-muc-export-legacy-bao-that-bai-du-da-ghi-file.md`.

## Kết quả kiểm chứng

**Fix:** `app/src/main/res/xml/filepaths.xml` — `external-path` `Pictures/EasyWaterMark/` → `Pictures/WaterMarkCreator/` (khớp `FileUtils.outPutFolderName`). Grep không còn chỗ nào khác dùng tên cũ.

- **Audit:** 9.2/10 — sửa 1 dòng đúng gốc, test khoá chống lệch tên thư mục lần sau. Trừ điểm: test dùng `FileUtils.outPutFolderName` nên nếu ai đổi hằng mà quên `filepaths.xml` thì test bắt được, nhưng nếu đổi cả hai cùng lúc sai thì không (chấp nhận được).
- **Unit test:** file mới `ExportFileProviderPathsRoboTest.kt` — 1 test dựng file trong `<getExternalStorageDirectory>/Pictures/WaterMarkCreator/` rồi gọi `FileProvider.getUriForFile` với `filepaths.xml` thật. RED thật (stash bỏ fix → fail `Failed to find configured root`), GREEN sau fix. Lưu ý: bản test đầu dùng `getExternalStoragePublicDirectory()` nằm NGOÀI root `external-path` của Robolectric nên fail cả khi đã fix — đã sửa dựng đúng cấu trúc `getExternalStorageDirectory()/Pictures`. Full `./gradlew testDebugUnitTest assembleDebug`: BUILD SUCCESSFUL, 1175 test, 0 failures, 1 skipped; `ktlintCheck` xanh.
- **Flaky ghi nhận (không liên quan fix):** `MainViewModelSaveImageImmutabilityRoboTest` fail 1 lần trong 1 lượt full suite (`No content provider: content://media/external_primary/images/media/1`), chạy lại riêng 5 lần (3 có fix, 2 bỏ fix) đều xanh, và 2 lượt full suite sau đều xanh. Chưa mở ticket; nếu tái diễn thì mở BUG riêng.
- **Smoke test thật** trên device đã khoá (Pixel 7 Pro, `2B051FDH3006MU`): cài `assembleDebug`, Chọn ảnh → editor → Lưu → "Xuất vào bộ sưu tập" → ảnh hiện tick thành công, xuất hiện nút "Chia sẻ"/"Xem trong thư viện"/"Chia sẻ dạng ZIP", logcat không có `FATAL EXCEPTION`/`Failed to find configured root`. Giới hạn: nhánh bug thật (`SDK_INT < Q`, Android 7-9) không chạy được trên Pixel API mới (đi nhánh MediaStore) — bằng chứng cho nhánh đó là unit test; smoke chỉ xác nhận không regression. Không có quảng cáo che UI.
