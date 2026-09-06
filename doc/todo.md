# Danh sách việc cần làm & Cải tiến

> Cập nhật: 2026-06-14 sau khi audit lại toàn bộ với code hiện tại.

## Tính năng cần triển khai

- [ ] Tích hợp Firebase (vẫn còn `//TODO firebase` trong `MyApplication.kt`)
- [x] ~~Thêm tính năng chọn màu (Color)~~ — ĐÃ XONG (`FuncTitleModel.Color` → `ColorFragment`)
- [ ] Thêm tính năng chia sẻ ứng dụng (Share App) — `ACTION_SEND` hiện chỉ để NHẬN ảnh, chưa có "share app"
- [x] ~~QR Code watermark~~ — ĐÃ XONG (`QrCodeGenerator` + `QrCodeBottomSheetFragment`, reuse luồng Image watermark).

## Cải thiện mã nguồn

- [x] ~~Dọn code comment trong `MyApplication.kt` & `build.gradle.kts`~~ — ĐÃ XONG (cả khối dead-code MaxAd/applyPalette trong `AboutActivity.kt` cũng đã xóa).
- [x] ~~Dọn 7 file nháp ở gốc repo~~ — ĐÃ XONG (`git rm` build_log.txt, fix_anim.kt, old_launch.kt, sim.kt, sim.py, test_anim.kt, translate.py).
- [x] ~~**Hardcoded log tag `roy93~`**~~ — ĐÃ XONG: gom 92 chỗ về hằng số chung `LOG_TAG` trong `AppConst.kt` (top-level, package gốc).
- [x] ~~**Magic numbers**: `MyApplication.catchException` (`1024 * 1024 / 2 / 10`)~~ — ĐÃ XONG: tách hằng `MAX_CRASH_STACK_TRACE_LENGTH` có doc.

## Kiểm thử (Test)

- [x] Đã bật lại test deps trong `settings.gradle.kts` + `app/build.gradle.kts`; thêm `testOptions` cho Robolectric.
- [x] **60 unit test** (JVM + Robolectric) — PASS, gồm 2 file mới cho refactor gỡ `MyApplication.instance`:
  - `FuncPanelAdapterRoboTest` — bind item, màu chữ theo `context` truyền vào, `applyTextColor`/`seNewData`.
  - `DetectedPerformanceSeekBarListenerRoboTest` — predicate hiệu năng theo `context` + mock `ActivityManager.MemoryInfo`.
- [x] **9 integration test** (androidTest, PASS trên TECNO KJ7 - Android 14): Room `TemplateDaoIntegrationTest` (4) + `BitmapUtilsDecodeFailureIntegrationTest` (1) + `BitmapUtilsContextThreadingIntegrationTest` (2, mới — decode JPEG thật + EXIF orientation=90 qua `context` tham số) + `WaterMarkRepositoryIntegrationTest` (2, mới — default text/round-trip qua `@ApplicationContext` + DataStore thật).
- [x] Smoke test thủ công trên TECNO KJ7: Splash → Launch → nhận ảnh qua `ACTION_SEND` → editor render watermark → Export to album (file thật ghi ra `/Pictures/WaterMarkCreator/`) — không crash, logcat sạch `FATAL EXCEPTION`.
- Lệnh: `./gradlew :app:testAppReleaseDebugUnitTest` và `./gradlew :app:connectedAppReleaseDebugAndroidTest`.

## Sửa lỗi rò rỉ bộ nhớ (Memory Leak Fixes)

- [x] ~~**WaterMarkImageView** — scope/executor leak~~ — ĐÃ XONG:
  - `onDetachedFromWindow()` đã override và gọi `generateBitmapJob?.cancel()`.
  - Không còn `Executors.newSingleThreadExecutor()`; dùng `Dispatchers.Default` cho `generateBitmapCoroutineCtx`. (Import rác `Executors` đã được xóa.)
- [x] ~~**MyApplication** — static `instance: Context`~~ — ĐÃ XONG (2026-09-06): gỡ field `instance`, thread `Context` qua toàn bộ chuỗi gọi.
  - `MainViewModel`/`WaterMarkRepository` nhận `@ApplicationContext` qua Hilt (`RepositoryModule.provideWaterMarkRepository` cập nhật theo).
  - `BitmapUtils`: `decodeBitmapWithExif(Sync)`/`decodeBitmapFromUri`/`decodeSampledBitmapFromResource(Sync)` nhận thêm tham số `context`; caller ở `MainViewModel` truyền `appContext`, `WaterMarkImageView` truyền `context` (View) sẵn có.
  - `SaveImageListAdapter`/`PhotoListPreviewAdapter` dùng field `context` sẵn có thay vì `MyApplication.instance`; `FuncPanelAdapter` nhận thêm tham số `context` ở constructor.
  - `DetectedPerformanceSeekBarListener` (class chưa dùng ở đâu) nhận `context` ở constructor.
  - `MainActivity` dùng `application as MyApplication` thay vì `MyApplication.instance as MyApplication`.

## Tham khảo
- Chi tiết các leak đã fix: xem `doc/memory_leak.md`.
- Trạng thái migrate quảng cáo: xem `doc/AD.MD`.
