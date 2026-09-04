# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Tổng quan

Ứng dụng Android (Kotlin, View system + ViewBinding) đóng dấu watermark hàng loạt lên ảnh.
Fork/rebrand của EasyWatermark, package `com.mckimquyen.watermark` (tên Gradle root: `Watermark_Creator`).
Kiến trúc MVVM + Hilt DI, không dùng Jetpack Compose.

- compileSdk/targetSdk 37, minSdk 24, JVM 17, Kotlin 2.1.0, AGP 8.7.2, Gradle 8.9.
- Toàn bộ giao tiếp/comment trong repo bằng tiếng Việt — giữ nguyên quy ước này.

## Lệnh thường dùng

Build có 2 trục: flavor (`appTest`, `appRelease`) × buildType (`debug`, `release`) → biến thể như `appReleaseDebug`, `appReleaseRelease`...

```bash
./gradlew assembleAppReleaseDebug      # build APK debug (flavor appRelease)
./gradlew assembleAppReleaseRelease    # build APK release (minify + shrink + ký)
./gradlew installAppReleaseDebug       # cài lên thiết bị/emulator
./gradlew lint                         # Android lint (baseline: app/lint-baseline.xml)
./gradlew ktlintCheck                  # kiểm tra style (ktlint áp cho mọi module)
./gradlew ktlintFormat                 # tự sửa style
./gradlew clean
```

- **Unit test (`app/src/test`)**: `QrCodeGeneratorTest`, `TextTokenResolverTest`, `QrPreviewWidgetTest`, `OutputImageUtilsTest`, `ImageFormatRoboTest` (Robolectric), `ExifModelTest`, `DateConverterTest`.
  ```bash
  ./gradlew testAppReleaseDebugUnitTest                                    # toàn bộ unit test
  ./gradlew testAppReleaseDebugUnitTest --tests "*.DateConverterTest"      # 1 class
  ```
- **Instrumentation test (`app/src/androidTest`)**: 1 test — `TemplateDaoIntegrationTest` (Room). Chạy bằng `./gradlew connectedAppReleaseDebugAndroidTest` (cần thiết bị/emulator).
- 2 module benchmark (`baseBenchmarks`, `macrobenchmark`) vẫn bị comment trong `settings.gradle.kts` — chưa dùng được.
- Release ký bằng các property `KEY_ALIAS` / `KEY_PASSWORD` / `STORE_FILE` / `STORE_PASSWORD` (hiện đặt trong `gradle.properties`, store `keystore.jks`).

## Cấu hình build & dependency (lưu ý đặc biệt)

- **Version catalog được khai báo inline trong `settings.gradle.kts`** (khối `dependencyResolutionManagement { versionCatalogs { create("libs") {...} } }`), **không phải** `gradle/libs.versions.toml`. Thêm/sửa thư viện ở đây.
- `buildSrc/` chỉ chứa `Apps.kt` (hằng `targetSdk`) và `Dependencies.kt`.
- App phụ thuộc nhiều bản vá `resolutionStrategy.force(...)` trong `app/build.gradle.kts` để khóa version (coroutines-android/core, core-ktx, core, kotlin-stdlib) — cẩn trọng khi nâng cấp.
- `app/build.gradle.kts` có 2 khối `compileOptions` (khối đầu set `VERSION_11`, khối sau set `VERSION_17`) — khối sau ghi đè, hiệu lực thật là JVM 17. Đây là artifact còn sót lại trong file, không phải bug cần fix ngay.
- Dùng **kapt** cho Hilt / Room / Glide compiler.

## Module

- **`:app`** — toàn bộ ứng dụng.
- **`:cmonet`** — thư viện nội bộ về Material You / Monet dynamic color (`CMonet`, `MonetManufacturer`, `SimpleSp`/`IStorage`). `:app` phụ thuộc `:cmonet`.

## Kiến trúc

### Luồng chính & màn hình
- `MyApplication` (`@HiltAndroidApp`) — khởi tạo Ad SDK; giữ static `instance` (đã ghi nhận là điểm cần dọn, xem `doc/todo.md`).
- `SplashActivity` → `MainActivity` (single editor) là trung tâm. Còn có `SignatureActivity`, `AboutActivity`, `OpenSourceActivity`.
- `MainActivity` nhận cả intent `ACTION_SEND` (chia sẻ ảnh từ app khác).
- UI điều hướng/đổi trạng thái qua sealed class `ui/UiState.kt` (`GoEdit`, `GoTemplate`, `UseTemplate`...), không dùng Navigation Component. View tùy biến `LaunchView`/`LaunchViewListener` chuyển giữa màn launch và editor.

### MVVM + dữ liệu
- `MainViewModel` (`@HiltViewModel`) là bộ não: phơi `LiveData`/`StateFlow`, và `generateImage(...)` thực hiện **batch processing** — sinh & lưu watermark cho nhiều ảnh (`imageInfoMapFlow`).
- Tầng repo trong `data/repo/`, mỗi repo bọc **một DataStore Preferences riêng** (đặt tên qua `@Named`, ví dụ `WaterMarkPreferences`):
  - `WaterMarkRepository` — toàn bộ cấu hình watermark (text/icon uri, màu, alpha, góc xoay, gap, mode...) dưới dạng `Flow<WaterMark>`.
  - `UserConfigRepository`, `MemorySettingRepo`, `TemplateRepository`, `SignatureRepository`.
- Persistence: **Room** chỉ cho `Template` (`AppDatabase` v1, `TemplateDao`, `DateConverter`). Mọi cấu hình khác nằm ở DataStore, **không** ở Room.
- DI module: `di/AppModule.kt`, `di/DataStoreModule.kt`, `di/RepositoryModule.kt`.

### Rendering watermark
- `ui/widget/WaterMarkImageView.kt` là custom view vẽ watermark (text hoặc tile bitmap với shader/rotation/alpha). Lưu ý cảnh báo memory-leak đã ghi trong `doc/todo.md` (executor không shutdown, scope không hủy ở `onDetachedFromWindow`).
- Tiện ích bitmap ở `utils/bitmap/` (`BitmapUtils`, `BitmapCache`) — decode lấy mẫu, đọc EXIF (`getOrientation`, `TAG_*`).
- Glide custom qua `GlideModule.kt`.

### Tính năng nổi bật
- Watermark Text & Image; Template lưu/tái dùng.
- **EXIF/Leica border** — sinh canvas mở rộng padding để in thông số máy ảnh (đọc EXIF) thành khung ảnh.
- **Signature Studio** (`SignatureActivity` + `ui/widget/SignatureView.kt`) — vẽ chữ ký tay, xuất bitmap rồi đẩy vào luồng Image watermark sẵn có.

### Quảng cáo
- Tích hợp qua SDK ngoài **`com.github.royt93:AdmobWrapper`** (`AdManager`), hỗ trợ cả AdMob lẫn AppLovin MAX, chọn provider bằng `BuildConfig.IS_ENABLE_ADMOB`.
- Touchpoints: App Open ở `SplashActivity`; Banner + Interstitial ở `AboutActivity`; Interstitial cũng bắn khi job finish.
- ID đặt qua `buildConfigField` trong `app/build.gradle.kts` (debug dùng ID test của Google; release dùng ID thật) và meta-data trong `AndroidManifest.xml`. Thư mục cũ `sdkadbmob/` (chứa `AdMobManager` tự viết) đã được dọn rỗng sau migrate — xem `doc/AD.MD`.

## Tài liệu trong repo

- `doc/feat.md` — đề xuất tính năng.
- `doc/AD.MD` — kế hoạch migrate Ad sang AdmobWrapper.
- `doc/AD_PROMPT_AOS.MD` — prompt/ghi chú liên quan cấu hình Ad Android.
- `doc/memory_leak.md` & `doc/todo.md` — các vấn đề kỹ thuật cần xử lý (memory leak ở `WaterMarkImageView`, dọn code comment, hardcoded strings như log tag `roy93~`).
- Các file rời ở gốc repo (`old_launch.kt`, `sim.kt`, `test_anim.kt`, `translate.py`, `build_log.txt`...) là file nháp/tham khảo, **không** thuộc source build.
