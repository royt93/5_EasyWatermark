---
id: BUG-64
type: Bug
priority: P1
effort: M
sources: re-audit 2026-10-04 (2 agent đọc code) + verify tay hình dạng code; chưa tái hiện trên máy trừ khi ghi khác
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/QrCodeGenerator.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/QrCodeBottomSheetFragment.kt
  - app/src/main/java/com/mckimquyen/watermark/data/repo/WaterMarkRepository.kt
---

# Qr uri luu ben nhung file nam trong cachdir bi don con 3 file

## Mô tả
URI QR (`FileProvider` trỏ `cacheDir/qrcodes`) được ghi vào DataStore, MRU icon (tối đa `MAX_RECENT_ICONS = 8`) và profile Room như tài nguyên bền, nhưng `QrCodeBottomSheetFragment.kt:179` gọi `FileUtils.cleanOldTempFiles(qrcodes, maxRetainedFiles = 3)` (mặc định xoá cả file quá 24h), và Android cũng có thể tự xoá cache.

**Kịch bản:** Tạo QR A, dùng làm watermark/lưu profile; hôm sau mở lại sheet QR (hoặc tạo thêm QR B-E) → file A bị xoá, URI còn nguyên → decode lỗi, watermark logo/QR biến mất, không báo lỗi. MRU giữ 8 URI trong khi thư mục chỉ giữ 3 file.

## Đề xuất
Khi user XÁC NHẬN dùng QR, copy file sang `filesDir` (kho bền) rồi mới `updateIcon`/`updateQrDynamicConfig`; cache chỉ dùng cho preview chưa xác nhận. Dọn/migrate URI cache cũ khỏi MRU và profile.

## Acceptance Criteria
- [x] URI QR đã xác nhận không còn nằm trong `cacheDir`.
- [x] Test: tạo QR, mô phỏng dọn cache → watermark vẫn render.
- [x] Profile cũ trỏ cache: mở lên không crash, bỏ icon hỏng thay vì giữ URI chết.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-64`, file ticket = `todo/BUG-64-qr-uri-luu-ben-nhung-file-nam-trong-cachdir-bi-don-con-3-file.md`.

## Kết quả kiểm chứng (2026-10-05)
**Fix:** QR user xác nhận ghi vào `filesDir/qrcodes` (`QrCodeGenerator.saveToFiles`, tên `qr_*` không khớp `_temp_` nên `cleanOldTempFiles` không bao giờ đụng); `QrCodeBottomSheetFragment.saveBitmapPersistently` thay `saveBitmapToCache` và bỏ bước dọn cache. `filepaths.xml` thêm `files-path qr_codes_persistent`, GIỮ `cache-path qr_codes` cho URI cũ. Migration lúc khởi động (`MyApplication.migrateLegacyQrUris`, IO scope): `promoteLegacyCacheUri` copy file cache cũ sang kho bền qua `.part` + rename (mất file → `null`), memo hoá để một URI cũ chỉ copy một lần; áp cho icon hiện tại + MRU (`WaterMarkRepository.rewriteIconUris`, một `edit` nguyên tử) và `iconUri` của mọi profile (`WatermarkProfileRepository.rewriteIconUris` + DAO `getAllIconUris/updateIconUri`).

- **Audit:** 9.1/10. Audit diff phát hiện và sửa 2 lỗi trước khi smoke: (1) cùng một URI cũ ở icon + MRU + nhiều profile bị copy lặp thành nhiều file; (2) copy lỗi giữa chừng có thể để PNG dang dở mà URI bền trỏ vào. Trừ điểm: migration chạy mỗi lần mở app (ghi `ponytail:` — đổi thành cờ một-lần khi cần), file mồ côi trong `filesDir/qrcodes` không được dọn (vài KB/QR, chỉ phát sinh khi user bấm xác nhận).
- **RED thật:** `QrCodeBottomSheetFragmentRoboTest.saveBitmapToCache_confirmedQr_survivesCacheWipe` đỏ trên code cũ (xoá cache → `openInputStream` không đọc được).
- **Test thêm/sửa:** `QrCodePersistenceRoboTest` (6), `WaterMarkRepositoryRewriteIconUrisRoboTest` (5), `WatermarkProfileRepositoryRoboTest` +2 (thay fake DAO), `QrCodeBottomSheetFragmentRoboTest` (viết lại test prune ENH-29 đóng đinh hành vi cũ + test sống sót), androidTest `QrPersistenceIntegrationTest` (3, máy thật, decode Skia). Toàn bộ: unit **1297 test, 0 fail, 0 skip** (270 lớp, XML mới), androidTest **154 test, 0 fail, 1 skip** (Geocoder), ktlint xanh.
- **Smoke thật trên TECNO KJ7 `115333744A005844` (Android 14), A/B bản cũ → bản mới cài đè giữ data:**
  1. Bản cũ: tạo QR → file `cache/qrcodes/qr_temp_*.png`, DataStore lưu URI `.../qr_codes/qr_temp_*.png` (icon + MRU), watermark vẽ đúng. Xoá `cache/qrcodes` + khởi động lại → DataStore vẫn giữ URI chết (tái hiện lỗi dữ liệu).
  2. Bản mới cài đè: file cache cũ 2768 B → `files/qrcodes/qr_*.png` đúng 2768 B, DataStore icon + MRU cùng trỏ MỘT URI `qr_codes_persistent/...`, không crash.
  3. Bản mới tạo QR mới → chỉ ghi `files/qrcodes` (2779 B), không ghi cache. Xoá `cache/qrcodes` + force-stop + mở lại → 2 file bền còn nguyên, hộp "Icon gần đây" hiện đủ 2 QR, chọn QR → watermark vẽ lại đúng, `FATAL EXCEPTION` = 0.
- **Giới hạn nói thẳng:** (1) nhánh "file cache cũ mất → bỏ URI" chỉ kiểm bằng unit + quan sát DataStore bản mới xoá URI trên lượt thử đầu (bản mới cài đè khi cache đã bị xoá), chưa chụp UI lúc đó; (2) migrate profile Room chỉ có unit test fake DAO, chưa smoke trên máy (không có profile nào); (3) QR động (`qrDynamic*`) không đụng file nên không smoke riêng; (4) lượt smoke bị gián đoạn 2 lần do phiên khác (lenslauncher) chiếm foreground Tecno/S24, và 1 lần tôi gửi chuỗi tap mù trúng quảng cáo/Chrome (R4) — sau đó đã thêm guard kiểm activity trước mỗi tap; (5) chưa chạy toàn suite androidTest trên Tecno (chạy trên S24 Ultra).
