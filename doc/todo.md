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
- [x] **31 unit test** (JVM + Robolectric): `ExifModelTest`, `DateConverterTest`, `TextTokenResolverTest`, `OutputImageUtilsTest`, `ImageFormatRoboTest`, `QrCodeGeneratorTest`, `QrPreviewWidgetTest` — PASS.
- [x] **4 integration test** Room `TemplateDaoIntegrationTest` (androidTest) — PASS trên 2 thiết bị thật (Android 16).
- Lệnh: `./gradlew :app:testAppReleaseDebugUnitTest` và `./gradlew :app:connectedAppReleaseDebugAndroidTest`.

## Sửa lỗi rò rỉ bộ nhớ (Memory Leak Fixes)

- [x] ~~**WaterMarkImageView** — scope/executor leak~~ — ĐÃ XONG:
  - `onDetachedFromWindow()` đã override và gọi `generateBitmapJob?.cancel()`.
  - Không còn `Executors.newSingleThreadExecutor()`; dùng `Dispatchers.Default` cho `generateBitmapCoroutineCtx`. (Import rác `Executors` đã được xóa.)
- [ ] **MyApplication** — static `instance: Context` (`@SuppressLint("StaticFieldLeak")`) vẫn còn. **Khuyến nghị HOÃN:** chỉ giữ Application context (không leak Activity), nhưng `instance` được dùng ở ~11 nơi gồm cả top-level functions (`BitmapUtils`: `decodeBitmapFromUri`/`getOrientation`/`interChangeSize`), adapter (`SaveImageListAdapter`, `PhotoListPreviewAdapter`, `FuncPanelAdapter`) và repo. Gỡ hẳn cần thread `Context` qua API của các hàm util (cascading, rủi ro hồi quy cao) trong khi lợi ích thực tế thấp. Nếu làm: inject `@ApplicationContext` vào `MainViewModel` + `WaterMarkRepository` (Hilt), `itemView.context` cho adapter, và thêm tham số `Context` cho hàm trong `BitmapUtils`.

## Tham khảo
- Chi tiết các leak đã fix: xem `doc/memory_leak.md`.
- Trạng thái migrate quảng cáo: xem `doc/AD.MD`.
