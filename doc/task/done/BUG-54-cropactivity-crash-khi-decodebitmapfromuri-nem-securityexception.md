---
id: BUG-54
type: Bug
priority: P1
effort: XS
sources: re-audit 2026-10-04 (agent ui) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/BitmapUtils.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/CropActivity.kt
---

# `CropActivity` crash khi ảnh không mở được — `decodeBitmapFromUri` không bắt `SecurityException`/`FileNotFoundException`

## Mô tả
`decodeBitmapFromUri` (`BitmapUtils.kt` ~dòng 395-430) gọi `resolver.openInputStream(uri)` 2 lần mà không try/catch. Provider ném `SecurityException` (quyền đọc URI tạm của ACTION_SEND không chuyển sang Activity mới) hoặc `FileNotFoundException` (ảnh bị xoá giữa chừng) → ngoại lệ lọt qua `lifecycleScope.launch` trong `CropActivity.loadImage()` → crash app. `SmartRedactionActivity` đã tự vá tại chỗ và ghi comment thừa nhận hàm dùng chung có lỗ hổng, nhưng gốc chưa sửa. Nhánh `isFailure()` của Crop chỉ `finish()` im lặng, không báo user.

## Đề xuất
- Bọc 2 lần `openInputStream` (và `readExifOrientationAndModel` nếu cần) trong `decodeBitmapFromUri` bằng `try/catch (SecurityException, IOException)` → `Result.failure`. Không nuốt `CancellationException`.
- `CropActivity`: toast `R.string.error_file_not_found` trước `finish()` ở nhánh failure.

## Acceptance Criteria
- [x] `openInputStream` ném `SecurityException` → `decodeBitmapFromUri` trả `Result.failure`, không throw.
- [x] `openInputStream` ném `FileNotFoundException` → tương tự.
- [x] `CancellationException` vẫn lan truyền bình thường.
- [x] `CropActivity` không crash, có toast khi decode lỗi.
- [x] Unit test (Robolectric, `ContentProvider` giả cùng pattern `BitmapUtilsNullStreamGuardRoboTest`) cho cả 2 loại ngoại lệ.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-54`, file ticket = `todo/BUG-54-cropactivity-crash-khi-decodebitmapfromuri-nem-securityexception.md`.

## Kết quả kiểm chứng

**Fix:** `decodeBitmapFromUri` giờ bọc `decodeBitmapFromUriUnguarded` bằng `try/catch (SecurityException, IOException)` → `Result.failure` + `AppLog.w`. `CancellationException` không bị bắt (không extend `IOException`/`SecurityException`). `CropActivity` toast `R.string.error_file_not_found` trước `finish()` ở nhánh failure.

- **Audit:** 9.3/10 — sửa ở gốc nên vá luôn mọi caller dùng chung hàm; không thêm magic number/`!!`/resource cần dispose. Trừ điểm: chưa có test Robolectric riêng cho toast của `CropActivity`.
- **Unit test:** file mới `BitmapUtilsOpenStreamThrowsRoboTest.kt` — 2 test (provider ném `SecurityException`, `FileNotFoundException`), RED thật trước fix (2/2 fail), GREEN sau fix. `./gradlew testDebugUnitTest` toàn bộ xanh, `ktlintCheck` xanh.
- **Smoke test thật** trên device đã khoá (Pixel 7 Pro, serial `2B051FDH3006MU`): cài `assembleDebug`, Chọn ảnh → editor → Cắt ảnh → `CropActivity` load ảnh và hiển thị bình thường, back về không crash, logcat không có `FATAL EXCEPTION`. Không có quảng cáo che UI. Giới hạn: nhánh lỗi (mất quyền/ảnh bị xoá) không kích hoạt được trên máy thật — `CropActivity` không exported nên `am start` bị chặn (`not exported from uid 10341`), đã phủ bằng unit test.
