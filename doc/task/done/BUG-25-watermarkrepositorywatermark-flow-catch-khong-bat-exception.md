---
id: BUG-25
priority: P1
type: Bug
effort: S
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/data/repo/WaterMarkRepository.kt
---

# `WaterMarkRepository.waterMark` Flow: `.catch` không bắt exception từ `obtainSealedClass` trong `.map`

## Mô tả
`.catch { }` gắn ngay sau `dataStore.data` chỉ bắt được exception xảy ra TRƯỚC nó trong chain (đúng ngữ nghĩa Flow — `catch` chỉ bắt upstream). `TextPaintStyle.obtainSealedClass()`/`TextTypeface.obtainSealedClass()` gọi bên trong `.map` PHÍA SAU `.catch` — nếu ordinal lưu trong DataStore ngoài range hợp lệ (DataStore hỏng, restore từ backup cũ/version khác), các hàm này `throw IllegalArgumentException`, văng thẳng ra mọi collector (`MainViewModel`, `AboutViewModel`...) không qua `.catch`. `Anchor.obtain()`/`ExifFrameStyle.obtain()` cùng file đã xử lý an toàn bằng `entries.getOrElse(ordinal) { default }` — 2 hàm kia thì chưa.

## Triển khai
Đổi `TextPaintStyle.obtainSealedClass()`/`TextTypeface.obtainSealedClass()` sang pattern `getOrElse(ordinal) { default }` giống `Anchor.obtain()`/`ExifFrameStyle.obtain()`, hoặc di chuyển `.catch` xuống sau toàn bộ `.map`.

## Acceptance Criteria
- [x] Ghi giá trị ordinal ngoài range hợp lệ trực tiếp vào DataStore (test) — `waterMark` Flow không crash, fallback về giá trị mặc định.
- [x] Giá trị hợp lệ bình thường không đổi hành vi.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-25`, file ticket = `todo/BUG-25-watermarkrepositorywatermark-flow-catch-khong-bat-exception.md`.

## Kết quả kiểm chứng (2026-09-16)

**Fix**: `TextPaintStyle.obtainSealedClass()` và `TextTypeface.obtainSealedClass()` (`data/model/TextPaintStyle.kt`, `data/model/TextTypeface.kt`) — nhánh `else` đổi từ `throw IllegalArgumentException(...)` sang fallback về giá trị mặc định (`Fill`/`Normal`), cùng pattern với `Anchor.obtain()`/`ExifFrameStyle.obtain()` đã có sẵn trong file.

**Unit test mới**:
- `TextPaintStyleTest.kt`, `TextTypefaceTest.kt` (pure JUnit) — ordinal hợp lệ trả đúng instance; ordinal ngoài range (999, -1) fallback về default, không throw.
- `WaterMarkRepositoryCorruptedOrdinalRoboTest.kt` (Robolectric) — ghi trực tiếp ordinal hỏng vào DataStore cô lập (`newTestWaterMarkDataStore`) qua đúng key thật (`SP_KEY_TEXT_STYLE`/`SP_KEY_TEXT_TYPEFACE`), gọi `repo.waterMark.first()` — xác nhận Flow KHÔNG crash, trả về giá trị mặc định; kèm 1 test giá trị hợp lệ không đổi hành vi.
- Đã verify cả 3 file test THẬT SỰ bắt được bug: tạm revert fix (khôi phục `throw`) → 4/7 test FAILED đúng dự đoán, trong đó `WaterMarkRepositoryCorruptedOrdinalRoboTest` cho thấy rõ `IllegalArgumentException` ném từ `.map` KHÔNG bị `.catch` phía trên bắt (đúng root cause mô tả trong ticket — exception văng thẳng ra ngoài `repo.waterMark.first()`). Khôi phục fix → toàn bộ PASS lại.
- Full suite: `./gradlew testAppReleaseDebugUnitTest` — 279 tests, 0 failures, 0 skipped. `ktlintCheck` — BUILD SUCCESSFUL.

**Smoke test trên device thật** (OPPO CPH1989, serial `FUJZIFIR7DQCNRWW` — user đổi device giữa chừng bằng lệnh "dùng oppo", thay cho Samsung S24 Ultra dùng ở BUG-24):
- Cài APK debug, mở app, chọn ảnh, vào Text Watermark editor → tab Style → sub-tab Style (5 chip "Aa!" ứng với các tổ hợp `TextPaintStyle`/`TextTypeface` thật — đúng đường code vừa sửa).
- Tap qua lần lượt cả 5 chip: preview đổi kiểu chữ bình thường, không crash, PID app (`27071`) không đổi trong suốt quá trình (xác nhận qua `pidof`), `adb logcat -d` không có `FATAL`/exception nào từ package app.
- Đây là smoke test đường HỢP LỆ (regression check — xác nhận fix không phá vỡ hành vi bình thường), không phải test trực tiếp kịch bản ordinal hỏng: DataStore Preferences lưu dạng protobuf nhị phân, không có cách thực tế/an toàn để ghi trực tiếp ordinal hỏng vào file thật của app qua `adb shell` như có thể làm với SharedPreferences XML. Kịch bản ordinal hỏng đã được tái hiện trung thực và đầy đủ qua `WaterMarkRepositoryCorruptedOrdinalRoboTest` (dùng đúng `WaterMarkRepository` thật + DataStore Preferences thật, chỉ khác đường dẫn file — cùng code path 100% với production).

**Tự chấm điểm**: 9.5/10 — root cause đúng, test tự-verify (revert fix → 4 test fail đúng vị trí exception mô tả trong ticket), full suite xanh, smoke test thật trên device cho đường hợp lệ. Trừ 0.5 vì không thể test trực tiếp kịch bản crash gốc trên device thật (giới hạn kỹ thuật của định dạng DataStore protobuf, đã bù bằng Robolectric integration test tái hiện đúng code path).
