# AdMob/UMP Release Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build repeatable automated and device verification for the currently shipped AdMob + UMP + rewarded VIP flows, with explicit evidence limits and no false claim of universal coverage.

**Architecture:** Keep production ad integration unchanged unless a failing test proves a defect. Use existing MockK/Robolectric tests for app orchestration; use real wrapper `1.8.5` on the locked Pixel for UMP/network/lifecycle integration. Separate app-owned UI screenshots from ad rendering, and treat leak/performance profiling as reported measurements, not policy proof. IAP and mediation remain explicitly N/A for current source.

**Tech Stack:** Kotlin, existing JUnit/Truth/MockK/Robolectric, Android instrumentation runner, AdmobApplovinWrapper `1.8.5`, ADB on locked Pixel 7 Pro `2B051FDH3006MU`, Android Studio/Perfetto tools if available. Do not add dependencies without separate approval.

## Global Constraints

- Keep production wrapper version `1.8.5` and current provider selection unchanged.
- `IS_ENABLE_ADMOB=true` is current release configuration; do not test against production ad units or click ads.
- Use Google UMP test geography and a registered test device for consent-flow tests.
- Device commands must target only locked serial `2B051FDH3006MU`; verify it is still connected before testing. Do not switch devices without explicit user request.
- Record each case as `PASS`, `FAIL`, `NOT TESTED`, or `N/A`, with evidence and environment.
- Do not snapshot ad creatives; golden/screenshot tests cover app-owned UI only.
- Never mark unrun device, account, network, profile, IAP, or mediation cases as passing.
- Dispose/cancel test Activities, callbacks, jobs and listeners; do not add production abstractions solely for tests.
- IAP/Billing and mediation remain `N/A` until actual app integrations/release configurations exist.

---

## File Map

| File | Responsibility |
|---|---|
| `app/src/test/java/com/mckimquyen/watermark/ui/MainActivityInterstitialRoboTest.kt` | Existing share-intent and ad-trigger Robolectric coverage; add explicit event ordering and all share entry modes. |
| `app/src/test/java/com/mckimquyen/watermark/ui/SplashConsentFlowRoboTest.kt` | Splash consent callback ordering/failure behavior. |
| `app/src/test/java/com/mckimquyen/watermark/ui/about/AboutActivityAdPrivacyRoboTest.kt` | Privacy-options UI and click contract. |
| `app/src/test/java/com/mckimquyen/watermark/feature/vip/VipManagementActivityRewardedRoboTest.kt` | Reward callback and VIP grant behavior; create if no existing focused test is found. |
| `app/src/test/java/com/mckimquyen/watermark/ui/AdConsentEventRecorder.kt` | Test-only event recorder/seam shared by tests if a small helper materially reduces duplication; otherwise keep recorder local to each test. |
| `app/src/androidTest/java/com/mckimquyen/watermark/ads/AdConsentDeviceIntegrationTest.kt` | Real wrapper + device consent/share ordering and diagnostics. |
| `app/src/androidTest/java/com/mckimquyen/watermark/ads/AdLifecycleInterruptionIntegrationTest.kt` | Device lifecycle/interruption cases when controllable with current app/UMP test setup. |
| `app/src/androidTest/java/com/mckimquyen/watermark/ads/AdNetworkRecoveryIntegrationTest.kt` | Offline/reconnect checks and state evidence; use existing device/network tooling only. |
| `app/src/test/java/com/mckimquyen/watermark/ui/about/AboutActivityAdPrivacyGoldenTest.kt` | Create only if existing approved snapshot mechanism is found; otherwise document screenshot baseline as `NOT TESTED`, do not add dependency. |
| `app/src/androidTest/java/com/mckimquyen/watermark/ads/AdUiScreenshotTest.kt` | Optional app-owned screenshot evidence, only if existing Android test infrastructure supports deterministic capture. |
| `docs/superpowers/specs/2026-10-15-ad-ump-release-test-design.md` | Source-of-truth design, limits and test matrix. |
| `doc/feature.md` | Update AD-02 status only when test implementation starts/finishes. |
| `docs/superpowers/plans/2026-10-15-ad-ump-release-tests.md` | This execution plan. |

No production Kotlin file should change in this plan unless a regression test demonstrates a concrete defect. If a code fix becomes necessary, stop and create a focused fix plan/task after root-cause confirmation.

## Task 1: Audit and strengthen share/consent event-order tests — completed

**Result:** Added Robolectric coverage for cold share consent allow/deny, restored share, and `onNewIntent` preload ordering. `MainActivity` now waits for UMP callback and preloads only when `canRequestAds=true` on shared-image paths. Ordinary launcher preload stays on its existing post-Splash path. TDD RED observed before production change. Full unit suite passes; EEA UMP form/device result remains `NOT TESTED`.

## Task 1: Audit and strengthen share/consent event-order tests

**Files:**
- Modify: `app/src/test/java/com/mckimquyen/watermark/ui/MainActivityInterstitialRoboTest.kt`
- Modify: `app/src/test/java/com/mckimquyen/watermark/ui/SplashConsentFlowRoboTest.kt`
- Optional create: `app/src/test/java/com/mckimquyen/watermark/ui/AdConsentEventRecorder.kt`

**Interfaces:**
- Consumes: `AdManager.requestConsentInfoUpdate`, `AdManager.loadInterstitial`, current Robolectric test setup.
- Produces: explicit, deterministic assertions for call sequence and user-visible continuation; no production API change.

- [ ] **Step 1: Inspect existing tests and call sites; identify exact ordering boundary.**

Read `MainActivity.kt` around `onCreate`, `onNewIntent`, `requestConsentForSharedImage`; read current tests. Confirm whether SDK static methods are mockable in test and record current baseline behavior. Do not assert that a wrapper method call equals a network request.

- [ ] **Step 2: Add failing ordering assertions for cold share and `onNewIntent`.**

Use a local ordered event list in test mocks. Record `loadInterstitial` and `requestConsentInfoUpdate` invocation. Assert the source’s intended orchestration and separately document that wrapper consent gating is validated at device layer. For callbacks, invoke captured consent callback with both `true` and `false` and assert flow does not remain blocked. Do not hard-code a desired order that current approved design does not require; the invariant is that no app-controlled ad display/request path bypasses wrapper consent gate.

- [ ] **Step 3: Run focused tests and establish failure/diagnostic value.**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.mckimquyen.watermark.ui.MainActivityInterstitialRoboTest' --tests 'com.mckimquyen.watermark.ui.SplashConsentFlowRoboTest'`

Expected: baseline existing assertions pass; newly added ordering assertions must fail if mocked call sequence violates the explicitly documented invariant. If no deterministic ordering can be tested without changing production design, remove the artificial assertion and mark wrapper-level behavior for device integration instead.

- [ ] **Step 4: Keep minimal test-only implementation and rerun.**

Add only the test recorder/callback capture needed for meaningful assertions. No new production seam unless user approves a separate design.

Run same command. Expected: all focused tests pass and verify share/non-share/cold-start/new-intent behavior.

- [ ] **Step 5: Record coverage in the spec.**

Update section 4.1/7 with actual test names and whether each invariant is app-level or SDK-level. Do not claim provider/network behavior from Robolectric mocks.

## Task 2: Test rewarded VIP and privacy options UI contracts

**Files:**
- Modify: `app/src/test/java/com/mckimquyen/watermark/ui/about/AboutActivityAdPrivacyRoboTest.kt`
- Create or modify: `app/src/test/java/com/mckimquyen/watermark/feature/vip/VipManagementActivityRewardedRoboTest.kt` (first locate any existing equivalent; avoid duplicate class coverage)

**Interfaces:**
- Consumes: `VipManagementActivity.watchRewarded`, `grantRewardedVipBy`, `AboutActivity.rowAdPrivacy`.
- Produces: evidence that consent options remain reachable and reward grants occur only on `earned=true`.

- [ ] **Step 1: Find existing rewarded activity tests and existing mock patterns.**

Search the test tree for `VipManagementActivity`, `showRewarded`, `grantVipDays`, and `btnWatchRewarded`. Reuse repository conventions; do not add another test class if a focused class already covers these branches.

- [ ] **Step 2: Add tests for earned and not-earned results before touching app code.**

Capture callback passed to `AdManager.showRewarded`. Assert `grantVipDays` is called exactly once only for `earned=true`, and zero times for `false`; assert UI button returns to usable state and no VIP success is reported on false. Ensure the Activity is destroyed in `finally`/controller cleanup.

- [ ] **Step 3: Run the focused VIP test.**

Run: `./gradlew :app:testDebugUnitTest --tests '*VipManagementActivityRewardedRoboTest'`

Expected: test compiles and fails only where current behavior violates stated contract. If Robolectric cannot instantiate Hilt/AdManager dependencies without broad scaffolding, use the narrowest existing test mechanism; do not introduce a new test framework.

- [ ] **Step 4: Add privacy-options failure/availability tests.**

For `AboutActivityAdPrivacyRoboTest`, cover SDK returns `true` and `false` from `showConsentFormIfAvailable`; verify row is still reachable/clickable and app does not assume that `false` means consent has been collected. Test callback path only if the public API exposes a callback in the dependency signature.

- [ ] **Step 5: Run focused tests and full debug unit suite.**

Run focused VIP/privacy commands, then `./gradlew :app:testDebugUnitTest`.

Expected: focused tests pass; full suite has zero failures. Report any unrelated/flaky failures verbatim and do not label full suite green.

## Task 3: Verify merged release/debug configuration and test safeguards

**Files:**
- Modify: `app/src/androidTest/java/com/mckimquyen/watermark/ads/AdConfigIntegrationTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/watermark/MyApplication.kt` only if a verified config defect is found (otherwise no source change).
- Inspect: `app/src/main/AndroidManifest.xml`, generated merged manifest/build config outputs.

**Interfaces:**
- Consumes: existing build config, UMP debug-only config contract, real SDK `1.8.5`.
- Produces: device-readable proof that debug test setup is isolated and test IDs are registered.

- [ ] **Step 1: Add static/config assertions that use existing public configuration.**

Verify debug uses Google sample ad units, all configured test device IDs are valid, and UMP debug geography/test IDs are not included in release configuration. Prefer generated `BuildConfig`/merged manifest inspection over brittle source-text checks.

- [ ] **Step 2: Run unit config tests.**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.mckimquyen.watermark.ads.*'`

Expected: all app ads config unit tests pass.

- [ ] **Step 3: Build debug and release candidate configuration.**

Run: `./gradlew assembleDebug`; then inspect the generated merged manifest and `BuildConfig` for the release variant without printing secret values. Release build requires private signing/config values; do not expose them in terminal output or report. If release build cannot run, mark release-variant config inspection `NOT TESTED` and state why.

- [ ] **Step 4: Verify test inventory safely.**

On-device ad rendering may run only with Google test ad units or network test mode. Never click an ad. Record ad label/test-device status; if no fill or no visible test label, stop interacting with the ad and mark the case `NOT TESTED`.

## Task 4: Device consent/share integration on locked Pixel — partial

**Result:** `ShareIntentManifestIntegrationTest` passed 2/2 and `AdConfigIntegrationTest` passed 10/10 on locked Pixel 7 Pro `2B051FDH3006MU`. Manual EEA Accept and Deny forms completed; after Deny, wrapper preload callback returned `SafetyBlocked(Consent not granted)`. Manual choice was user-driven, not automated. A separate HTTP traffic capture was not done.

## Task 4: Device consent/share integration on locked Pixel

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/watermark/ads/AdConsentDeviceIntegrationTest.kt`
- Modify: `app/src/androidTest/java/com/mckimquyen/watermark/ads/ShareIntentManifestIntegrationTest.kt` only if existing manifest test can be extended without duplicating scope.

**Interfaces:**
- Consumes: actual app APK, actual cached/resolved wrapper AAR `1.8.5`, UMP test geography, device `2B051FDH3006MU`.
- Produces: logs/screenshots/test output with consent → provider init → ad load ordering for launcher and share entry.

- [ ] **Step 1: Create instrumentation test skeleton with cleanup.**

Use `AndroidJUnit4`, existing `ApplicationProvider`/runner dependencies, and test-scoped UMP configuration. Each test must restore/clear test configuration where public APIs permit and never leave test-only flags persisted.

- [ ] **Step 2: Add device test for cold share entry before consent decision.**

Launch the explicit `ACTION_SEND` intent through the same exported activity route. Assert `MainActivity` handles the image and collect public SDK diagnostics/log markers to distinguish consent not ready from provider init and actual ad loading. Do not assert based on `loadInterstitial()` call alone.

- [ ] **Step 3: Add EEA test-geography accept and deny cases.**

Use only SDK-supported debug geography and registered test device. Capture expected form/result and SDK diagnostics. Do not automate taps based on guessed coordinates; use available UI automation/accessibility hierarchy and inspect screenshot first. If UMP test form is unavailable, stop and report `NOT TESTED` rather than bypassing.

- [ ] **Step 4: Add privacy options/re-consent path.**

Navigate to About and tap the app-owned privacy row; verify SDK form opens when available and that closing/canceling returns to app. Use current app language/device state; do not assume exact Google-owned copy in assertions.

- [ ] **Step 5: Run instrumentation only on locked serial.**

Before any device command, verify `adb devices -l`; target only `2B051FDH3006MU`. Use `ANDROID_SERIAL=2B051FDH3006MU ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mckimquyen.watermark.ads.AdConsentDeviceIntegrationTest`.

Expected: all deterministic cases pass. Include exact test count. If UMP region/test form or device state prevents a case, report separately as `NOT TESTED`.

- [ ] **Step 6: Collect redacted evidence.**

Save test report and selected log lines without GAID, user/image URI, or other identifiers. Include app version, SDK version, device/API, and ordering evidence. Do not save or publish unredacted diagnostic output.

## Task 5: Interruption and lifecycle integration — NOT TESTED

Pixel interruptions during UMP/rewarded are not executed; do not mark lifecycle interruption coverage passed.

## Task 5: Interruption and lifecycle integration

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/watermark/ads/AdLifecycleInterruptionIntegrationTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/watermark/utils/AppOpenSuppressor.kt` only if test proves defect and after root-cause review.

**Interfaces:**
- Consumes: Android Activity lifecycle, UMP consent form, App Open suppression API and reward callback behavior.
- Produces: device evidence for background/foreground, Activity recreation and cancellation without stale Activity access or ad overlay.

- [ ] **Step 1: Select interruption cases that instrumentation can reliably trigger.**

Cover app background/foreground, Activity recreation while consent is pending, back/cancel from consent form, and return from system picker if deterministic. Exclude process-kill reward completion unless a deterministic callback can be controlled; report that case `NOT TESTED` if provider cannot produce it safely.

- [ ] **Step 2: Add tests with explicit cleanup and bounded waits.**

Use lifecycle APIs/IdlingResource or observable state where available; do not use arbitrary sleeps as the only success condition. Ensure test ends in app foreground and no form/ad remains over UI.

- [ ] **Step 3: Run instrumentation on locked Pixel.**

Use the same `ANDROID_SERIAL=2B051FDH3006MU` runner class filter. Expected: no crash, no app-owned UI access after Activity destruction, and no App Open showing over consent/system flow. Report unautomatable manual steps as `NOT TESTED`.

## Task 6: Network failure and recovery integration — partial

**Result:** Pixel offline cold start manual test passed behaviorally: on Pixel `2B051FDH3006MU`, airplane mode + Wi‑Fi/mobile disabled, `dumpsys connectivity` reported `Active default network: none`. UMP returned request error code 2 and `canRequestAds=false`; Splash continued without App Open, MainActivity entered, interstitial logged `consent chưa cho phép`, and wrapper logged retry waiting for validated network. Wi‑Fi/mobile were restored after the case; validated default network returned. Reconnect retry completing successfully was not verified. No S24 Ultra or TECNO commands were issued for this test.

## Task 6: Network failure and recovery integration

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/watermark/ads/AdNetworkRecoveryIntegrationTest.kt` only if instrumentation can set/observe network deterministically.
- Modify: none by default.

**Interfaces:**
- Consumes: Android connectivity state, wrapper retry behavior, real UMP/network callbacks.
- Produces: reproducible offline/failure/recovery evidence with actual network conditions.

- [ ] **Step 1: Check available network-conditioning tooling and permissions.**

Determine whether existing device/lab tooling can disable connectivity or shape network. Do not install a VPN/shaper or change global network state without user approval. If deterministic control is unavailable, classify network cases as manual device tests and retain logs.

- [ ] **Step 2: Implement only deterministic instrumentation assertions.**

Cover offline cold start and validated network recovery if test runner can control state. Distinguish no connectivity from connectivity without validated internet. Assert app continues and wrapper does not bypass consent; do not assert ad fill because remote inventory is nondeterministic.

- [ ] **Step 3: Execute and record each condition.**

Run on locked Pixel. Record Wi-Fi/cellular/validated connectivity, timestamps, consent callback, provider init, retry and final UI. Expected: no hang, no duplicate init, and no ad load before SDK consent allows it. If network condition cannot be reproducibly controlled, mark `NOT TESTED`.

## Task 7: App-owned UI golden/widget verification

**Files:**
- Modify: `app/src/test/java/com/mckimquyen/watermark/ui/about/AboutActivityAdPrivacyRoboTest.kt`
- Create `AboutActivityAdPrivacyGoldenTest.kt` only if an approved snapshot framework or stable existing screenshot mechanism is already present.
- No new dependency by default.

**Interfaces:**
- Consumes: About layout, current Robolectric/widget tests, theme resources.
- Produces: deterministic accessibility/visual assertions for app-owned privacy options UI.

- [ ] **Step 1: Inspect project test dependencies and CI for existing golden support.**

Check `app/build.gradle.kts`, source sets and CI configuration. If no approved snapshot solution exists, do not add a framework; mark golden as `NOT TESTED` and use widget assertions plus a manually reviewed device screenshot.

- [ ] **Step 2: Add widget assertions.**

Verify privacy options row exists, is visible/clickable/accessibility-labelled, and remains usable under relevant theme/locale fixture. Avoid relying only on color/pixel equality.

- [ ] **Step 3: Add deterministic app-owned screenshot only if infrastructure already supports it.**

Replace banner content with a static placeholder or hide network-loaded banner for screenshot. Pin API/theme/locale/font scale. Never capture ad creative as a golden.

- [ ] **Step 4: Run focused tests and inspect screenshot diffs.**

Run: `./gradlew :app:testDebugUnitTest --tests '*AboutActivityAdPrivacy*'`

Expected: assertions pass and any accepted golden baseline is reviewed. A screenshot diff requires human review; do not auto-update baseline to silence a failure.

## Task 8: Leak/profile and frame/jank measurement

**Files:**
- Inspect: `settings.gradle.kts`, `baseBenchmarks/`, `macrobenchmark/`, `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`.
- Create/modify benchmark files only if module can be enabled without changing production delivery artifact or adding unapproved dependencies.
- Evidence output: `docs/superpowers/reports/` only if the user wants checked-in reports; otherwise provide terminal/report links without committing sensitive traces.

**Interfaces:**
- Consumes: profileable app build, Android Studio/Perfetto/Macrobenchmark tooling, locked Pixel.
- Produces: reproducible metadata and measurements; no policy pass/fail inference.

- [ ] **Step 1: Verify benchmark module status and existing profileable manifest behavior.**

Confirm whether benchmark modules remain disabled and whether profileable config exists only in intended build variant. Do not enable a production-manifest flag without reviewing release impact.

- [ ] **Step 2: Establish a baseline before selecting regression thresholds.**

Measure cold start, About open, VIP open and resume after share/picker at least 10 times on the same device/API/build mode. Record frame duration/overrun p50/p90/p95, jank count, thermal/battery state and run count. Do not choose a threshold before baseline data.

- [ ] **Step 3: Run leak-retention cycles.**

Repeat open/close About, VIP, share and consent test flow at least 20 times in a profile run. Capture heap/Perfetto evidence and inspect whether destroyed Activity/Window/Dialog/listeners remain retained. If Android Studio Profiler is unavailable in this session, mark profiling `NOT TESTED`; do not replace it with an unsupported assertion.

- [ ] **Step 4: Add a benchmark module only after a separate setup review.**

If existing AndroidX Macrobenchmark dependencies/module can be safely enabled, propose exact Gradle/config diff and ask approval before adding dependencies or changing settings. No code change by default in this task.

## Task 9: Update feature status and final evidence report

**Files:**
- Modify: `docs/superpowers/specs/2026-10-15-ad-ump-release-test-design.md`
- Modify: `doc/feature.md`
- Optional create: `docs/superpowers/reports/2026-10-15-ad-ump-release-test-results.md` only if a shareable report is requested.

**Interfaces:**
- Consumes: actual test results and device evidence from Tasks 1–8.
- Produces: accurate AD-02 status and explicit release test matrix.

- [ ] **Step 1: Update each matrix row with actual status and evidence.**

Use only `PASS`, `FAIL`, `NOT TESTED`, `N/A`. Include command/test name, environment, device/API, SDK version, and redacted log/trace location as applicable.

- [ ] **Step 2: Update AD-02 in `doc/feature.md`.**

Move from `📋 Picked` to `🟡 In progress` only when implementation begins; move to `✅ Implemented` only when agreed automated tests and device gates pass. Do not claim every case was covered.

- [ ] **Step 3: Run final verification.**

Run: `./gradlew :app:testDebugUnitTest`
Run: `./gradlew assembleDebug`
Run instrumentation for every supported deterministic device test on locked serial.
Run: `git diff --check`

Expected: each command's exit status and test count recorded. Report failures and unrun gates without omission.

## Execution Order and Stop Conditions

1. Tasks 1–3 (fast app/config test work) establish what can be proven without remote services.
2. Tasks 4–6 (device/UMP/network) require actual test geography/device state; stop if a full-screen ad covers UI, per project device-test rule, and wait for user confirmation before continuing.
3. Task 7 depends on existing golden infrastructure; if absent, do not add dependency without approval.
4. Task 8 requires profiler/module feasibility review; do not fabricate measurements or enable production-impacting profile config without approval.
5. Task 9 closes only after evidence is collected.

If a task reveals a production defect, stop the test-plan execution at that finding. Preserve failing reproduction, trace root cause, and propose a separate minimal fix with its own regression test before modifying production code.

## Explicit Non-Goals

- No IAP test implementation until BillingClient/purchase flow exists.
- No mediation claim/test until adapter/waterfall is part of shipped configuration and test inventory is available.
- No legal sign-off, dashboard reconfiguration, Data Safety submission, or privacy-policy text audit.
- No production ad clicks, real-user data collection, unapproved SDK/dependency upgrades, commits, pushes, or release deployment.
- No promise that these tests prove all devices, jurisdictions, network paths or future SDK behavior.
