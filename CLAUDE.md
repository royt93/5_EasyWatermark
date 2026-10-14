# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Tổng quan

Ứng dụng Android (Kotlin, View system + ViewBinding) đóng dấu watermark hàng loạt lên ảnh.
Fork/rebrand của EasyWatermark, package `com.mckimquyen.watermark` (tên Gradle root: `Watermark_Creator`).
Kiến trúc MVVM + Hilt DI, không dùng Jetpack Compose.

- compileSdk/targetSdk 37, minSdk 24, JVM 17, Kotlin 2.1.0, AGP 8.7.2, Gradle 8.9.
- Toàn bộ giao tiếp/comment trong repo bằng tiếng Việt — giữ nguyên quy ước này.

## Lệnh thường dùng

Build chỉ 1 trục buildType (`debug`, `release`) — không còn `productFlavors` (2 flavor rỗng `appTest`/`appRelease` đã gộp bỏ ở ENH-26).

```bash
./gradlew assembleDebug     # build APK debug
./gradlew assembleRelease   # build APK release (minify + shrink + ký)
./gradlew installDebug      # cài lên thiết bị/emulator
./gradlew lint               # Android lint (baseline: app/lint-baseline.xml)
./gradlew ktlintCheck        # kiểm tra style (ktlint áp cho mọi module)
./gradlew ktlintFormat       # tự sửa style
./gradlew clean
```

- **Unit test (`app/src/test`, ~310 file)**: rải khắp `data/{backup,db,model,repo}`, `di`, `export`, `feature/vip`, `ui/{about,adapter,dlg,panel,widget}`, `utils/{bitmap,ktx}`. Test Robolectric mang hậu tố `*RoboTest`.
  ```bash
  ./gradlew testDebugUnitTest                                    # toàn bộ unit test
  ./gradlew testDebugUnitTest --tests "*.DateConverterTest"      # 1 class
  ```
- **Instrumentation test (`app/src/androidTest`)**: `ui/MainViewModelCompressImgIntegrationTest`, `e2e/BatchWatermarkE2EAndroidTest`, `utils/bitmap/BitmapUtilsContextThreadingIntegrationTest`, `utils/bitmap/BitmapUtilsDecodeFailureIntegrationTest`, `utils/bitmap/ExifBorderAutoPaletteIntegrationTest` (pixel khung EXIF theo palette, IDEA-18), `utils/LocationTokenIntegrationTest` (đọc EXIF GPS + Geocoder thật, IDEA-16), `export/BrandComplianceContrastIntegrationTest` (tương phản màu watermark trên bitmap thật qua Palette, IDEA-15), `export/ProofingIndexIntegrationTest` (ghi và đọc HTML index thật qua DocumentFile, IDEA-13), `data/db/TemplateDaoIntegrationTest` (Room), `data/db/BatchHistoryDaoIntegrationTest` (Room, FEAT-04), `data/db/WatermarkProfileDaoIntegrationTest` (Room, FEAT-06), `data/repo/WaterMarkRepositoryIntegrationTest` (DataStore), `data/repo/SignatureRepositoryImportIntegrationTest` (decode Skia thật — Robolectric không mô phỏng đúng decode-failure cho bytes rác), `export/BatchExportEngineCustomDirectoryIntegrationTest` (ghi file SAF thật qua `DocumentFile.fromFile()`). Chạy bằng `./gradlew connectedDebugAndroidTest` (cần thiết bị/emulator).
- **Pitfall instrumentation** (đã vấp thật):
  - Luôn đặt `ANDROID_SERIAL=<serial>` — thiếu thì Gradle chạy trên MỌI thiết bị đang cắm (vi phạm R3). Không có `--tests`; lọc bằng `-Pandroid.testInstrumentationRunnerArguments.class=<fqcn>` (hoặc `...package=`).
  - Test method phải trả `void`: viết `fun x() { runBlocking { ... } }`, KHÔNG `fun x() = runBlocking { ... }` (JUnit báo `InvalidTestClassError ... should be void`).
  - Test Activity tự khai báo/lồng không resolve được (không có trong manifest, khác process) → dùng `MainActivity`/`OpenSourceActivity` thật; không thêm `app/src/androidTest/AndroidManifest.xml`.
  - `connectedDebugAndroidTest` gỡ app sau khi chạy → cài lại bằng `adb -s <serial> install -r app/build/outputs/apk/debug/<tên apk>.apk` (tên file có version, không phải `app-debug.apk`).
  - `uiautomator dump` có thể trả root null khi máy có accessibility service bên thứ ba; kiểm a11y bằng `AccessibilityNodeInfo` trong instrumentation.
  - Tool MCP screenshot bám serial cũ; khi đổi máy dùng `adb -s <serial> exec-out screencap -p`.
  - `ViewPropertyAnimator.cancel()` vẫn chạy `withEndAction` → cần epoch guard; Robolectric hoàn tất animator trong 1 tick nên không test được giữa chừng.
  - Test phụ thuộc class chỉ có ở `app/src/debug` (`VipTokenSmoke*`) phải nằm ở `app/src/testDebug`, không phải `app/src/test` — nếu không `testReleaseUnitTest` không biên dịch.
  - Không dùng `ObjectAnimator.ofInt(<Int boxed>, ...)`: target boxed chỉ giữ qua `WeakReference`, GC thu là animator tự `cancel()` (màu kẹt giữa chừng; từng gây flaky `MainActivityColorPaletteAnimatorRoboTest`). Dùng `ValueAnimator.ofArgb` như `Int.toColor`.
- CI (`.github/workflows/pr_pre_check.yml`): PR vào `master`/`dev` chỉ chạy `assembleDebug` (JDK 17) — không chạy test/lint/ktlint, nên tự chạy local trước khi push.
- 2 module benchmark (`baseBenchmarks`, `macrobenchmark`) vẫn bị comment trong `settings.gradle.kts` — chưa dùng được.
- **Secret gom 1 file**: `myKeyStore/com.mckimquyen.watermark/app.properties` (repo private `royt93/myKeyStore`, env `WATERMARK_PRIVATE_CONFIG_DIR` hoặc fallback `~/AndroidStudioProjects/@mckimquyen/myKeyStore/...`) — keystore, ad ID release, AppLovin key, VIP secret, `ADMOB_TEST_DEVICE_IDS`. Release thiếu key → build fail. Hash máy test áp cả debug+release, chỉ thêm không xoá. KHÔNG đưa secret vào repo này.

## Cấu hình build & dependency (lưu ý đặc biệt)

- **Version catalog được khai báo inline trong `settings.gradle.kts`** (khối `dependencyResolutionManagement { versionCatalogs { create("libs") {...} } }`), **không phải** `gradle/libs.versions.toml`. Thêm/sửa thư viện ở đây.
- `buildSrc/` chỉ có `build.gradle.kts` (plugin `kotlin-dsl`), không còn file Kotlin nguồn nào — `Apps`/`Dependencies` dead object đã xoá (ENH-25), `compileSdk` mọi module đọc trực tiếp giá trị số (37).
- App phụ thuộc nhiều bản vá `resolutionStrategy.force(...)` trong `app/build.gradle.kts` để khóa version (coroutines-android/core, core-ktx, core, kotlin-stdlib) — cẩn trọng khi nâng cấp.
- Dùng **kapt** cho Hilt / Room / Glide compiler.

## Module

- **`:app`** — toàn bộ ứng dụng.
- **`:cmonet`** — thư viện nội bộ về Material You / Monet dynamic color (`CMonet`, `MonetManufacturer`, `SimpleSp`/`IStorage`). `:app` phụ thuộc `:cmonet`.

## Kiến trúc

### Luồng chính & màn hình
- `MyApplication` (`@HiltAndroidApp`) — khởi tạo Ad SDK; không còn static `instance` (context truyền qua `@ApplicationContext`), chỉ còn `applicationScope` và `recoveryMode` trong companion.
- `SplashActivity` → `MainActivity` (single editor) là trung tâm. Còn có `SignatureActivity`, `AboutActivity`, `OpenSourceActivity`.
- `MainActivity` nhận cả intent `ACTION_SEND` (chia sẻ ảnh từ app khác).
- UI điều hướng/đổi trạng thái qua sealed class `ui/UiState.kt` (`GoEdit`, `GoTemplate`, `UseTemplate`...), không dùng Navigation Component. View tùy biến `LaunchView`/`LaunchViewListener` chuyển giữa màn launch và editor.

### MVVM + dữ liệu
- `MainViewModel` (`@HiltViewModel`) là bộ não: phơi `LiveData`/`StateFlow`; `generateImage(...)` chỉ **enqueue** `BatchExportWorker` (WorkManager, ENH-01), logic export thật nằm ở `export/BatchExportEngine` (ViewModel giữ 1 instance riêng cho preview/compare/survivability). Worker đọc config trực tiếp từ repo singleton, chỉ `ViewInfo` đi qua `inputData`. KHÔNG dùng foreground service (quyết định business 2026-09-28, xem KDoc `BatchExportWorker`) — đừng thêm lại.
- Tầng repo trong `data/repo/`, mỗi repo bọc **một DataStore Preferences riêng** (đặt tên qua `@Named`, ví dụ `WaterMarkPreferences`):
  - `WaterMarkRepository` — toàn bộ cấu hình watermark (text/icon uri, màu, alpha, góc xoay, gap, mode...) dưới dạng `Flow<WaterMark>`.
  - `UserConfigRepository`, `MemorySettingRepo`, `TemplateRepository`, `SignatureRepository`.
- Persistence: **Room** cho `Template` (`AppDatabase` v1, asset-seeded `ewm-db`, `TemplateDao`, `DateConverter`), `BatchHistory` (FEAT-04 — `BatchHistoryDatabase` v1, DB riêng KHÔNG asset, `BatchHistoryDao`), `WatermarkProfile` (FEAT-06 — `WatermarkProfileDatabase` v1, DB riêng KHÔNG asset, `WatermarkProfileDao`) và `Recipient` (IDEA-10 — `RecipientDatabase` v1, DB riêng KHÔNG asset) — 3 DB sau tách khỏi `AppDatabase` để tránh rủi ro migration lên asset DB; DB mới theo cùng quy ước (provide trong `di/AppModule.kt`). Mọi cấu hình khác nằm ở DataStore, **không** ở Room.
- DI module: `di/AppModule.kt`, `di/DataStoreModule.kt`, `di/RepositoryModule.kt`.

### Export (`export/`)
- `BatchExportEngine` (render + ghi file/MediaStore/SAF), `BatchExportWorker` (WorkManager), `ExportNaming`, `AutoPlacementEngine`, `BrandComplianceScorer`, `ProofingMode`, `SurvivabilityProfile`.
- `export/stego/` — watermark ẩn tự viết (DCT 8x8 miền JPEG: `Dct8x8`, `StegoCodec`, `StegoPayload`, `InvisibleWatermark` cầu nối Bitmap, `HiddenWatermarkReader`). Phần thuật toán thuần JVM (làm việc trên `IntArray`) để unit test thẳng; `AuthenticityStamp`/`AuthenticityVerifier` dựa trên nó.

### Rendering watermark
- `ui/widget/WaterMarkImageView.kt` là custom view vẽ watermark (text hoặc tile bitmap với shader/rotation/alpha). Cảnh báo memory-leak cũ (executor không shutdown, scope không hủy) đã được fix từ trước — xem `doc/todo.md` mục Memory Leak Fixes; `onDetachedFromWindow()` hiện cancel `generateBitmapJob` + release `BitmapCache.BitmapValue`, không còn `Executors.newSingleThreadExecutor()`.
- Tiện ích bitmap ở `utils/bitmap/` (`BitmapUtils`, `BitmapCache`) — decode lấy mẫu, đọc EXIF (`getOrientation`, `TAG_*`).
- Glide custom qua `GlideModule.kt`.

### Tính năng nổi bật
- Watermark Text & Image; Template lưu/tái dùng.
- **EXIF/Leica border** — sinh canvas mở rộng padding để in thông số máy ảnh (đọc EXIF) thành khung ảnh.
- **Signature Studio** (`SignatureActivity` + `ui/widget/SignatureView.kt`) — vẽ chữ ký tay, xuất bitmap rồi đẩy vào luồng Image watermark sẵn có.

### Quảng cáo
- Tích hợp qua SDK ngoài **`com.github.royt93:AdmobWrapper`** (`AdManager`), hỗ trợ cả AdMob lẫn AppLovin MAX, chọn provider bằng `BuildConfig.IS_ENABLE_ADMOB`.
- Touchpoints: App Open ở `SplashActivity`; Banner + Interstitial ở `AboutActivity`; MainActivity chỉ preload Interstitial, không tự bắn sau batch export.
- ID đặt qua `buildConfigField` trong `app/build.gradle.kts` (debug dùng ID test của Google; release dùng ID thật) và meta-data trong `AndroidManifest.xml`. Thư mục cũ `sdkadbmob/` (chứa `AdMobManager` tự viết) đã được dọn rỗng sau migrate — xem `doc/AD.MD`.

## Tài liệu trong repo

- `doc/feat.md` — đề xuất tính năng.
- `doc/AD.MD` — kế hoạch migrate Ad sang AdmobWrapper.
- `doc/AD_PROMPT_AOS.MD` — prompt/ghi chú liên quan cấu hình Ad Android.
- `doc/memory_leak.md` & `doc/todo.md` — các vấn đề kỹ thuật cần xử lý (memory leak ở `WaterMarkImageView`, dọn code comment).
- `doc/task/` — hàng đợi ticket kỹ thuật (`BACKLOG.md` là bảng tổng, mỗi ticket 1 file `.md` prefix `BUG-`/`ENH-`/`FEAT-`/`IDEA-`, di chuyển giữa `todo/` → `inprogress/` → `done/` khi đổi trạng thái). `PROMPT_TEMPLATE.md` định nghĩa Definition of Done chung: audit >9/10 theo quy tắc R5 ở trên, test đủ mọi case sửa, smoke test thật trên device đã khoá (R3) mới được move `done/`.
- Các file rời ở gốc repo (`old_launch.kt`, `sim.kt`, `test_anim.kt`, `translate.py`, `build_log.txt`...) là file nháp/tham khảo, **không** thuộc source build.
