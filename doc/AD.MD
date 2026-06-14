# 📊 KẾ HOẠCH MIGRATE AD SYSTEM SANG ADMOB WRAPPER SDK

> **✅ ĐÃ HOÀN TẤT MIGRATION (cập nhật 2026-06-14).** File dưới là kế hoạch gốc, giữ lại để tham khảo. Trạng thái thực tế trên code:
> - SDK: `com.github.royt93:AdmobWrapper` (`com.roy.sdkadbmob.AdManager`); `AdMobManager.kt` cũ đã xóa.
> - Khởi tạo: `MyApplication.setupAdmob()` (`setConfig` → `earlyInit` → `init`), switch provider bằng `BuildConfig.IS_ENABLE_ADMOB`; ID/Key lấy từ `BuildConfig` (đã có đủ AdMob + AppLovin).
> - App Open: `SplashActivity` (`AdManager.initSplashScreen`) + `registerAppOpenAdLifecycle` trong `MyApplication`.
> - Banner + Interstitial: `AboutActivity` (`loadBanner`/`bannerResume`/`bannerPause`/`bannerDestroy`; `loadInterstitial` + `showInterstitial` trong override `finish()`).
> - Giữ đúng 3 touchpoint, KHÔNG có Reward Ad.

## 1. TÌNH TRẠNG HIỆN TẠI (BEFORE MIGRATION)
Dự án EasyWatermark đang tự triển khai manual integration của AdMob thông qua class local `AdMobManager.kt`.
Các Touchpoints hiện có trong dự án:
- **App Open Ad:** Được gọi tại `SplashActivity`.
- **Banner Ad:** Được gắn tại `AboutActivity` (`layout_ad_banner`).
- **Interstitial Ad:** Được load và show tại `AboutActivity` (trong hàm `finish()`).
- **Reward Ad:** **KHÔNG CÓ** (Không tìm thấy touchpoint).

> **Note Spec 5:** Vì không tự tiện thêm ad mới, quá trình migrate sẽ giữ nguyên 3 touchpoint trên và **không tích hợp Reward Ad**.

---

## 2. NHỮNG THÔNG TIN CẦN THÊM TỪ USER (CẦN CUNG CẤP)
Dự án hiện tại chỉ có AdMob ID. Wrap SDK (`_260321AdmobWrapper`) hỗ trợ cả AppLovin MAX. Vì vậy để build config hoàn chỉnh, tôi cần bạn cung cấp các thông tin sau:
1. **AppLovin SDK Key**
2. **AppLovin Banner ID**
3. **AppLovin Interstitial ID**
4. **AppLovin App Open ID**

*Nếu bạn chỉ muốn tiếp tục chạy AdMob, hãy confirm lại. Tuy nhiên khai báo stub IDs cho AppLovin vẫn có thể cần thiết.*

---

## 3. KẾ HOẠCH TRIỂN KHAI (STEP-BY-STEP)

### BƯỚC 1: Xoá bỏ local implementation cũ
- Xóa file: `app/src/main/java/com/mckimquyen/watermark/sdkadbmob/AdMobManager.kt`.
- Xóa các logic liên quan tới biến `AdMobManager` cũ trong các file `MyApplication`, `SplashActivity`, `AboutActivity`.
- Xoá dependencies AdMob/AppLovin cũ trong `app/build.gradle.kts`.

### BƯỚC 2: Thêm SDK JitPack & Dependency
- Thêm `maven { url = uri("https://jitpack.io") }` vào `settings.gradle.kts` hoặc repository project level.
- Thêm dependency `implementation("com.github.royt93:AdmobWrapper:1.1.1")` vào `app/build.gradle.kts`.
- Thêm các biến `buildConfigField` cho `APPLOVIN_SDK_KEY` và các `APPLOVIN_*_ID` tương ứng (sau khi được cung cấp ở trên).
- Thêm cờ `IS_ENABLE_ADMOB` để switch ad provider.

### BƯỚC 3: Cấu hình Manifest
- Đảm bảo có `com.google.android.gms.ads.APPLICATION_ID`.
- Mới: Thêm `<meta-data android:name="applovin.sdk.key" ... />`.

### BƯỚC 4: Khởi tạo ở MyApplication
- Tạo đối tượng `AdSdkConfig` truyền đủ ID của AdMob/AppLoyin lấy từ `BuildConfig`.
- Gọi tuần tự:
  1. `AdManager.setConfig(adConfig)`
  2. `AdManager.earlyInit(this)`
  3. Khởi tạo Native SDK (MobileAds Init hoặc AppLovinSdk Init dựa theo flag `IS_ENABLE_ADMOB`).
  4. Gọi `AdManager.init(this, ...)`.
- Đảm bảo logic recovery mode hoặc tính toàn vẹn của app được giữ nguyên.

### BƯỚC 5: Migrate App Open Ad (Splash Screen & Background)
- Tại `SplashActivity`: Đổi `AdMobManager(local).initSplashScreen()` thành `AdManager(sdk).initSplashScreen()`.
- Note: SDK wrapper đã tự động bind lifecycle ngầm để catch App Open Ad mỗi khi resume từ background, nên không cần lo logic background tự chế.

### BƯỚC 6: Migrate Banner Ad (AboutActivity)
- Layout tại `AboutActivity` (`layout_ad_banner`) đã chuẩn.
- Đổi hàm load banner thành `AdManager.loadBanner(context, container, tvLabelAd)`.
- **QUAN TRỌNG:** Phải map các App Lifecycle callbacks `onResume`, `onPause`, `onDestroy` vào Activity cho Banner theo định nghĩa của SDK, tránh rò rỉ impression hoặc memory leak.

### BƯỚC 7: Migrate Interstitial Ad (AboutActivity)
- Tại `AboutActivity.onCreate()`: load sẵn ad dùng `AdManager.loadInterstitial(this)`.
- Khi đóng Activity (back/finish): dùng `AdManager.showInterstitial(this)` và catch callback đóng ad để gọi `super.finish()`.

---

## 4. REVIEW POLICY & PERFORMANCE
- Ad Banner: Lifecycle đúng của Wrapper tự pause/resume, tránh invalid traffic hoặc ấn nhầm khi scroll.
- Ad Open App: Wrapper chặn show app open ad ở cold start, delay 5s để tránh trigger nhầm khi ấn ad back lại (AdSafety filters).
- Bug & Memory Leaks: Việc dùng AdManager theo pattern của wrapper huỷ view reference cẩn thận. Cleanup banner trên `onDestroy`.

---

**→ Xin vui lòng phản hồi lại các KEY/ID bạn muốn sử dụng (Mục 2) và review kế hoạch này để tôi bắt đầu code migration.**
