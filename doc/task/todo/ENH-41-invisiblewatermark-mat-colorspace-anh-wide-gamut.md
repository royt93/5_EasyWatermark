---
id: ENH-41
type: Enhancement
priority: P2
effort: M
sources: /code-review --level high (review pass 12, 2026-09-30) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/export/stego/InvisibleWatermark.kt
  - app/src/androidTest/java/com/mckimquyen/watermark/export/stego/InvisibleWatermarkIntegrationTest.kt
---

# `InvisibleWatermark.embed()` — mất `ColorSpace` gốc (Display P3...), lệch màu thấy được trên ảnh wide-gamut

## Mô tả
`embed()` (dòng 35) dựng bitmap kết quả bằng `Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)` — overload này LUÔN tạo bitmap gắn `ColorSpace.get(ColorSpace.Named.SRGB)` mặc định, không có tham số nhận `ColorSpace` gốc của `source`. Nếu ảnh đầu vào mang `ColorSpace` khác sRGB (phổ biến nhất: Display P3 — mặc định trên camera nhiều dòng máy hiện đại, cả Android lẫn ảnh chia sẻ từ iPhone), bitmap trả về bị "đọc nhầm" là sRGB ở các bước sau (hiển thị preview, ghi file) — gây lệch màu thấy được, CHỈ xảy ra khi bật watermark ẩn (không phải lỗi của toàn pipeline export).

## Đề xuất
`Bitmap.createBitmap(width, height, config, hasAlpha, colorSpace)` (API 26+) tạo được bitmap gắn đúng `ColorSpace` — nhưng overload này không nhận `int[]` pixel trực tiếp, cần dựng bitmap trống rồi `setPixels()` hoặc vẽ qua `Canvas`. minSdk hiện tại là 24 (xem `CLAUDE.md`) nên cần gate `Build.VERSION.SDK_INT >= 26`, fallback về hành vi hiện tại (mất ColorSpace, chấp nhận được) cho API 24-25.

## Acceptance Criteria
- [ ] Ảnh nguồn `ColorSpace.Named.DISPLAY_P3` (API 26+) sau `embed()` vẫn giữ đúng `bitmap.colorSpace` — không tự ý đổi thành sRGB.
- [ ] API 24-25: hành vi giữ nguyên như hiện tại (không crash, không cần fix — chấp nhận giới hạn nền tảng).
- [ ] Toàn bộ `InvisibleWatermarkIntegrationTest`/`StegoRobustnessTest`/`StegoPerformanceTest` hiện có vẫn PASS (không phá pipeline `embed()` đã có test bao phủ rộng).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-41`, file ticket = `todo/ENH-41-invisiblewatermark-mat-colorspace-anh-wide-gamut.md`.
