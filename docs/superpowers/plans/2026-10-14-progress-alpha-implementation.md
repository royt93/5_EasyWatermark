# ProgressImageView and Alpha Precision Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cancel `ProgressImageView` animation on detach and preserve alpha slider percentage through native-alpha persistence conversion.

**Architecture:** Implement BUG-82 first with a lifecycle override that cancels the existing animator and leaves all other state transitions unchanged. Then update `AlphaPbFragment` to use nearest-integer conversion in both directions; verify conversion at pure-function, widget, and Android instrumentation levels using a test host with a real `MainViewModel` and a separately constructed `WaterMarkRepository` backed by isolated DataStore.

**Tech Stack:** Kotlin, Android View system, Robolectric, AndroidX instrumentation (`AndroidJUnit4`, `ActivityScenario`), Preferences DataStore, Truth, Gradle.

## Global Constraints

- Follow repository `CLAUDE.md`: Kotlin/ViewBinding idioms, Vietnamese comments, null-safe code, resource cleanup, and unit/widget/integration tests for applicable behavior.
- TDD: each behavior test must fail for the expected reason before production change, then pass after minimal fix.
- Pixel 7 Pro is locked for this session: `2B051FDH3006MU`; use only `adb -s 2B051FDH3006MU` for device operations.
- If a screenshot-based smoke test shows an ad obscuring UI, stop immediately and wait for user confirmation before continuing.
- Do not alter alpha range (0..100 slider, 0..255 native), persistence schema, unrelated panels, animation durations, or production dependencies.
- Do not commit or modify `.idea/caches/deviceStreaming.xml`.
- Keep each ticket independently testable and record actual test/smoke evidence before moving it to `done/`.

---

## File Structure

- Modify `app/src/main/java/com/mckimquyen/watermark/ui/widget/ProgressImageView.kt`: cancel `startAnimator` in detach lifecycle.
- Create `app/src/test/java/com/mckimquyen/watermark/ui/widget/ProgressImageViewLifecycleRoboTest.kt`: unit assertions for start/failure/reset animator state and Activity-attached Robolectric detach test.
- Create `app/src/androidTest/java/com/mckimquyen/watermark/ui/widget/ProgressImageViewLifecycleIntegrationTest.kt`: real Android Activity/View detach test.
- Modify `app/src/main/java/com/mckimquyen/watermark/ui/panel/AlphaPbFragment.kt`: use rounded conversion in both directions.
- Create `app/src/test/java/com/mckimquyen/watermark/ui/panel/AlphaPbFragmentConversionTest.kt`: pure unit conversion and boundary tests.
- Create `app/src/test/java/com/mckimquyen/watermark/ui/panel/AlphaPbFragmentConversionWidgetTest.kt`: drive the actual panel slider in a Robolectric host.
- Create `app/src/androidTest/java/com/mckimquyen/watermark/ui/panel/AlphaPbFragmentIntegrationTest.kt`: real Activity + isolated Preferences DataStore + `WaterMarkRepository` round-trip.
- Update `doc/task/done/BUG-82-progressimageview-cancel-animator-on-detach.md`, `doc/task/done/ENH-48-alpha-slider-roundtrip-loss-one-percent.md`, and `doc/task/BACKLOG.md` only after verification.

## Task 1: BUG-82 — cancel `ProgressImageView` animator on detach

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/watermark/ui/widget/ProgressImageView.kt`
- Create: `app/src/test/java/com/mckimquyen/watermark/ui/widget/ProgressImageViewLifecycleRoboTest.kt`
- Create: `app/src/androidTest/java/com/mckimquyen/watermark/ui/widget/ProgressImageViewLifecycleIntegrationTest.kt`
- Move: `doc/task/todo/BUG-82-progressimageview-cancel-animator-on-detach.md` → `doc/task/inprogress/`

**Interfaces:**
- Existing public API remains unchanged: `start()`, `finish(animate)`, `failed()`, `ready()`.
- Tests observe the private lazy animator using reflection, matching `WaterMarkImageViewLifecycleRoboTest`; do not add production testing accessors.

- [ ] **Step 1: Move ticket and write failing Robolectric widget test.**

Create `ProgressImageViewLifecycleRoboTest` with `RobolectricTestRunner`. Build an Activity via `Robolectric.buildActivity(Activity::class.java).create().start().resume().visible().get()`. Create `ProgressImageView(ContextThemeWrapper(activity, R.style.Theme_MyApp))`, add it to `android.R.id.content` with `ViewGroup.LayoutParams(200, 200)`, idle the main looper, and assert `isAttachedToWindow`. Reflect `ProgressImageView::class.java.getDeclaredField("startAnimator\$delegate")`, cast to `Lazy<ObjectAnimator>`, obtain the animator, set `duration = 10_000L`, call `view.start()`, assert `isStarted`. Remove view from root, idle main looper, then assert `isStarted` and `isRunning` are false. Before the fix, detach has no cleanup, so this must fail because animator remains active.

- [ ] **Step 2: Run focused test and confirm RED.**

Run: `./gradlew :app:testDebugUnitTest --tests "*ProgressImageViewLifecycleRoboTest" --console=plain`

Expected: detach assertion fails; setup and attachment assertions pass.

- [ ] **Step 3: Add minimal lifecycle cleanup.**

In `ProgressImageView`, add after `ready()`:

```kotlin
override fun onDetachedFromWindow() {
    startAnimator.cancel()
    super.onDetachedFromWindow()
}
```

- [ ] **Step 4: Run focused Robolectric test and confirm GREEN.**

Run the focused Gradle command again. Expected: detach assertion passes.

- [ ] **Step 5: Add unit assertions for explicit animator state transitions.**

In the same test class, add separate tests: `start()` makes the animator started; `failed()` cancels it; `ready()` cancels it. For each, obtain the animator by the same reflection helper and use an Activity-attached view with `duration = 10_000L`, so the 250ms default duration cannot complete before assertions. These guard against regressions in the existing public transitions; no extra production behavior is added.

- [ ] **Step 6: Add Android instrumentation integration test.**

Create `ProgressImageViewLifecycleIntegrationTest` with `AndroidJUnit4`. Launch `ActivityScenario<OpenSourceActivity>` (`OpenSourceActivity` is the existing no-Ad host identified by instrumentation test comments). In `scenario.onActivity`, create a themed `ProgressImageView`, add it to the Activity content root, set animator duration long using reflection before `start()`, then start and assert animator is running. Remove the view from root, assert the animator stopped, and remove any remaining view in `finally`. Close `ActivityScenario` with `use`; no observers or external files are created.

- [ ] **Step 7: Run focused unit/widget and instrumentation tests.**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "*ProgressImageViewLifecycleRoboTest" --console=plain
ANDROID_SERIAL=2B051FDH3006MU ./gradlew :app:connectedDebugAndroidTest --tests "*ProgressImageViewLifecycleIntegrationTest" --console=plain
```

Expected: all Robolectric cases pass and instrumentation report shows `ProgressImageViewLifecycleIntegrationTest` passed on Pixel 7 Pro. If Gradle does not honor class filtering, run the repository's established instrumentation package filter and verify the report names this test.

- [ ] **Step 8: Audit, smoke, record, and commit BUG-82.**

Review the diff for `ProgressImageView.kt` and both new tests. Run `./gradlew :app:ktlintCheck`. Build/install debug on the locked Pixel, enter a non-ad flow that displays export progress, verify normal start/completion and no crash. Stop if a full-screen ad blocks the screen. Record test output, smoke evidence, and audit score in the BUG-82 ticket; update `BACKLOG.md`, move ticket to `done/`, then commit only BUG-82 files using a Conventional Commit.

## Task 2: ENH-48 — round alpha conversions consistently

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/watermark/ui/panel/AlphaPbFragment.kt`
- Create: `app/src/test/java/com/mckimquyen/watermark/ui/panel/AlphaPbFragmentConversionTest.kt`
- Create: `app/src/test/java/com/mckimquyen/watermark/ui/panel/AlphaPbFragmentConversionWidgetTest.kt`
- Create: `app/src/androidTest/java/com/mckimquyen/watermark/ui/panel/AlphaPbFragmentIntegrationTest.kt`
- Move: `doc/task/todo/ENH-48-alpha-slider-roundtrip-loss-one-percent.md` → `doc/task/inprogress/`

**Interfaces:**
- Slider range remains `0f..100f`; model/DataStore alpha remains integer `0..255`.
- Define two `internal` pure conversion functions in `AlphaPbFragment` companion:

```kotlin
internal fun alphaPercentToNative(percent: Float): Int
internal fun nativeAlphaToPercent(alpha: Int): Int
```

- Clamp inputs to their documented ranges, then use `roundToInt()`; `WaterMarkRepository.updateAlpha()` remains the final native alpha persistence guard.

- [ ] **Step 1: Move ticket and write failing unit tests.**

Create tests for percentages `0f`, `1f`, `50f`, `99f`, `100f` and native alpha `0`, `1`, `128`, `252`, `255`. Assert exact conversion results and round-trip percentage error ≤1 because 256 native values cannot represent all 101 slider positions exactly. Assert specifically `99f → 252 → 99`, not 98. Add out-of-range inputs `-1f`, `101f`, `-1`, `256`; assert conversion clamps to the documented ranges before conversion.

- [ ] **Step 2: Run focused unit tests and confirm RED.**

Run: `./gradlew :app:testDebugUnitTest --tests "*AlphaPbFragmentConversionTest" --console=plain`

Expected: compile fails because the conversion functions do not exist yet.

- [ ] **Step 3: Implement rounded conversion and wire Fragment.**

Add `kotlin.math.roundToInt`. Implement both functions with `coerceIn(0f, 100f)` / `coerceIn(0, 255)` followed by scaling and rounding. Replace inline math in `doOnChange()` and `rebuildAlpha()` with these functions. Leave `MainViewModel.updateAlpha()` and repository persistence unchanged.

- [ ] **Step 4: Run focused unit tests and confirm GREEN.**

Run the focused unit-test command again. Expected: all conversion, round-trip, and bound assertions pass.

- [ ] **Step 5: Write failing widget test for actual Slider callback.**

Create `AlphaPbFragmentConversionWidgetTest` with the repository's Robolectric `MainActivity`/`ViewModelProvider` host pattern. Attach `AlphaPbFragment` through `supportFragmentManager.beginTransaction().add(fragment, "alpha-test").commit()` and idle main looper. Read `R.id.slideContentSize` as `Slider`, set value to 99f, and invoke `fragment.doOnChange(slider, 99f, true)`, the same callback the base panel uses for a user change. Wait/idle until host ViewModel WaterMark Flow emits new alpha. Assert alpha is 252 and `fragment.formatValue(updatedWaterMark)` returns 99f. Include a 100f→255 boundary assertion. Before fix, persisted alpha 252 formats as 98%.

- [ ] **Step 6: Run focused widget test and confirm meaningful GREEN.**

Run: `./gradlew :app:testDebugUnitTest --tests "*AlphaPbFragmentConversionWidgetTest" --console=plain`

Expected: all widget assertions pass. Confirm earlier RED was conversion mismatch, not a host/Hilt setup error.

- [ ] **Step 7: Add Android instrumentation integration test using real DataStore.**

Create `AlphaPbFragmentIntegrationTest` with `AndroidJUnit4`. Define nested `TestHostActivity : FragmentActivity` whose `defaultViewModelProviderFactory` returns a test `MainViewModel`, following `GalleryFragmentLifecycleRoboTest.TestHostActivity`. Launch using `ActivityScenario<TestHostActivity>` to avoid app UI/ad startup. Construct isolated DataStores with `PreferenceDataStoreFactory.create(produceFile = { File.createTempFile("alpha_${UUID.randomUUID()}", ".preferences_pb", context.cacheDir) })`. Construct `WaterMarkRepository(context, waterMarkDataStore)` and `UserConfigRepository(userDataStore)`; construct `MainViewModel` with those repos plus `MemorySettingRepo()`, `TemplateRepository(null)`, and `noopWatermarkStyleHistoryRepository()`. Attach `AlphaPbFragment` to the test Activity. On main thread set actual Material Slider to 99f and dispatch its production user-change callback. Assert ViewModel publishes native alpha 252; await `waterMark.first()` off main and assert DataStore persisted 252; recreate/detach and reattach panel with persisted model, assert formatted value is 99. In teardown close/cancel both DataStore scopes and delete both files; close any DB if created. No `observeForever` unless observer is removed.

- [ ] **Step 8: Run instrumentation integration test on locked Pixel.**

Run: `ANDROID_SERIAL=2B051FDH3006MU ./gradlew :app:connectedDebugAndroidTest --tests "*AlphaPbFragmentIntegrationTest" --console=plain`

Expected: test report shows one passing Android instrumentation test and the real round-trip returns 99. If Gradle ignores test-class filters, use the repository instrumentation package filter and confirm test name in report.

- [ ] **Step 9: Run broader verification.**

Run in order:

```bash
./gradlew :app:ktlintCheck
./gradlew :app:testDebugUnitTest
ANDROID_SERIAL=2B051FDH3006MU ./gradlew :app:connectedDebugAndroidTest --tests "*AlphaPbFragmentIntegrationTest" --tests "*ProgressImageViewLifecycleIntegrationTest" --console=plain
```

Expected: all commands pass; unit/instrumentation XML reports contain zero failures and both named instrumented tests.

- [ ] **Step 10: Smoke-test, record, and commit ENH-48.**

Build/install debug on locked Pixel 7 Pro. Choose an image, set alpha to 99%, leave and reopen Alpha panel, confirm label remains 99%. Check 0% and 100% endpoints if reachable without unrelated export. Inspect logcat for `FATAL EXCEPTION`. If an ad obscures screenshot UI, stop and wait for user confirmation. Record exact evidence and audit score, update `BACKLOG.md`, move ENH-48 to `done/`, and commit only ENH-48 files using a Conventional Commit.

## Final verification

- [ ] Re-read both ticket result sections and compare every claim with test/build output.
- [ ] Confirm no new force-unwrap, unclosed DataStore scope, observer, or other resource.
- [ ] Confirm `.idea/caches/deviceStreaming.xml` remains uncommitted.
- [ ] Push implementation commits only to `origin/dev`; do not merge to `master`.
