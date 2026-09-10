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
- [ ] Chỉ còn 1 định nghĩa duy nhất cho chuỗi fallback "Unknown Device".
- [ ] Test: `ExifModelTest` xác nhận `getCameraName()` trả đúng hằng số khi thiếu EXIF; test dispatch EXIF border xác nhận điều kiện lọc trong `buildMinimalExifBorder` vẫn đúng sau khi đổi tham chiếu.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-18`, file ticket = `todo/ENH-18-unknown-device-fallback-hang-so-chung.md`.
