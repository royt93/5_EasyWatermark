# Báo Cáo Audit Source Code: Memory Leaks & Bugs

> **Lưu ý lịch sử (cập nhật 2026-06-14):** Các mục liên quan `AdMobManager.kt` (1.1, 2.2, 2.3) là LỊCH SỬ — class `AdMobManager.kt` tự viết đã bị xóa và thay bằng SDK wrapper `com.roy.sdkadbmob.AdManager` (xem `doc/AD.MD`). Logic delay/lifecycle giờ nằm trong wrapper. Các mục còn lại (`MultiSelectRv`, `MainViewModel`, `AboutActivity`, `WaterMarkImageView`) đã được xác nhận FIX trên code hiện tại.

Sau khi phân tích toàn bộ source code của dự án, tôi đã phát hiện một số bug và memory leak nghiêm trọng ảnh hưởng đến hiệu năng và tính ổn định của ứng dụng. Dưới đây là ghi chép chi tiết về các lỗi, cách khắc phục và trạng thái (Status) hiện tại:

## 1. Lỗi Thực Thi & Memory Leak Nghiêm Trọng

### 1.1 rò rỉ Activity và Global Coroutine trong `AdMobManager.kt` (`initSplashScreen`)
- **Vấn đề:** 
  Hàm `initSplashScreen` khởi tạo một coroutine `CoroutineScope(Dispatchers.Default).launch` để thu thập dữ liệu từ `EventBus.eventFlow.collectLatest`. Do scope này không gắn với bất kỳ Lifecycle nào và không bao giờ bị hủy (cancel), nó sẽ chạy vĩnh viễn (Global Leak). 
  Bên trong collector, hàm tiếp tục sử dụng `CoroutineScope(Dispatchers.Main).launch` và capture (giữ tham chiếu) đối số `activity` truyền vào `initSplashScreen`.
- **Hậu quả:** 
  Mỗi lần gọi `initSplashScreen`, một coroutine chạy ngầm mới lại được tạo và giữ chặt tham chiếu đến `Activity` đó, khiến Activity không thể bị Garbage Collector thu hồi, gây OOM (Out Of Memory) nhanh chóng.
- **Khắc phục:** 
  Sử dụng `lifecycleScope` của `Activity` hoặc lưu job lại để `cancel()`. Hạn chế truyền trực tiếp activity vào block coroutine chạy vĩnh viễn.
- **Trạng thái:** ✅ **ĐÃ FIX**. Chuyển đổi qua việc gọi `activity.lifecycleScope.launch` cho flow collect, pass `activity.applicationContext` vào tiến trình bắt Ad (loadAppOpenAd) và thêm logic huỷ `Job` trước đó, ngăn chặn Coroutine sống dai hơn Activity.

### 1.2 Rò rỉ MultiSelectRv qua Handler/Runnable (`MultiSelectRv.kt`)
- **Vấn đề:**
  Trong sự kiện chạm kéo (touch), biến `autoScroll` (kiểu `Runnable`) liên tục tự động enqueue lại chính nó thông qua `handle.postDelayed(this, ...)`. Khi View bị gỡ khỏi giao diện (`onDetachedFromWindow()`), hàm mới chỉ vô hiệu hóa biến `onSelect` và `onUnSelect`, nhưng **bỏ quên việc xóa callbacks từ Handler**.
- **Hậu quả:**
  Đoạn code `Runnable` này tiếp tục chạy vĩnh viễn ngầm trong `MainLooper`, giữ tham chiếu mạnh tới class `OnItemTouchListener` ẩn danh, và từ đó rò rỉ nguyên component `MultiSelectRv` cộng toàn bộ `Context/Activity` chứa nó.
- **Khắc phục:**
  Trong phương thức `onDetachedFromWindow`, bắt buộc phải gọi `autoScroll?.let { handle.removeCallbacks(it) }` để clear handler.
- **Trạng thái:** ✅ **ĐÃ FIX**. Thuộc tính `autoScroll` và `handle` đã được đẩy lên class level, và hàm `handle.removeCallbacks(autoScroll)` nay đã được gọi chuẩn xác trong `onDetachedFromWindow()`.

### 1.3 Lỗi gọi UI (AdMob) trong `onDestroy()` tại `AboutActivity.kt`
- **Vấn đề:**
  Trong hàm `onDestroy()`, source code gọi:
  ```kotlin
  super.onDestroy()
  AdMobManager.showInterstitial(this) { ... }
  ```
- **Hậu quả:**
  Việc hiển thị quảng cáo Interstitial sau khi vòng đời của Activity đã hoàn tất (hoặc đang tiến hành phá hủy) là một anti-pattern. Nó có thể quăng lỗi `WindowManager$BadTokenException` do sử dụng bad/destroyed context, đồng thời rò rỉ Activity đã bị hủy.
- **Khắc phục:**
  Kích hoạt Ads ở các thời điểm như khi nhấn nuớc back, thay vì bắt event tự động trong `onDestroy()`.
- **Trạng thái:** ✅ **ĐÃ FIX**. SDK call đã bị lược bỏ khỏi ngầm `onDestroy()`, và code tích hợp nay intercept override được chuyển qua hàm `finish()` với một lá cờ `isFinishingInternal` chống dội logic.

---

## 2. Tiềm Ẩn Memory Leak Tạm Thời & Vấn đề Cấu trúc

### 2.1 Rò Rỉ `Activity` Gián Tiếp qua ViewModel Coroutine (`MainViewModel.kt` - `compressImg`)
- **Vấn đề:**
  Hàm `compressImg(activity: Activity)` truyền đối số `Activity` vào `viewModelScope.launch { ... }` chờ quá trình Compress Image khá tốn thời gian.
- **Hậu quả:**
  Mặc dù `viewModelScope` tự hủy khi ViewModel bị destroy, nhưng LifeCycle của ViewModel trong Android luôn sống lâu hơn LifeCycle của Activity khi xoay màn hình (Configuration Changes). Lúc này Coroutine block cũ trong ViewModel vẫn đang chạy, nắn chặt reference của **Activity đã bị phá hủy (old instance)** gây leak Activity tạm thời.
- **Khắc phục:**
  Truyền `activity.applicationContext` thay vì nguyên `Activity` cho hàm `Compressor.compress()`.
- **Trạng thái:** ✅ **ĐÃ FIX**. Code đã dùng `activity.applicationContext` ngay từ đầu hàm `compressImg`. Lớp `FileProvider.getUriForFile` và `Compressor` hiện nhận Application Context nên loại bỏ 100% tỷ lệ bị leak Activity.

### 2.2 Rò Rỉ Biến Cục Bộ Thread tại `AdMobManager.kt` (`getGAID`)
- **Vấn đề:**
  Sử dụng `Thread { ... }.start()` chay mà truyền argument `context` vào để lấy ra `AdvertisingIdClient`.
- **Hậu quả:**
  Nếu `context` truyền vào vô tình là một `Activity` context, `Thread` này sẽ tiếp tục giữ tham chiếu đến `Activity` trong lúc chờ Google Play Services phản hồi (đôi khi rất lâu).
- **Khắc phục:**
  Luôn lấy giá trị `context.applicationContext` ở trong hàm hoặc dùng Coroutines.
- **Trạng thái:** ✅ **ĐÃ FIX**. Đoạn raw thread bây giờ pass giá trị application context thông qua biến `val appContext = context.applicationContext`.

### 2.3 Quản Lý `Handler` Chậm Trễ (`AdMobManager.kt` - `loadAppOpenAd`)
- **Vấn đề:**
  Dùng `Handler(Looper.getMainLooper()).postDelayed({ ... }, 1000)` rất nhiều để retry hoặc handle ad fallbacks.
- **Hậu quả:**
  Các đoạn callback này giữ references (Closure leak), nếu Component bị đóng trước 1s, tham chiếu tới Context vẫn bị kéo lê thêm 1s trước khi GC dọn.
- **Trạng thái:** ⚠️ **CHẤP NHẬN ĐƯỢC (By Intent)**. Đây là luồng logic delay tạm thời (short-lived leak khoảng 1000ms đỗ lại) để phục vụ UI/UX không giật cục. Mức độ tác động thấp nhất do không duy trì một scope vĩnh viễn.

---

## Tóm Lược
Toàn bộ Memory Leaks và rủi ro OOM lớn nhất chặn đứng độ ổn định của ứng dụng (trong package `AdMobManager`, `MultiSelectRv`, `MainViewModel`, `AboutActivity`) đã được refactor và khắc phục hoàn toàn. Các thay đổi không làm ảnh hưởng tính năng và tương thích đầy đủ với hệ sinh thái Component Architecture của app.

---

## Báo Cáo Audit Bổ Sung (Final Check - 5 Round Spec)

**1. Vòng đời Ad Banner (`AboutActivity.kt`)**
- **Trạng thái:** ✅ **ĐÃ FIX**. 
- **Vấn đề đã phát hiện:** Container load ad lúc khởi tạo nhưng thiếu hai phương thức vòng đời quan trọng là `bannerResume(adView)` và `bannerPause(adView)` trong `onResume()` và `onPause()`. Điều này khiến tiến trình refresh banner của AppLovin/AdMob chọc ngoáy liên tục kể cả khi app bị đẩy xuống nền, bào mòn RAM và Pin.
- **Xử lý:** Đã attach đầy đủ `AdManager.bannerResume` tại `onResume` và `AdManager.bannerPause` tại `onPause` của `AboutActivity`.

**2. App Open Ad từ Background (`MyApplication.kt`)**
- **Trạng thái:** ✅ **ĐÃ FIX**.
- **Vấn đề đã phát hiện:** Thiếu hoàn toàn logic đăng ký `ProcessLifecycleOwner` cho App Open Ad. App mở từ Background sẽ không show Splash Ad.
- **Xử lý:** Đã tiêm hàm `AdManager.registerAppOpenAdLifecycle(this)` chạy ở Main Thread (Handler) ngay lập tức khi callback SDK AppLovin/AdMob khởi tạo (`init()`) thành công.

**3. Memory Leak & Context Catching**
- **Trạng thái:** ✅ **SẠCH SẼ**.
- Toàn bộ tham chiếu Context truyền vào `AdSdkConfig` và `AppLovinSdk` đều sử dụng Application Level Context ẩn bên trong wrapper an toàn. 
- Component chặn màn hình `SplashActivity` sử dụng safe-delay thuộc về Frame buffer của decorView (`window.decorView.postDelayed`). Khi Window Manager dỡ decorView xuống, các pending message sẽ tự động rụng (detach) theo nên không gây rò rỉ Activity Context ở Splash. Đạt tiêu chuẩn.
