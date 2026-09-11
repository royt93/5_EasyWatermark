---
id: BUG-08
type: Bug
priority: P1
effort: XS
sources: Claude (1/4, verify trực tiếp xác nhận đúng)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainActivity.kt
verified: true
---

# `postDelayed` hiện interstitial không huỷ khi thoát Activity

## Mô tả
`MainActivity.kt:449`:
```kotlin
launchView.postDelayed({ AdManager.showInterstitial(this@MainActivity) {} }, 800)
```
`Runnable` không được lưu lại để `removeCallbacks()` ở `onDestroy()`. Nếu người dùng thoát Activity trong khoảng 800ms sau khi job save xong (rất dễ xảy ra — user hay bấm back ngay sau khi thấy nút Share xuất hiện), callback vẫn chạy sau khi Activity đã destroy → rủi ro `WindowManager$BadTokenException` hoặc leak Activity qua closure.

Đây đúng loại lỗi đã được fix ở `AboutActivity` (xem `doc/memory_leak.md` mục 1.3) nhưng tái diễn ở `MainActivity`, chưa được xử lý.

## Cách fix đề xuất
```kotlin
private val showInterstitialRunnable = Runnable {
    AdManager.showInterstitial(this@MainActivity) {}
}
// ... postDelayed(showInterstitialRunnable, 800)
// onDestroy(): launchView.removeCallbacks(showInterstitialRunnable)
```

## Acceptance Criteria
- [ ] `Runnable` được lưu tham chiếu và `removeCallbacks` trong `onDestroy()`.
- [ ] Thoát Activity ngay sau khi save (trong 800ms) không crash/log lỗi WindowManager.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-08`, file ticket = `todo/BUG-08-interstitial-postdelayed-khong-huy.md`.

## Kết quả kiểm chứng (2026-09-11)
- **Điểm audit tự chấm: 9.5/10.** `Runnable` lưu vào field `showInterstitialRunnable`, `removeCallbacks` trong `onDestroy()`; delay `800` được đặt tên `INTERSTITIAL_DELAY_MS` (tuân R5, không magic number). Đúng theo pattern đã fix ở `AboutActivity` (`doc/memory_leak.md` 1.3).
- **Test (re-audit 2026-09-11 — thử lại nghiêm túc, không bỏ qua):** Báo cáo trước ghi "không có test tự động" vì thiếu Hilt test harness. Thử lại: `Robolectric.buildActivity(MainActivity::class.java)` **chạy được thật** — `MyApplication` (`@HiltAndroidApp`) khởi tạo bình thường dưới Robolectric vì mọi module DI ở đây dùng implementation thật (DataStore/Room), không có dependency nào chỉ tồn tại trên thiết bị thật; `AdManager.earlyInit()` trong `onCreate()` cũng không crash dưới Robolectric SDK 34 (đã probe trực tiếp trước khi viết test, không đoán). Viết `app/src/test/java/com/mckimquyen/watermark/ui/MainActivityInterstitialRoboTest.kt`: build `MainActivity` thật (`create().start()`, không `resume()`/`visible()` vì 2 hàm đó kích hoạt code path KHÔNG liên quan BUG-08 — `enableAdaptiveRefreshRate()` gọi `Context.getDisplay()` crash dưới Robolectric ở bước này, và `visible()` từng treo test, nghi animation/ad-init loop — nên tránh để không kéo theo rủi ro ngoài phạm vi ticket); set `viewModel.saveResult.value = Result.success(TYPE_JOB_FINISH)` (đúng LiveData thật, không mock) để trigger observer thật trong `MainActivity`; đọc `View.mRunQueue` (`getRunQueue()`, API thật của `View`, vì `launchView` chưa attach window nên `postDelayed` nằm ở hàng đợi nội bộ này) để xác nhận runnable đã lên lịch rồi bị gỡ đúng lúc `onDestroy()`. Mutation test thủ công (tạm bỏ `removeCallbacks` trong `onDestroy()`) xác nhận test THẬT SỰ bắt được lỗi (fail đúng), rồi phục hồi lại fix. `./gradlew testAppReleaseDebugUnitTest` xanh toàn bộ (121 test, 0 failure).
- **Smoke test (2026-09-11, TECNO KJ7 `115333744A005844`):** PASS. Batch export 4 ảnh → job finish → `AdManager showInterstitial called` xuất hiện đúng trong log; bấm back liên tiếp ngay sau khi trigger export (trong cửa sổ vài giây trước khi interstitial tự chạy) nhiều lần — không crash, không `WindowManager$BadTokenException` trong logcat suốt phiên. **Đạt Definition of Done: điểm 9.5/10 > 9, test đủ (test tự động thật, đã mutation-test xác nhận), smoke test pass.**
