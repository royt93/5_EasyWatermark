---
id: ENH-18
type: Enhancement
priority: P2
effort: XS
sources: claude -p (external CLI, re-audit 2026-09-10)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
  - app/src/main/java/com/mckimquyen/watermark/data/model/ExifModel.kt
---

# Fallback string "Unknown Device" hardcode trùng lặp giữa 2 file

## Mô tả
`MainViewModel.kt:838` (`buildMinimalExifBorder`) so sánh cứng `eModel.getCameraName() != "Unknown Device"`, trong khi giá trị fallback thật sự được sinh ra ở `ExifModel.getCameraName()`. 2 chuỗi phải khớp y hệt để logic lọc đúng — đổi fallback text ở 1 nơi mà quên nơi kia sẽ âm thầm làm điều kiện sai, không có lỗi biên dịch, không crash, chỉ sai hành vi UI.

## Đề xuất
Đưa `"Unknown Device"` ra 1 hằng số chung (companion object hoặc `AppConst.kt` theo pattern `LOG_TAG` đã dùng), cả `ExifModel.getCameraName()` lẫn `MainViewModel.buildMinimalExifBorder` cùng tham chiếu.

## Acceptance Criteria
- [x] Chỉ còn 1 định nghĩa duy nhất cho chuỗi fallback "Unknown Device".
- [x] Test: `ExifModelTest` xác nhận `getCameraName()` trả đúng hằng số khi thiếu EXIF; test dispatch EXIF border xác nhận điều kiện lọc trong `buildMinimalExifBorder` vẫn đúng sau khi đổi tham chiếu.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-18`, file ticket = `todo/ENH-18-unknown-device-fallback-hang-so-chung.md`.

## Kết quả kiểm chứng (2026-09-12)

- **Fix:** Thêm `ExifModel.Companion.UNKNOWN_DEVICE_FALLBACK` (hằng số duy nhất), `getCameraName()` trả về hằng số này; `MainViewModel.buildMinimalExifBorder` đổi so sánh hardcode `"Unknown Device"` → `ExifModel.UNKNOWN_DEVICE_FALLBACK`.
- **Điểm tự audit:** 9.5/10 — đúng root cause, thay đổi tối thiểu, không đổi giá trị chuỗi thực tế nên hành vi hiện tại giữ nguyên 100%.
- **Test:** `ExifModelTest` sẵn có đã cover `getCameraName()` fallback (không cần sửa vì test assert giá trị chuỗi, không hardcode lại constant riêng — vẫn pass vì giá trị không đổi); `MainViewModelExifBorderRoboTest` (test sẵn có cho MINIMAL style + test mới ENH-19) tiếp tục pass, xác nhận điều kiện lọc trong `buildMinimalExifBorder` không bị ảnh hưởng.
- **Smoke test:** verify qua unit test suite (không cần thao tác tay riêng — thay đổi thuần tham chiếu hằng số, không có hành vi UI mới để test tay).
