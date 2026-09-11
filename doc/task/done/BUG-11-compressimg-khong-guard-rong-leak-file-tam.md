---
id: BUG-11
type: Bug
priority: P2
effort: XS
sources: Codex, Agy (2/4, verify trực tiếp xác nhận đúng dòng)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
verified: true
---

# `compressImg` không guard danh sách rỗng + leak file tạm

## Mô tả
`compressImg()` (dòng 726-769), đã đọc trực tiếp xác nhận:
```kotlin
appContext.contentResolver.openInputStream(waterMarkRepo.imageInfoList.first().uri)
```
`imageInfoList.first()` ném `NoSuchElementException` nếu danh sách rỗng (crash) khi thao tác nén trùng lúc danh sách ảnh vừa bị xoá hết. Ngoài ra, `File.createTempFile` tạo file tạm không nằm trong khối `try-finally` — nếu nén thất bại giữa chừng hoặc coroutine bị huỷ (user thoát màn hình), file tạm trong cache không được dọn, tích luỹ dung lượng theo thời gian.

## Cách fix đề xuất
- Guard: `imageInfoList.firstOrNull() ?: return@... ` (early return/emit lỗi phù hợp).
- Bọc phần tạo/dùng file tạm trong `try { ... } finally { tempFile.delete() }`.

## Acceptance Criteria
- [x] Gọi `compressImg` khi danh sách ảnh rỗng không crash.
- [x] Huỷ coroutine giữa lúc nén (thoát màn hình) không để lại file tạm trong cache.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-11`, file ticket = `todo/BUG-11-compressimg-khong-guard-rong-leak-file-tam.md`.

## Kết quả kiểm chứng (2026-09-11)

- **Fix:** Guard `waterMarkRepo.imageInfoList.firstOrNull()` + `waterMark.value == null` → `return@launch` với `Result.failure(TYPE_COMPRESS_ERROR)` thay vì `imageInfoList.first()` (crash khi rỗng). Toàn bộ khối đọc/copy/compress bọc trong `try { ... } finally { tmpFile.delete() }` — đảm bảo dọn file tạm kể cả khi coroutine bị huỷ giữa chừng hoặc `Compressor.compress` ném exception.
- **Điểm tự audit:** 9.5/10 — đúng root cause, không đổi hành vi nhánh thành công, không leak thêm resource.
- **Test (unit):** `MainViewModelCompressImgRoboTest` (Robolectric) — `compressImg_emptyImageList_doesNotCrash_andPostsFailure`: gọi `compressImg` khi `imageInfoList` rỗng, xác nhận không crash và `compressedResult` nhận đúng `TYPE_COMPRESS_ERROR`.
- **Test (integration, `app/src/androidTest`, IO thật):** `MainViewModelCompressImgIntegrationTest` (mới, 3 case, chạy trên Samsung SM_A115F thật qua `ANDROID_SERIAL=R9JN61LDLFJ ./gradlew connectedAppReleaseDebugAndroidTest`):
  - `compressImg_realImage_deletesTempInputFile_afterSuccess` — nén ảnh JPEG thật (`Compressor` thật, không mock) → `compressedResult` = `TYPE_COMPRESS_OK` và **không còn file `easy_water_mark_*` nào trong `cacheDir`**.
  - `compressImg_cancelCalledRightAfterLaunch_neverLeavesTempFile` — gọi `cancelCompressJob()` ngay sau `compressImg()` → vẫn không để lại file tạm. Ghi chú quan trọng: thân coroutine hoàn toàn là I/O đồng bộ (không có suspension point nội bộ), nên `cancel()` không ngắt ngang được thao tác đang chạy (cooperative cancellation chỉ có tác dụng tại điểm suspend) — coroutine chạy hết và `finally` dọn file như đường thành công. Test xác nhận đúng hành vi thực tế này (không hứa "ngắt tức thời", chỉ đảm bảo "không rác").
  - `compressImg_emptyImageList_doesNotCrash_onRealDevice` — lặp lại case rỗng nhưng trên thiết bị thật thay vì Robolectric, cùng kết quả `TYPE_COMPRESS_ERROR` + không file rác.
  - Kết quả: **24/24 test instrumented PASS** trên Samsung (0 fail), bao gồm cả các integration test cũ (Room/DataStore) không bị ảnh hưởng.
- Toàn bộ `testAppReleaseDebugUnitTest` (30 test class) PASS, 0 failure/error.
- **Smoke test thật (UI tay):** device Samsung SM_A115F (R9JN61LDLFJ, khoá theo yêu cầu user giữa phiên). Chạy toàn bộ luồng editor (chọn ảnh → sửa text → export) không crash, logcat sạch `FATAL EXCEPTION`/`AndroidRuntime`.
