# Danh sách việc cần làm & Cải tiến

> Cập nhật: 2026-09-16. Xem thêm `doc/feat.md` cho danh sách tính năng (FEAT-XX) — file này tập trung bugfix/cải tiến/hạ tầng.

## Tính năng cần triển khai

- [ ] Tích hợp Firebase (vẫn còn `//TODO firebase` trong `MyApplication.kt`) — không còn là điều kiện tiên quyết cho Backup/Restore (xem dòng dưới), vẫn treo riêng.
- [x] ~~Backup/Restore Template & Signature~~ — ĐÃ XONG (2026-09-16, đề xuất F): local-only qua SAF + zip, không cần Firebase. Chi tiết xem `doc/feat.md` mục #21.
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
- [x] **271 unit test** (JVM + Robolectric, `testAppReleaseDebugUnitTest`) — PASS 100% (2026-09-16).
- [x] **Fix deadlock full-suite (2026-09-16):** `context.waterMarkDataStore`/`userDataStore` (`by preferencesDataStore(...)`) là singleton keyed theo FILE PATH chứ không theo Context — Robolectric chạy hết mọi `@Test` trong 1 JVM fork dùng chung 1 main-thread executor, nên nhiều test method/class vô tình share chung 1 DataStore instance ngầm; nếu Robolectric teardown sandbox đúng lúc DataStore đang giữ Mutex ghi dở, Mutex kẹt khoá vĩnh viễn → `runBlocking { edit {} }` ở test sau treo mãi. Chỉ lộ ra khi chạy full suite không filter `--tests` (đủ nhiều test tích luỹ trong 1 JVM). Fix: `app/src/test/.../testutil/TestDataStores.kt` cấp DataStore cô lập (file tạm riêng mỗi test), áp dụng cho 18 file test đang đụng singleton thật.
- [x] **Fix `SaveImageListAdapterPreviewRoboTest` (2026-09-16):** thiếu theme M3 khi inflate `item_saving_image` (`ProgressImageView` đọc `?attr/colorTertiary`/`colorError`) — bọc `ContextThemeWrapper(context, R.style.Theme_MyApp)`, đúng pattern các test khác đã dùng sau đợt migrate M3.
- [x] **9 integration test** (androidTest, PASS trên TECNO KJ7 - Android 14): Room `TemplateDaoIntegrationTest` (4) + `BitmapUtilsDecodeFailureIntegrationTest` (1) + `BitmapUtilsContextThreadingIntegrationTest` (2, mới — decode JPEG thật + EXIF orientation=90 qua `context` tham số) + `WaterMarkRepositoryIntegrationTest` (2, mới — default text/round-trip qua `@ApplicationContext` + DataStore thật).
- [x] Smoke test thủ công trên TECNO KJ7: Splash → Launch → nhận ảnh qua `ACTION_SEND` → editor render watermark → Export to album (file thật ghi ra `/Pictures/WaterMarkCreator/`) — không crash, logcat sạch `FATAL EXCEPTION`.
- Lệnh: `./gradlew :app:testDebugUnitTest` (toàn bộ) hoặc thêm `--tests "*.ClassName"` cho 1 class; `./gradlew :app:connectedDebugAndroidTest` cho instrumentation test (cần thiết bị). (Tên task đổi từ `testAppReleaseDebugUnitTest`/`connectedAppReleaseDebugAndroidTest` sau khi gộp bỏ flavor `appTest`/`appRelease` ở ENH-26.)

## Code review (2026-09-16, `/code-review master..dev high`)

Review toàn bộ diff `dev` so với `master` — tìm 6 phát hiện, đã fix hết + thêm test, xem chi tiết
commit `c3af54a`:

- [x] **[Nghiêm trọng]** `BatchExportEngine.generateImage()`: caption rỗng ("" — cố ý không
  watermark ảnh đó) không skip vẽ như `generatePreviewBitmap()` — `layoutPaint` (Paint() mặc định
  đen, alpha 255) vẫn tô kín đè lên ảnh xuất ra vì shader null. Preview/export lệch nhau, ảnh xuất
  ra thật bị đen kín. Fix: `shouldSkipTextWatermark()` dùng chung 2 nơi.
- [x] **[Bảo mật, zip-slip]** `SignatureRepository.importSignatureBytes()`: tên file lấy trực
  tiếp từ zip entry (backup do user chọn qua SAF, không tin cậy), không sanitize — entry tên
  `../../../shared_prefs/evil.xml` có thể ghi đè file ngoài thư mục signature. Fix:
  `File(fileName).name` chỉ lấy phần tên cuối.
- [x] Restore template không dedup — Template.id luôn = 0 (autoGenerate PK mới) nên restore 2 lần
  nhân đôi mọi template. Fix: dedup theo content trước khi insert.
- [x] `BatchCaptionBSDialogFragment`: dùng snapshot `imageList.size` chụp lúc mở dialog thay vì
  đọc live lúc bấm Apply — list ảnh đổi giữa chừng có thể gán sai caption. Fix: đọc lại live.
  **Không test tự động được** (hạn chế đã ghi nhận sẵn — `requireContext() as MainActivity` không
  launch được thật trong JVM test của repo này).
- [x] 2 chỗ DRY: rule `caption ?: text` copy-paste 2 nơi (chính là nguyên nhân bug #1 lệch nhau) —
  gộp thành hàm dùng chung. Boilerplate "expand bottom sheet" lặp 4 lần ở
  BatchCaption/Qr/Signature/PositionAnchor fragment — gộp vào `BaseBSDFragment.expandBottomSheetFully()`.
- [x] **Phát hiện phụ:** viết test cho fix zip-slip lộ thêm 1 bug hạ tầng CÙNG LOẠI với deadlock
  DataStore — `FileProvider.getUriForFile()` cache `PathStrategy` theo authority ở static field
  AndroidX, sống sót qua ranh giới Application/Context của từng `@Test` dưới Robolectric. Fix:
  `SignatureModel.uri` tính lazy (không còn eager ở constructor), tránh gọi `FileProvider` khi
  caller không cần URI ngay — né hẳn collision, cũng là cải tiến perf hợp lý độc lập.

## Sửa lỗi rò rỉ bộ nhớ (Memory Leak Fixes)

> ✅ **Re-verified (2026-09-16):** đọc lại `WaterMarkImageView.kt` thật — `onDetachedFromWindow()`
> vẫn cancel `generateBitmapJob` + release `mainImageBitmapValue`/`iconBitmapValue`, không còn
> `Executors.newSingleThreadExecutor()`. Cảnh báo trong `CLAUDE.md` (root) là lỗi thời, đã sửa lại.
> `drawableAlphaAnimator`/`animator` (snap-back sau pinch) không bị cancel ở `onDetachedFromWindow`
> nhưng duration ngắn (300-450ms) — không đáng kể, không cần fix.

- [x] ~~**WaterMarkImageView** — scope/executor leak~~ — ĐÃ XONG, re-verify lại 2026-09-16 vẫn đúng:
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
