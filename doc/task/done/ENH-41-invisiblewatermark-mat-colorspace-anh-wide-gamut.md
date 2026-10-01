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

## Kết quả kiểm chứng

- **Audit**: 9.7/10 sau review `--level high` lần cuối.
  - Fix đúng scope `embed()`, không magic number (`Build.VERSION_CODES.O`), không force-unwrap/`late`, không leak resource mới.
  - Review phát hiện và đã fix 1 lỗi nghiêm trọng trong bản đầu: `source.colorSpace` từng được gọi vô điều kiện trước khi check SDK — gây `NoSuchMethodError` thật trên API 24-25. Unit test API 24 mới chứng minh nhánh fallback không crash.
  - Tối ưu sau review: chỉ dùng overload 5 tham số + `setPixels()` khi ColorSpace nguồn khác sRGB và model RGB; ảnh sRGB phổ biến giữ fast path cũ, không tốn thêm alloc/copy; ColorSpace non-RGB không gây `IllegalArgumentException` mới.
- **Unit test bổ sung**: 4 Robolectric test mới trong `InvisibleWatermarkRoboTest` — Display P3 giữ đúng ColorSpace; Display P3 round-trip extract đúng ownerId; sRGB giữ hành vi cũ; API 24 fallback không crash + extract đúng ownerId.
- **Widget test**: không thêm — ticket không thay đổi View/layout/UI; toggle "Watermark vô hình" đã được kiểm trực tiếp trong smoke test tay. Thêm widget test cho core bitmap branch sẽ chỉ test plumbing không liên quan, không tăng confidence.
- **Integration test bổ sung**: 2 case trong `InvisibleWatermarkIntegrationTest` trên Skia/device thật — Display P3 giữ ColorSpace **và** extract đúng ownerId; ảnh sRGB giữ ColorSpace/round-trip cũ. Toàn suite: 12/12 PASS trên TECNO KJ7.
- **Toàn bộ test suite**: `./gradlew :app:testDebugUnitTest` + `./gradlew ktlintCheck` — `BUILD SUCCESSFUL`.
- **Smoke test thật**: device khoá session TECNO KJ7 (serial `115333744A005844`, API 34).
  - Build/cài lại APK sau tất cả fix review; thao tác tay end-to-end: chọn JPEG gắn ICC `Display P3` thật → editor → Lưu → bật toggle "Watermark vô hình" → Xuất vào bộ sưu tập → UI báo 1/1 thành công.
  - Pull file xuất `ewm_1790828953183_1.jpg` về kiểm tra bằng `sips -g profile`: **`Display P3 Gamut with sRGB Transfer`**, 800×600 — không bị đổi thành sRGB như bug gốc.
  - `adb logcat` không có `FATAL`/`AndroidRuntime`/`NoSuchMethodError`/exception liên quan trong toàn bộ luồng.
