---
id: FEAT-14
type: Feature
priority: P2
effort: S
sources: claude -p (external CLI, re-audit 2026-09-10)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
  - app/src/main/java/com/mckimquyen/watermark/data/model/ExifFrameStyle.kt
---

# Custom Frame Builder — tham số hoá 4 EXIF frame style đã có

## Mô tả
4 style EXIF border (Classic/Polaroid/Film Strip/Minimal, xem `doc/feat.md` mục 9) mỗi style đã được tách thành 1 hàm build riêng (`buildClassicExifBorder`/`buildPolaroidExifBorder`/`buildFilmStripExifBorder`/`buildMinimalExifBorder` trong `MainViewModel`). Thay vì 4 preset cứng, cho phép user tự chỉnh 1 vài tham số nhẹ trên style đang chọn (màu băng nền, độ dày băng, font caption serif/sans) — tái dùng gần như nguyên vẹn 4 hàm build sẵn có, chỉ thêm tham số đầu vào thay vì hardcode.

## Đề xuất
- Thêm data class tham số nhẹ (vd `FrameCustomization(bandColor, bandThicknessPercent, useSerifCaption)`) truyền vào 4 hàm build hiện có thay vì hardcode giá trị bên trong từng hàm.
- UI: thêm 1-2 control (color picker tái dùng `ColorFragment` sẵn có, slider độ dày) trong `ExifPbFragment` khi đã chọn 1 style.
- Giữ giá trị mặc định y hệt hiện tại nếu user không tuỳ chỉnh (backward-compatible).

## Acceptance Criteria
- [ ] Không tuỳ chỉnh gì → hành vi 4 style giữ nguyên y hệt hiện tại (không breaking).
- [ ] Đổi màu băng/độ dày → export ra đúng theo tham số đã chọn, đúng ở cả 4 style.
- [ ] Tham số tuỳ chỉnh được persist qua DataStore (theo pattern `WaterMarkRepository` sẵn có).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-14`, file ticket = `todo/FEAT-14-custom-frame-builder-tham-so-hoa.md`.
