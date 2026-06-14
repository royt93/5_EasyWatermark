# AD migration plan - EasyWatermark

Updated: 2026-06-14

## VIP UX revamp + đa ngôn ngữ (2026-06-14, lần 3)

Theo feedback người dùng, đã verify trên Pixel 7 Pro:

1. **Nút "Kích hoạt VIP" revamp**: nền tím `glass_accent`, bo góc 14dp; icon trùng màu label (cùng `@color/vip_redeem_btn_fg`). Chỉ **enable khi field có mã** (`doAfterTextChanged`), disable + làm mờ khi trống — dùng ColorStateList `vip_redeem_btn_bg`/`vip_redeem_btn_fg` để trạng thái disabled hiển thị mờ đúng (backgroundTint màu đơn không tự mờ).
2. **Kết quả kích hoạt dùng Material You dialog** (`MaterialAlertDialogBuilder`, tiêu đề `vip_success_title`/`vip_failed_title`) thay cho Toast — áp cho cả redeem key, rewarded grant, và rewarded fail.
3. **Nút "Xem quảng cáo → 3 ngày VIP" nổi bật**: nền gold `vip_gold` + chữ/icon đậm `vip_on_gold`, bo 16dp, bọc `ShimmerFrameLayout` (shine sweep) + pulse. **Fix lỗi "animation cut UI"**: pulse scale làm nút (match_parent) tràn ra ngoài và bị `clipChildren` mặc định cắt mép → bật `clipChildren=false`/`clipToPadding=false` cho `shimmerWatch` + `cardReward` + `contentContainer`, giảm pulse 1.05→1.03.
4. **Đa ngôn ngữ native-speaker đầy đủ**: bổ sung khối ~37 string VIP cho cả 13 locale app hỗ trợ (de-rDE, es, fr, it, ja, nb-rNO, nn, pt-rBR, pt, ru, zh-rCN, zh-rTW) + vi/en. Mỗi locale 35 vip + cancel/confirm, escape XML đúng (\\' cho fr/it), giữ placeholder `%1$s`/`%1$d`/`%1$dd %2$02dh...`.

Ghi chú: `activateVipByKey` khi VIP đang còn hạn thì **cộng dồn (stacking)** lên hạn cũ — đó là lý do nhập key 3 ngày lúc đang active 30 ngày ra ~30 ngày; nhập từ trạng thái Free thì ra đúng 3 ngày. Hành vi wrapper, không phải lỗi app. Build `:app:assembleAppTestDebug` + `:app:assembleAppReleaseRelease` đều PASS.

## VIP screen full-spec upgrade (2026-06-14, lần 2)

Nâng `VipManagementActivity` lên đúng spec template Step 10 (giữ kiểu Activity, không đổi Fragment):

- **Bug fix chặn app (phát hiện khi test thật trên Pixel 7 Pro)**: gỡ `<meta-data android:name="applovin.sdk.key">` khỏi `AndroidManifest.xml`. AppLovin SDK 13.x (pin bởi wrapper 1.1.5) ném `IllegalStateException: Invalid initialization process` nếu key vẫn còn trong manifest — phải set programmatically (wrapper đã làm qua `AdSdkConfig.applovinSdkKey = BuildConfig.APPLOVIN_SDK_KEY`). Trước fix: app crash ngay ở `SplashActivity` consent callback.
- **UI 13/13 component**: hero header gold/xám + crown shimmer, activation date row, expiry row, `LinearProgressIndicator` elapsed-semantic, countdown `Xd HHh MMm SSs`, key input (clear icon), watch-rewarded, 4 nút Buy disabled (30d/90d/1y/lifetime), active VIP card single-entry + nút Revoke, Restore disabled, privacy footer.
- **Animation 5/5**: pulse nút watch-ad (`ObjectAnimator`), slide/shimmer crown (`ShimmerFrameLayout`), count-up bump khi đổi phút (`ValueAnimator`), confetti khi activate (`konfetti-xml:2.0.5`) + haptic `CONFIRM`/`LONG_PRESS`. Tất cả nullable + cancel ở `onPause`/`onDestroy` (đúng rule memory-leak 10.5).
- **Deps mới**: `nl.dionsegijn:konfetti-xml:2.0.5`, `com.facebook.shimmer:shimmer:0.5.0` (qua version catalog inline trong `settings.gradle.kts`).
- **i18n**: thêm `values-vi/strings.xml` (default repo là tiếng Anh) + bổ sung string VIP mở rộng ở `values/strings.xml`.
- **Toolbar badge**: `BadgeDrawable` vàng gắn lên menu item crown (`actionVip`) của `MainActivity`, refresh ở `onResume`/`onCreateOptionsMenu` theo `AdManager.isVipByKeyActive()`.
- **VipPrefs**: thêm `clearGrantedAtMs()` cho revoke.

Kiểm thử thực tế trên Pixel 7 Pro (192.168.21.185, Android, AppLovin primary):
- `:app:assembleAppTestDebug` + `:app:assembleAppReleaseRelease`: PASS (release qua minify+shrink, konfetti/shimmer không lỗi ProGuard).
- Luồng chạy: Splash → App Open ad (AppLovin MAX test) hiển thị → Main → About (banner AppLovin MAX test hiển thị) → VIP screen.
- Redeem key 3 ngày: kích hoạt OK, confetti bắn, countdown live tick, progress bar rỗng→đầy đúng elapsed-semantic, active card + revoke hiện.
- Revoke: dialog Material confirm → về free state, timer/active card ẩn, reward card hiện lại.
- 0 FATAL exception suốt session sau khi fix manifest.
- `:app` ktlint: các file VIP mới sạch (project có sẵn nhiều vi phạm trailing-comma pre-existing, ngoài phạm vi).

## Implementation status

Implemented on 2026-06-14.

- `./gradlew :app:assembleAppTestDebug`: PASS.
- `./gradlew :app:assembleAppReleaseRelease`: PASS.
- Wrapper dependency is now `com.github.royt93:AdmobApplovinWrapper:1.1.5`.
- AppLovin remains primary provider via `BuildConfig.IS_ENABLE_ADMOB=false`; AdMob rewarded fallback uses the official test rewarded ID.
- Compatibility updates required by wrapper-pinned Google Ads `24.5.0`:
  - Kotlin `2.1.0`.
  - Room `2.8.4`.
  - Hilt `2.57.2`.
  - Disabled lint detector `NullSafeMutableLiveData` because AGP 8.7.2 lint crashes on that lifecycle detector with the Kotlin analysis API used here.

Selected implementation options:
- Scope: full migrate.
- Build verification: `:app:assembleAppTestDebug` and `:app:assembleAppReleaseRelease`.
- Underlying SDK policy: wrapper-pinned transitive dependencies; do not force latest Google/AppLovin SDK from the app.

## Decisions

- Target SDK wrapper: `com.github.royt93:AdmobApplovinWrapper:1.1.5`.
- Runtime provider priority: AppLovin MAX primary. AdMob remains fallback only; keep `BuildConfig.IS_ENABLE_ADMOB=false`.
- Current app dependency: `com.github.royt93:AdmobWrapper:1.1.2`.
- Current app forced Google Mobile Ads SDK: `23.6.0`.
- Latest checked SDKs:
  - `_AdmobWrapper`: local git tag `1.1.5`.
  - Google Mobile Ads SDK: `25.3.0` from Google Maven / official release notes.
  - AppLovin Android SDK: `13.6.3` from Maven Central metadata.
- Migration rule: consume wrapper `1.1.5` first. Do not force app-level Google Ads `25.3.0` or AppLovin `13.6.3` unless `_AdmobWrapper` is upgraded and verified separately.

## Supplied IDs and config

| Field | Value |
|---|---|
| AppLovin SDK key | `e75FnQfS9XTTqM1Kne69U7PW_MBgAnGQTFvtwVVui6kRPKs5L7ws9twr5IQWwVfzPKZ5pF2IfDa7lguMgGlCyt` |
| AppLovin banner ID | `d3455cc529985b25` |
| AppLovin interstitial ID | `a48241ebcb20ad5c` |
| AppLovin app open ID | `8239a7fd6896cf1f` |
| AppLovin rewarded ID | `87a4f5696dfd4212` |
| AdMob banner ID | existing debug/release values in `app/build.gradle.kts` |
| AdMob interstitial ID | existing debug/release values in `app/build.gradle.kts` |
| AdMob app open ID | existing debug/release values in `app/build.gradle.kts` |
| AdMob rewarded ID | AdMob test rewarded ID `ca-app-pub-3940256099942544/5224354917` |
| Privacy Policy URL | `https://loitp.notion.site/Term-Privacy-Policy-Disclaimer-319b1cd8783942fa8923d2a3c9bce60f` |
| VIP key 30 days | `9fA0q7eN!27cLx04@21993Y2u0I7#Q0` |
| VIP key 3 days | `eQ7@93L0f!2Y2707xN04021993u0I#2aK` |

VIP keys must be compiled as Base64-obfuscated constants, not plain strings.

## API surface summary

`AdManager` is the wrapper facade. App code sets `AdSdkConfig`, calls `initialize`, then uses `requestConsentInfoUpdate`, `awaitSplashComplete`, `loadBanner`, `loadInterstitial`, `showInterstitial`, `loadRewarded`, `showRewarded`, and VIP helpers. Wrapper `1.1.5` owns provider selection, App Open lifecycle through `ProcessLifecycleOwner`, banner auto lifecycle, listener cleanup, frequency/safety limits, UMP consent, rewarded, VIP by key, and first-install 1-day VIP trial.

## Current audit

| Question | Answer | Evidence |
|---|---|---|
| Project already applies target wrapper? | Yes. It uses `AdmobApplovinWrapper:1.1.5`. | `app/build.gradle.kts` dependency |
| Any old SDK/manual manager still active? | No old local `AdMobManager` found. A commented legacy AppLovin helper remains but is not active. | `app/src/main/java/com/mckimquyen/watermark/utils/Applovin.kt` comments |
| Application init? | `MyApplication.setupAdmob()` builds full `AdSdkConfig`, calls `setConfig`, then wrapper `initialize`. | `MyApplication.kt` |
| Splash consent/App Open warmup? | Yes. `SplashActivity` calls `requestConsentInfoUpdate`; if ads can be requested it runs `awaitSplashComplete`, otherwise it navigates to main. | `SplashActivity.kt` |
| Banner? | `AboutActivity` loads adaptive banner into `layoutAdBanner.bannerContainer`; wrapper auto-manages lifecycle. | `AboutActivity.kt` |
| Interstitial? | `AboutActivity.finish()` and post-save in `MainActivity` call `showInterstitial`. | `AboutActivity.kt`, `MainActivity.kt` |
| VIP screen? | Present as standalone `VipManagementActivity`, with About row and main toolbar crown entry points. | `feature/vip`, `a_about.xml`, `menu.xml` |

## Touchpoint table

| Slot | File hiện tại | Trigger | API mới | API cũ thay thế |
|---|---|---|---|---|
| Banner | `AboutActivity` | `onCreate` after view init | `AdManager.loadBanner(autoManageLifecycle=true)` | `loadBanner` + manual `bannerResume/bannerPause/bannerDestroy` |
| Interstitial | `AboutActivity` | Back/finish from About | `AdManager.showInterstitial` | existing `showInterstitial`, keep trigger |
| Interstitial | `MainActivity` | after save job finished, delayed 800ms | `AdManager.showInterstitial` | existing `showInterstitial`, keep trigger |
| App Open | `SplashActivity` + app foreground resume | consent resolved, splash warmup, resume by wrapper lifecycle | `requestConsentInfoUpdate` + `awaitSplashComplete` | `initSplashScreen` + manual lifecycle registration |
| Rewarded | New `VipManagementActivity` | user taps "Watch ad - 3 days VIP" | `loadRewarded` + `showRewarded`, interstitial fallback only if wrapper supports required callback path | `(chua co)` |
| VIP entry | `AboutActivity`/settings | user taps VIP row | start `VipManagementActivity` | `(chua co)` |
| VIP entry | `MainActivity` | crown icon on main screen | start `VipManagementActivity` | `(chua co)` |

## Step-by-step migrate plan

### 1. Setup

- Edit `app/build.gradle.kts`.
- Replace `implementation("com.github.royt93:AdmobWrapper:1.1.2")` with `implementation("com.github.royt93:AdmobApplovinWrapper:1.1.5")`.
- Add `ADMOB_REWARDED_ID`, `APPLOVIN_REWARDED_ID`, and `PRIVACY_POLICY_URL` `buildConfigField`.
- Keep `IS_ENABLE_ADMOB=false` for AppLovin primary runtime.
- Remove or relax `force("com.google.android.gms:play-services-ads:23.6.0")` if it conflicts with wrapper `1.1.5`.

### 2. App init and consent

- Edit `MyApplication.kt`.
- Add all ad IDs, `applovinSdkKey`, `vipKeySecret`, and `safety` to `AdSdkConfig`.
- Replace the current manual provider init block with wrapper `AdManager.initialize(this)`.
- Use debug-friendly `AdSafetyLimits.TEST` or explicit relaxed safety only when `BuildConfig.DEBUG`; release must use default limits.
- Keep crash recovery and `CMonet.init` behavior intact.

### 3. Splash

- Edit `SplashActivity.kt`.
- Replace `initSplashScreen` with consent-first flow:
  - `AdManager.requestConsentInfoUpdate`.
  - `AdManager.awaitSplashComplete`.
  - `goToMain()`.
- Do not add a second foreground lifecycle observer in app code; wrapper owns App Open resume.

### 4. VIP Activity

- Add `VipManagementActivity` under `feature/vip`.
- Add `VipKeys.kt` for Base64-obfuscated 30-day and 3-day key validation.
- Add `VipPrefs.kt` for app-owned flags: `pref_user_redeemed_key_at_least_once`, granted timestamp if needed for UI.
- Register Activity in manifest.
- UI requirements:
  - Standalone Activity, edge-to-edge safe.
  - Style must match current app glass/dynamic-color visual language.
  - Show current VIP status, expiry, grace label for first-install gift, key input, redeem action, watch rewarded for 3 days, privacy footer.
  - No fake close ad UI and no ad-click encouragement.

### 5. VIP entry points

- Settings/About touchpoint: add a VIP row/action in `AboutActivity` using existing settings screen style.
- Main screen touchpoint: add crown icon in `LaunchView`/main toolbar area and wire it from `MainActivity` to start `VipManagementActivity`.

### 6. Banner

- Edit `AboutActivity.kt`.
- Keep existing banner container.
- Use wrapper auto-managed banner lifecycle.
- Remove manual `bannerResume`, `bannerPause`, and `bannerDestroy` if no mid-lifecycle destroy is needed.

### 7. Interstitial

- Keep the existing two triggers only.
- Do not add any new interstitial trigger.
- Trust `AdSafetyLimits` for frequency cap.

### 8. Rewarded

- Load rewarded only in `VipManagementActivity`.
- Show rewarded only after user taps the watch-ad button.
- On earned reward, activate/grant 3-day VIP path and persist "user redeemed/watched" state needed by UI.
- Use AdMob rewarded test ID only for fallback config, not active runtime while `IS_ENABLE_ADMOB=false`.

## Before / after

| Area | Before | After |
|---|---|---|
| Provider | AppLovin via old wrapper path, AdMob switch possible | AppLovin primary via `AdmobApplovinWrapper:1.1.5`, AdMob fallback disabled by flag |
| Dependency | `AdmobWrapper:1.1.2` | `AdmobApplovinWrapper:1.1.5` |
| Init | App manually initializes MobileAds/AppLovinSdk | Wrapper `initialize` owns provider init |
| App Open | `initSplashScreen` + app registers lifecycle | consent-first splash + wrapper lifecycle |
| Consent | Not explicit in Splash | UMP request before ad warmup/load |
| Banner lifecycle | Manual resume/pause/destroy | SDK auto lifecycle |
| Interstitial | Existing two triggers | Same triggers, newer safety caps |
| Rewarded | Not present | VIP Activity user-click only |
| VIP | Not present | Key-based VIP, rewarded 3 days, first-install 1-day grace |
| Memory risk | Manual banner references in Activity | SDK-owned lifecycle and cleanup |
| Policy posture | Partial | Consent, App Open splash gating, rewarded user click, cap documented |

## Information still open

- Confirm required build tasks. Assumption: `./gradlew :app:assembleAppTestDebug` and `./gradlew :app:assembleAppReleaseRelease`.
- Optional: test device IDs for AdMob debug/test-device config.
- Optional: EEA test geography override plan for consent QA.

## Verification plan

- Run `./gradlew :app:assembleAppTestDebug`.
- Run `./gradlew :app:assembleAppReleaseRelease`.
- Manual code review:
  - Banner container does not overlay interactive content.
  - Splash does not show App Open before consent/warmup completion.
  - Rewarded only fires from VIP button click.
  - No manual duplicate App Open lifecycle observer.
  - Activity/banner/rewarded callbacks do not hold destroyed Activity references.
