---
id: BUG-43
type: Bug
priority: P2
effort: XS
sources: Claude self-audit 2026-09-27 (truy đường lan exception qua 3 lớp gọi)
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/BitmapUtils.kt
---

# EXIF `focalLength`/`exposureTime` parse không guard — ảnh EXIF lạ bị fail decode mơ hồ

## Mô tả
`buildExifModel()` (`BitmapUtils.kt:242-253`) parse 2 tag EXIF bằng `toDouble()` trần và phép chia
không kiểm 0:

```kotlin
// dòng 250-251
val parts = focalLength.split("/")
if (parts.size == 2) "${parts[0].toDouble() / parts[1].toDouble()}mm" else "${focalLength}mm"
// dòng 244
if (d != null && d < 1) "1/${(1 / d).toInt()}s" else "${exposureTime}s"
```

3 vấn đề:
1. `parts[0].toDouble()` ném `NumberFormatException` nếu tag không phải rational số (EXIF hỏng,
   máy/ROM ghi sai, chuỗi rỗng dạng `"/2"`). Exception này **không được bắt ở đâu**:
   `decodeSampledBitmapFromResourceSync` chỉ `catch FileNotFoundException` + `OutOfMemoryError`
   (dòng 434-443) ⇒ lan ra `WaterMarkImageView.exceptionHandler` (watermark im lặng không vẽ) hoặc
   `generateList catch(Exception)` (ảnh báo "thất bại" không rõ lý do). Ảnh hoàn toàn bình thường
   nhưng không export được.
2. `parts[1].toDouble() == 0.0` → `"Infinitymm"` in ra khung EXIF.
3. `exposureTime` = `"0"` → `d = 0.0`, `1/0 = Infinity`, `.toInt()` = `Int.MAX_VALUE` → caption
   `"1/2147483647s"`.

Cùng họ với BUG-17 (chia cho 0 trong khung EXIF) nhưng ở tầng ĐỌC EXIF, chưa từng ticket hoá.

## Cách fix đề xuất
Tách hàm thuần `parseFocalLength(raw): String` và `parseExposureTime(raw): String` (JVM-friendly,
không cần Robolectric): dùng `toDoubleOrNull()`, bỏ qua mẫu số 0/không hợp lệ → trả chuỗi rỗng
(cùng quy ước "tag thiếu = rỗng" hiện có), làm tròn focal về số gọn (`50mm` thay vì `50.0mm`).

## Acceptance Criteria
- [x] Ảnh có tag `TAG_FOCAL_LENGTH` rác không còn làm decode/export thất bại (không exception lan ra).
- [x] Mẫu số 0 → token rỗng, không in `Infinity`.
- [x] `exposureTime = "0"` → không sinh `1/2147483647s`.
- [x] Ảnh EXIF bình thường: chuỗi hiển thị KHÔNG đổi so với trước (hoặc đổi đúng theo AC làm tròn, có test khoá).
- [x] Unit test cho 2 hàm thuần (rational hợp lệ, mẫu số 0, chuỗi rác, rỗng).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-43`, file ticket = `todo/BUG-43-exif-focal-exposure-parse-khong-guard.md`.

## Kết quả kiểm chứng (2026-09-28)

**Fix:** tách 2 hàm thuần top-level `parseExposureTime(raw)`/`parseFocalLength(raw)` trong
`BitmapUtils.kt`, cả 2 dùng `toDoubleOrNull()` (không còn `.toDouble()` trần ném
`NumberFormatException`), guard mẫu số 0/giá trị không hữu hạn → trả rỗng thay vì `Infinity`/
`Int.MAX_VALUE`. `parseFocalLength` làm tròn số gọn qua `formatFocalValue()` (`50.0mm`→`50mm`,
giữ `23.5mm` nếu số lẻ thật).

- **Điểm tự audit:** 9.5/10 — đúng đề xuất ticket, hàm thuần JVM-only không cần Robolectric, không
  regression case bình thường (đã khoá bằng test cho cả `1/125s` và `2s`).
- **Test:** 12 case mới trong `BitmapUtilsTest.kt` (plain JUnit, không Robolectric) — cả 2 hàm:
  case hợp lệ (giữ nguyên định dạng cũ), mẫu số 0 (rỗng thay vì Infinity), chuỗi rác (không throw),
  rỗng. 24/24 PASS trong file. `./gradlew :app:testDebugUnitTest` không regression.
- **Smoke test thật**: không dựng được ảnh EXIF hỏng/rác thật qua UI trong thời gian hợp lý của
  phiên này (cần chỉnh byte EXIF thủ công bằng công cụ ngoài) — dựa vào 12 unit test JVM làm bằng
  chứng chính, đúng tinh thần ticket ("JVM-friendly, không cần Robolectric"). Verify trên device
  đã khoá (TECNO KJ7): cài bản debug chứa fix, mở app/điều hướng nhiều màn hình trong cùng phiên
  (bao gồm About, editor) không crash, logcat sạch — xác nhận thay đổi không phá vỡ pipeline decode
  ảnh bình thường. Chưa smoke-test trực tiếp caption EXIF focal/exposure trên khung ảnh thật trong
  đợt này.
