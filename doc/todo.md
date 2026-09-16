# Danh sách việc cần làm & Cải tiến

> Cập nhật: 2026-09-16. Xem thêm `doc/feat.md` cho danh sách tính năng (FEAT-XX) — file này tập trung bugfix/cải tiến/hạ tầng.

## Tính năng cần triển khai

- [ ] Tích hợp Firebase (vẫn còn `//TODO firebase` trong `MyApplication.kt`)
- [x] ~~Thêm tính năng chọn màu (Color)~~ — ĐÃ XONG (`FuncTitleModel.Color` → `ColorFragment`)
- [x] ~~Thêm tính năng chia sẻ ứng dụng (Share App)~~ — ĐÃ XONG (2026-09-06): pill "Share App" trong `AboutActivity` (`a_about.xml` id `tvShareApp`, bọc trong `HorizontalScrollView` cùng Rate/More Apps) mở `Intent.ACTION_SEND` text kèm link Play Store (`R.string.share_app_message`). Verify trên emulator: chooser "Sharing text" hiện đúng nội dung `Check out Watermark Creator-Debug: https://play.google.com/store/apps/details?id=com.mckimquyen.watermark`.
- [x] ~~QR Code watermark~~ — ĐÃ XONG (`QrCodeGenerator` + `QrCodeBottomSheetFragment`, reuse luồng Image watermark).

## Cải thiện mã nguồn

- [x] ~~Dọn code comment trong `MyApplication.kt` & `build.gradle.kts`~~ — ĐÃ XONG (cả khối dead-code MaxAd/applyPalette trong `AboutActivity.kt` cũng đã xóa).
- [x] ~~Dọn 7 file nháp ở gốc repo~~ — ĐÃ XONG (`git rm` build_log.txt, fix_anim.kt, old_launch.kt, sim.kt, sim.py, test_anim.kt, translate.py).
- [x] ~~**Hardcoded log tag `roy93~`**~~ — ĐÃ XONG: gom 92 chỗ về hằng số chung `LOG_TAG` trong `AppConst.kt` (top-level, package gốc).
- [x] ~~**Magic numbers**: `MyApplication.catchException` (`1024 * 1024 / 2 / 10`)~~ — ĐÃ XONG: tách hằng `MAX_CRASH_STACK_TRACE_LENGTH` có doc.

## Kiểm thử (Test)

- [x] Đã bật lại test deps trong `settings.gradle.kts` + `app/build.gradle.kts`; thêm `testOptions` cho Robolectric.
- [x] **259 unit test** (JVM + Robolectric, `testAppReleaseDebugUnitTest`) — PASS 100% (2026-09-16).
- [x] **Fix deadlock full-suite (2026-09-16):** `context.waterMarkDataStore`/`userDataStore` (`by preferencesDataStore(...)`) là singleton keyed theo FILE PATH chứ không theo Context — Robolectric chạy hết mọi `@Test` trong 1 JVM fork dùng chung 1 main-thread executor, nên nhiều test method/class vô tình share chung 1 DataStore instance ngầm; nếu Robolectric teardown sandbox đúng lúc DataStore đang giữ Mutex ghi dở, Mutex kẹt khoá vĩnh viễn → `runBlocking { edit {} }` ở test sau treo mãi. Chỉ lộ ra khi chạy full suite không filter `--tests` (đủ nhiều test tích luỹ trong 1 JVM). Fix: `app/src/test/.../testutil/TestDataStores.kt` cấp DataStore cô lập (file tạm riêng mỗi test), áp dụng cho 18 file test đang đụng singleton thật.
- [x] **Fix `SaveImageListAdapterPreviewRoboTest` (2026-09-16):** thiếu theme M3 khi inflate `item_saving_image` (`ProgressImageView` đọc `?attr/colorTertiary`/`colorError`) — bọc `ContextThemeWrapper(context, R.style.Theme_MyApp)`, đúng pattern các test khác đã dùng sau đợt migrate M3.
- [x] **9 integration test** (androidTest, PASS trên TECNO KJ7 - Android 14): Room `TemplateDaoIntegrationTest` (4) + `BitmapUtilsDecodeFailureIntegrationTest` (1) + `BitmapUtilsContextThreadingIntegrationTest` (2, mới — decode JPEG thật + EXIF orientation=90 qua `context` tham số) + `WaterMarkRepositoryIntegrationTest` (2, mới — default text/round-trip qua `@ApplicationContext` + DataStore thật).
- [x] Smoke test thủ công trên TECNO KJ7: Splash → Launch → nhận ảnh qua `ACTION_SEND` → editor render watermark → Export to album (file thật ghi ra `/Pictures/WaterMarkCreator/`) — không crash, logcat sạch `FATAL EXCEPTION`.
- Lệnh: `./gradlew :app:testAppReleaseDebugUnitTest` (toàn bộ) hoặc thêm `--tests "*.ClassName"` cho 1 class; `./gradlew :app:connectedAppReleaseDebugAndroidTest` cho instrumentation test (cần thiết bị).

## Sửa lỗi rò rỉ bộ nhớ (Memory Leak Fixes)

> ⚠️ **Cần re-verify (2026-09-16):** `CLAUDE.md` (root) hiện vẫn ghi `WaterMarkImageView` còn leak
> (executor không shutdown, scope không hủy ở `onDetachedFromWindow`) — mâu thuẫn với mục "ĐÃ XONG"
> ngay dưới đây. Chưa rõ CLAUDE.md lỗi thời hay leak đã tái xuất hiện sau các lần sửa `WaterMarkImageView`
> gần đây (FEAT-11 text-effect, M3 migration). Cần đọc lại code thật trước khi tin bên nào.

- [x] ~~**WaterMarkImageView** — scope/executor leak~~ — ĐÃ XONG (theo ghi nhận cũ, cần re-verify ở trên):
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
