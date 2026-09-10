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
- [x] Không tuỳ chỉnh gì → hành vi 4 style giữ nguyên y hệt hiện tại (không breaking).
- [x] Đổi màu băng/độ dày → export ra đúng theo tham số đã chọn, đúng ở cả 4 style.
- [x] Tham số tuỳ chỉnh được persist qua DataStore (theo pattern `WaterMarkRepository` sẵn có).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-14`, file ticket = `todo/FEAT-14-custom-frame-builder-tham-so-hoa.md`.

## Kết quả kiểm chứng (2026-09-10)

**Code**: `ExifFrameStyle` enum mang `defaultBandColor/defaultBandThicknessPercent/defaultUseSerifCaption` cho 4 style; `WaterMark` thêm 3 field nullable `exifBandColor/exifBandThicknessPercent/exifUseSerifCaption` (null = dùng mặc định style); `WaterMarkRepository` thêm `updateExifBandColor/updateExifBandThicknessPercent/updateExifUseSerifCaption/resetExifCustomization` + 3 DataStore key mới, clamp thickness trong `[0.04, 0.30]`; `MainViewModel.buildExifBorderBitmap` + 4 hàm build nhận thêm 3 tham số override (mặc định `null` giữ hành vi gốc); UI `ExifPbFragment` thêm nhóm `groupCustomize` (color swatch mở `ColorPickerDialog`, slider độ dày, switch serif, nút Reset).

**Unit test**: `testAppReleaseDebugUnitTest` PASS — `MainViewModelExifBorderRoboTest` (13 test, gồm case override null/non-null cho cả 4 style) + `ExifFrameStyleTest`. `WaterMarkRepositoryIntegrationTest` (androidTest, 14 test) cover persist/clamp/reset qua DataStore thật.

**Smoke test thủ công — thiết bị TECNO BG6 (serial `118743744X002560`)**:
1. Build `assembleAppReleaseDebug` + cài `installAppReleaseDebug` lên đúng serial BG6 — thành công.
2. Mở ảnh thật (EXIF Make/Model = "TECNO BG6") qua `ACTION_SEND`, bật switch "Leica EXIF Border".
3. Duyệt lần lượt cả 4 style (Classic/Polaroid/Film Strip/Minimal) — giá trị mặc định hiển thị trên UI khớp đúng code: Classic (trắng, 12%, sans), Polaroid (trắng, 16%, **serif**), Film Strip (đen, 10%, sans), Minimal (trắng, 6%, sans) → xác nhận AC1 (không tuỳ chỉnh = giữ nguyên hành vi gốc).
4. Tuỳ chỉnh trên style Minimal: đổi band color → đỏ (qua `ColorPickerDialog`), kéo slider độ dày → 21%, bật switch serif caption.
5. Export ảnh (JPEG, quality 80) → hệ thống mở Share sheet thành công (không crash). Pull file `Pictures/WaterMarkCreator/ewm_*.jpg` về máy, crop vùng band đáy: xác nhận đúng màu đỏ đã chọn, caption dùng font **serif**, đọc đúng "TECNO BG6" từ EXIF thật của ảnh nguồn → xác nhận AC2.
6. Tắt app hoàn toàn (`am force-stop`) rồi mở lại → 3 giá trị tuỳ chỉnh (đỏ/21%/serif) vẫn còn nguyên → xác nhận AC3 (persist qua DataStore).
7. Bấm "Reset" → cả 3 giá trị quay về đúng mặc định của style đang chọn (trắng/6%/sans cho Minimal).
8. `logcat -s` toàn bộ phiên (từ lúc cài đến hết test) trên đúng serial BG6: không có `FATAL EXCEPTION`, không ANR, không log lỗi tag app.
9. Không gặp quảng cáo che UI trong suốt quá trình test (app dùng ad ID debug/test).

Kết luận: cả 3 AC đạt, không crash, không regression trên 4 style gốc.
