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
