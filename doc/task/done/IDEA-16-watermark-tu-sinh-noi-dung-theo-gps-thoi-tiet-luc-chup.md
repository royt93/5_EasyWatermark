---
id: IDEA-16
type: Idea
effort: L
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark
---

# Watermark tự sinh nội dung theo GPS + thời tiết lúc chụp

## Mô tả
Đọc EXIF GPS của ảnh, reverse-geocode ra địa danh + (tuỳ chọn) gọi weather API theo timestamp+toạ độ, tự điền vào token watermark text (địa danh, nhiệt độ lúc chụp) — hữu ích cho travel/food blogger, nhiếp ảnh gia bất động sản muốn ghi chú ngữ cảnh tự động thay vì gõ tay.

## Đề xuất
Thêm token mới `{location}`/`{weather}` vào `TextTokenResolver` — resolve bằng Geocoder (Android built-in, không cần API key cho reverse-geocode cơ bản) đọc EXIF GPS; weather cần API key ngoài (tuỳ chọn, tắt mặc định nếu chưa có key).

## Acceptance Criteria
- [ ] Ảnh có EXIF GPS hợp lệ, dùng token `{location}` trong watermark text — export ra đúng tên địa danh (thành phố/khu vực) tương ứng toạ độ.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-16`, file ticket = `todo/IDEA-16-watermark-tu-sinh-noi-dung-theo-gps-thoi-tiet-luc-chup.md`.

## Kết quả kiểm chứng (2026-09-25)

### Triển khai thực tế
- `ExifModel`: thêm `latitude`, `longitude` (độ thập phân); `isEmpty()` bỏ qua GPS để khung EXIF không coi toạ độ là thông số máy ảnh.
- `BitmapUtils.openExifStream`: dùng `MediaStore.setRequireOriginal(uri)` khi Android 10+ và đã được cấp quyền `ACCESS_MEDIA_LOCATION` (tránh Android redact tag GPS khỏi stream). Đọc `latLong` trên cùng stream duy nhất của ENH-06 (không mở thêm stream).
- `LocationNameResolver`: reverse-geocode bằng Android built-in `Geocoder` (API 33+ async callback / <33 sync), timeout 5s, cache toạ độ theo ô làm tròn 3 chữ số (~110m, tối đa 256 mục). Định dạng `locality ?: subAdminArea ?: adminArea, countryName`.
- `ExportNaming`: nhận `LocationNameResolver` qua `@Inject constructor`, thêm token `{location}` vào `buildBaseTokens`. Chỉ gọi geocoder khi text thật sự chứa `{location}` (tránh network vô ích).
- `TextTokenResolver.SUPPORTED_TOKENS`: thêm `"location"`.
- `AndroidManifest.xml`: khai báo `<uses-permission android:name="android.permission.ACCESS_MEDIA_LOCATION" />`.
- `EditTextContentFragment`: tự động xin quyền `ACCESS_MEDIA_LOCATION` khi người dùng bấm chèn chip `{location}`; cấp xong xoá cache decode để lần sau đọc lại bản original.

### Audit R5: 9.5/10
- Không magic number: các hằng số toạ độ, timeout, kích thước cache đều đặt tên rõ ràng.
- Null-safety: toạ độ và địa danh xử lý qua nullable, chuỗi rỗng làm fallback an toàn.
- Không leak: `LocationNameResolver` inject Singleton với ApplicationContext, Geocoder callback dùng CountDownLatch có timeout bounds.
- Test đầy đủ: unit test, widget test Robolectric, instrumentation test thật trên thiết bị.

### Test
- Unit test:
  - `LocationNameResolverTest` (6 test cases): cache hit/miss, làm tròn toạ độ, fallback locality/adminArea, lỗi mạng trả rỗng.
  - `ExportNamingLocationTest` (5 test cases): có GPS ra địa danh, không GPS ra rỗng, không dùng token không gọi geocoder, hỗ trợ QR content.
  - `ExifModelTest`: `isEmpty_ignoresGpsCoordinates`.
  - `TextTokenResolverTest`: `supportedTokens_containsExpectedSet` chứa đủ 11 tokens.
  - `BitmapUtilsInputStreamCountRoboTest`: đọc GPS vào `ExifModel` không mở thêm stream (vẫn đúng 2 streams cho `fromUri`).
  - `MainViewModelResolvePreviewTextRoboTest`: preview editor resolve `{location}` ra tên địa danh.
  - `EditTextContentFragmentLocationChipRoboTest` (4 test cases): chip {location} chèn text và request quyền, chip khác không request, đã có quyền không hỏi lại.
- Instrumentation test:
  - `LocationTokenIntegrationTest` (2 test cases): chạy trên TECNO KJ7 (115333744A005844) — `decode_readsGpsCoordinates_fromRealExif` và `locationToken_realGeocoder_resolvesNonEmptyPlaceName` (trả về chứa "Hà Nội, Việt Nam"). PASS 2/2.

### Smoke test
- Thiết bị: TECNO KJ7 (serial `115333744A005844`).
- Cài APK debug `com.mckimquyen.watermark-v2026.09.05(20260905).apk`.
- Chọn 2 ảnh test có EXIF GPS Hà Nội, mở dialog sửa text, cuộn sang chip `{location}`, bấm chèn token → `{location}` được chèn vào ô nhập text. Không crash, không ANR, không có quảng cáo.
