# Ad Consent and Interstitial Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure share entry paths request UMP consent while removing surprising automatic interstitials after batch export.

**Architecture:** Keep SplashActivity's direct UMP request on each normal launch. MainActivity directly requests UMP on cold ACTION_SEND with an image or a new shared-image onNewIntent; no process-wide gate, so future launches can refresh/re-consent per UMP policy. Remove the batch-finish interstitial callsite and all delay/pending/dialog lifecycle state that only exists for that callsite; retain interstitial preload.

**Tech Stack:** Kotlin, Android Activity lifecycle, AdmobApplovinWrapper, JUnit, MockK, Robolectric, Gradle.

## Global Constraints

- Follow approved design in `docs/superpowers/specs/2026-10-14-ad-consent-and-interstitial-design.md`.
- Preserve existing `AdManager.loadInterstitial(this)` preload.
- Splash UMP request remains callback-awaited; share-intent fallback is asynchronous and does not block UI.
- Do not modify `.idea/caches/deviceStreaming.xml`.
- Run `ktlintCheck`, `lint`, `:app:testDebugUnitTest`, and `:app:testReleaseUnitTest`.

---

### Task 1: Remove post-export interstitial

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/watermark/ui/MainActivity.kt`
- Modify: `app/src/test/java/com/mckimquyen/watermark/ui/MainActivityInterstitialRoboTest.kt`
- Delete: `app/src/test/java/com/mckimquyen/watermark/ui/InterstitialDelayTest.kt`

**Interfaces:**
- Keep `AdManager.loadInterstitial(this)` in `MainActivity.onCreate()`.
- Remove `showInterstitialRunnable`, `interstitialPending`, `saveDialogCallbacks`, `tryShowPendingInterstitial`, `canShowInterstitial`, and `INTERSTITIAL_DELAY_MS` if only used by post-export ad.

- [ ] Remove auto `showInterstitial` scheduling from `saveResult` observer; keep export result handling and preload.
- [ ] Remove obsolete delay/runnable tests; add a testable small decision/helper only if needed to assert `TYPE_JOB_FINISH` does not enqueue an ad. Prefer test through existing Robolectric MainActivity observer, with `AdManager.showInterstitial` mocked and verified never called.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests "*MainActivityInterstitialRoboTest"`.
- [ ] Remove obsolete test files/helpers if no longer meaningful; keep prior BUG-08 coverage only if it tests behavior still present.

### Task 2: Request consent on shared-image entry cold start

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/watermark/ui/MainActivity.kt`
- Modify: `app/src/test/java/com/mckimquyen/watermark/ui/MainActivityInterstitialRoboTest.kt`
- No SplashActivity change: its existing direct UMP request already follows the SDK example.

**Interfaces:**
- No process-wide consent gate: Splash calls UMP on each normal launch; MainActivity calls UMP on cold ACTION_SEND with an image and on new shared-image `onNewIntent`.
- `MainActivity.requestConsentForSharedImage(): Unit` calls `AdManager.requestConsentInfoUpdate(this) { canRequestAds -> ... }` asynchronously; no UI blocking.

- [ ] Add Robolectric tests for cold ACTION_SEND request, new shared-image intent request where practical, and normal launch no-op; run them against actual Activity entry points.
- [ ] Keep SplashActivity's existing direct callback-awaited UMP request unchanged.
- [ ] Confirm MainActivity handles existing singleTask `onNewIntent` and does not request consent for non-image intents.
- [ ] Run focused tests.

### Task 3: Add widget and manifest integration coverage

**Files:**
- Modify: `app/src/test/java/com/mckimquyen/watermark/ui/dlg/SaveImageBSDialogFragmentBatchActionRoboTest.kt`
- Create: `app/src/androidTest/java/com/mckimquyen/watermark/ads/ShareIntentManifestIntegrationTest.kt`

- [ ] Confirm existing widget coverage verifies successful export exposes enabled Share/Share ZIP buttons; extend only if assertion missing.
- [ ] Verify manifest routing through PackageManager query without starting Activity or invoking UMP/ads.
- [ ] Run instrumentation on TECNO only: `ANDROID_SERIAL=115333744A005844 ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mckimquyen.watermark.ads.ShareIntentManifestIntegrationTest --max-workers=1`.

### Task 4: Verify whole change

**Files:**
- No further changes unless tests expose defects.

- [ ] Run `./gradlew ktlintCheck lint :app:testDebugUnitTest :app:testReleaseUnitTest --continue`.
- [ ] Confirm all four tasks succeed and inspect test XML totals for zero failures/errors.
- [ ] Inspect `git diff --check` and `git status`; exclude unrelated `.idea/caches/deviceStreaming.xml`.
- [ ] Commit changes with a concise Vietnamese commit message and Claude attribution. Push to `origin/dev` only after verification; current active GitHub account is `royt93`.
