---
id: IDEA-18
type: Idea
effort: XL
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/ExifBorderRenderer.kt
---

# Bộ sinh khung EXIF border mới theo bảng màu ảnh (không giới hạn 4 style cố định)

## Mô tả
Hiện chỉ có 4 style khung cố định (Classic/Polaroid/Film Strip/Minimal, tham số hoá thủ công ở FEAT-14) — thay vì chọn 1 trong 4 style có sẵn, phân tích palette chủ đạo của chính ảnh đang xử lý rồi TỰ SINH bố cục khung mới (màu nền, font accent, vị trí thông số EXIF) khớp tông màu ảnh, tạo cảm giác "khung riêng cho từng ảnh" thay vì lặp lại 4 template cố định.

## Đề xuất
Dùng `Palette` API (androidx.palette, có sẵn trong nhiều nơi code đã dùng Palette cho dynamic color) trích màu chủ đạo từ ảnh, sinh bảng màu khung (nền/chữ/viền) theo màu đó thay vì màu cố định trong 4 style hiện có — vẫn tái dùng layout logic (vị trí text, kích thước band) của `ExifBorderRenderer`.

## Acceptance Criteria
- [x] 2 ảnh có tông màu khác biệt rõ (1 ảnh tối, 1 ảnh sáng rực) — khung EXIF border sinh ra có màu nền/chữ khác nhau phù hợp từng ảnh, không dùng chung 1 bảng màu cố định.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-18`, file ticket = `todo/IDEA-18-bo-sinh-khung-exif-border-moi-theo-bang-mau-anh-khong-gioi-h.md`.

## Kết quả kiểm chứng (2026-09-25)

**Triển khai thực tế**
- Không thêm style thứ 5 (sẽ vỡ `ExifFrameStyleTest.entries_hasExactlyFourStyles` + mọi UI duyệt `entries`) — làm thành cờ `WaterMark.exifAutoPalette` áp lên cả 4 style sẵn có.
- `utils/bitmap/ExifFramePalette.kt` (mới): `resolve(dominantRgb, style)` thuần → `FrameColors(band, primaryText, secondaryText, accent)`; chữ đen/trắng chọn theo tỉ lệ WCAG cao hơn (không dùng ngưỡng luminance 0.5 của `TextEffectRenderer` — nền xám ~#808080 ra chữ trắng chỉ 3.9:1, test phát hiện); chữ phụ pha 25% màu nền nhưng rơi về chữ chính nếu tụt dưới 4.5:1; `null` → `defaultsFor(style)` = đúng các màu từng hardcode.
- `ExifBorderRenderer` (ticket ghi sai path `utils/`, thực tế `utils/bitmap/`): 4 builder nhận `FrameColors` thay vì `bandColor` + `Color.*` hardcode. Caption Film Strip giữ trắng vì nằm trên scrim đen phủ ảnh, không nằm trên dải nền.
- `BatchExportEngine`: bật cờ → `Palette.from(mutableBitmap).generate().dominantSwatch` (Palette tự thu nhỏ ~112px, rẻ ở full-res). Cờ bật thì bỏ qua màu band chọn tay (FEAT-14).
- `WaterMarkRepository`: key + `updateExifAutoPalette` + đọc + `applyWaterMark` (profile FEAT-06) + `resetExifCustomization` xoá luôn cờ. `MainViewModel.updateExifAutoPalette`.
- UI `dlg_exif_border.xml`: `MaterialSwitch swExifAutoPalette` trong `groupCustomize`; bật thì hàng màu dải viền mờ 38% (M3 disabled) + khoá click. Strings EN + VI.

**Audit: 9.5/10** — trừ 0.5: khung vẫn chỉ render lúc export (hạn chế sẵn có của app, ghi rõ trong mô tả tính năng), nên user chỉ thấy màu khung sau khi xuất.

**Test** — `./gradlew ktlintCheck :app:testDebugUnitTest` xanh toàn bộ.
- `ExifFramePaletteRoboTest` (7): nền tối → chữ trắng, nền sáng → chữ đen, ảnh tối vs sáng ra band + chữ khác nhau (AC), mọi style × 5 màu mẫu đều ≥ 4.5:1 cả chữ chính/phụ, band bị ép opaque, `null` → mặc định style, mặc định khớp màu hardcode cũ.
- `ExifAutoPaletteSwitchWidgetTest` (3, widget): MaterialSwitch M3, tắt mặc định, có contentDescription, nằm trong `groupCustomize`, đặt trước hàng màu dải viền.
- `MainViewModelExifBorderRoboTest` +1: `frameColors` không đổi kích thước khung ở cả 4 style.
- `WaterMarkRepositoryApplyWaterMarkRoboTest` +2 (và mở rộng case round-trip mọi field): setter/reset, apply profile ghi đè.
- androidTest `ExifBorderAutoPaletteIntegrationTest` (2, Skia thật): pixel dải nền ảnh tối/sáng khác nhau và khớp tông ảnh; tắt cờ vẫn trắng. `WaterMarkRepositoryIntegrationTest` +1 (DataStore thật). Chạy trên TECNO KJ7: 22/22 xanh.

**Smoke test thật** — TECNO KJ7 (`115333744A005844`), Android 14: tạo 2 JPEG có EXIF Canon EOS R5 (1 navy tối, 1 vàng sáng) → chọn cả 2 → Khung → bật Leica EXIF + Cổ điển + "Màu khung theo ảnh" (hàng màu dải viền mờ đúng) → Xuất 2/2. File xuất: pixel dải nền `(15,24,55)` với chữ trắng và `(247,216,89)` với chữ đen, thông số EXIF đọc rõ. Logcat không có FATAL/ANR. Không gặp quảng cáo.
