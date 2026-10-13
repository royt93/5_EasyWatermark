# VIP Token Pending Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Handle SDK-queued device-bound VIP token redemption without reporting pending tokens as invalid, losing late results, or allowing a second token while SDK holds its one pending slot.

**Architecture:** Add an application-scoped pending-result state holder that stores only small state and never captures an Activity. Register the SDK’s global `setPendingVipTokenResultListener` once in `MyApplication` before `initialize`; `VipManagementActivity` observes state and SDK pending flags, presents pending/timeout/resolved UI, and keeps the SDK’s one-slot constraint. Timeout changes user messaging only; it does not clear the pending token or unlock another submission.

**Tech Stack:** Kotlin, Android Application/Activity lifecycle, Kotlin StateFlow/coroutines, AdmobApplovinWrapper 1.8.5, JUnit4, Truth, Robolectric, MockK.

## Global Constraints

- Keep current AdMob-primary provider and all consent/ad behavior unchanged.
- `activateVipByKey` and `activateVipByToken` still require network; no offline redemption implementation.
- Do not claim to verify or change SDK internals; test app integration by mocking SDK callbacks.
- SDK pending result listener is global; callback and state holder must not retain Activity/View references.
- Keep one pending token only; do not allow another token submission until SDK resolves the current pending token.
- Timeout is 30 seconds and changes messaging only; it must not cancel SDK retry, mark token invalid, or unlock submission.
- Preserve legacy 3-day/30-day key flow, rewarded grant behavior, and ECDSA token routing.
- Preserve existing dependency versions; no new library.
- All user-facing strings require English and Vietnamese translations, matching repository localization policy.

---

## File Map

- Create `app/src/main/java/com/mckimquyen/watermark/feature/vip/VipTokenPendingState.kt`: application-scoped observable states and transitions; no Android Activity references.
- Modify `app/src/main/java/com/mckimquyen/watermark/MyApplication.kt`: register one SDK result listener after `setConfig` and before `initialize`.
- Modify `app/src/main/java/com/mckimquyen/watermark/feature/vip/VipManagementActivity.kt`: observe pending state, distinguish immediate success/failure from SDK-pending `false`, timeout presentation, lock/unlock controls, refresh entitlement on late success and re-entry.
- Modify `app/src/main/res/values/strings.xml` and `app/src/main/res/values-vi/strings.xml`: pending, still-verifying, resolved-failure, resolved-success messaging only if no existing resource fits.
- Create `app/src/test/java/com/mckimquyen/watermark/feature/vip/VipTokenPendingStateTest.kt`: pure state-transition tests.
- Create `app/src/test/java/com/mckimquyen/watermark/feature/vip/VipTokenPendingActivityRoboTest.kt`: activity/SDK callback integration tests using established MockK/Robolectric patterns.

## Task 1: Define pending state model with tests

**Files:**
- Create: `app/src/test/java/com/mckimquyen/watermark/feature/vip/VipTokenPendingStateTest.kt`
- Create: `app/src/main/java/com/mckimquyen/watermark/feature/vip/VipTokenPendingState.kt`

**Interfaces:**
- Produce sealed state `VipTokenPendingState.State`: `Idle`, `Pending(timedOut: Boolean)`, `Resolved(activated: Boolean)`.
- Produce methods `begin()`, `timeout()`, `resolve(activated: Boolean)`, `consumeResolved()` and read-only `StateFlow<State>`.
- `timeout()` only maps `Pending(false)` to `Pending(true)`; it must not change `Pending(true)` to `Idle`.
- `resolve()` only changes a pending state; ignore stale callbacks after `Idle`/already resolved.
- `consumeResolved()` returns to `Idle` after UI consumes result.

- [ ] **Step 1: Write failing tests** for begin → Pending, timeout preserves pending lock, success/failure resolution, stale resolution ignored, and consumeResolved → Idle.
- [ ] **Step 2: Run the targeted test** with `./gradlew :app:testDebugUnitTest --tests "*.VipTokenPendingStateTest"`; expected initial compile failure because state type is absent.
- [ ] **Step 3: Implement minimal state holder** with `MutableStateFlow`, keeping it independent of Android and Activity types.
- [ ] **Step 4: Rerun targeted tests**; expected all state-transition tests pass.
- [ ] **Step 5: Verify test file and implementation have no clock or Android dependencies.**

## Task 2: Wire SDK listener at Application scope

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/watermark/MyApplication.kt`
- Modify: `app/src/test/java/com/mckimquyen/watermark/feature/vip/VipTokenPendingActivityRoboTest.kt`

**Interfaces:**
- `MyApplication` registers `AdManager.setPendingVipTokenResultListener { activated -> VipTokenPendingState.resolve(activated) }` exactly once in `setupAdmob`, after config and before SDK `initialize`.
- Callback captures only singleton state holder; never Activity, binding, view, or coroutine scope owned by Activity.

- [ ] **Step 1: Add a failing integration assertion** that startup registers the listener before `AdManager.initialize` and callback updates the state holder.
- [ ] **Step 2: Run targeted test**; expected failure because app does not register the pending listener.
- [ ] **Step 3: Add one registration in `setupAdmob`** at the verified order: `setConfig` → test IDs/listeners → pending result listener → `initialize`.
- [ ] **Step 4: Rerun targeted integration test**; expected callback order and state assertion pass.
- [ ] **Step 5: Add cleanup to test teardown** using `AdManager.setPendingVipTokenResultListener(null)` and reset the state holder; do not add production unregister tied to Activity destruction.

## Task 3: Connect redeem flow and pending UI

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/watermark/feature/vip/VipManagementActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-vi/strings.xml`
- Modify: `app/src/test/java/com/mckimquyen/watermark/feature/vip/VipTokenPendingActivityRoboTest.kt`

**Interfaces:**
- Legacy input continues to call `activateVipByKey`; ECDSA token continues to call `activateVipByToken`.
- If `activateVipByToken` returns true, use existing immediate-success path; do not wait for pending callback.
- If it returns false and `AdManager.isVipTokenPending || AdManager.isVipTokenResolutionInFlight`, begin/retain pending UI; otherwise preserve immediate invalid-token message.
- Pending disables `edtVipKey` and `btnRedeemKey`; no new token is submitted while state is Pending.
- On result true, call `refreshVipState`, show token success once, clear pending state. On result false, show invalid/rejected result once, clear pending state.
- 30-second timeout only changes text to still-verifying; keep controls locked until result.
- On Activity creation/resume, read the singleton state and SDK pending flag, refresh entitlement, and consume a resolved state once.

- [ ] **Step 1: Add failing Activity tests** for: token API `false` + pending flag → pending UI; `false` without pending → invalid UI; legacy code still calls legacy API; pending callback true/false unlocks input and updates state; timeout message appears but input remains disabled; callback after timeout still resolves; destroy/recreate observes retained state.
- [ ] **Step 2: Run only `VipTokenPendingActivityRoboTest`**; expected failures must be assertions about pending state/UI, not Activity initialization errors.
- [ ] **Step 3: Add translated resources** for pending message, timed-out-but-pending instruction, and pending result failure only if existing resources cannot express them.
- [ ] **Step 4: Implement Activity observation** with `repeatOnLifecycle(STARTED)` or existing lifecycleScope/flow pattern; cancel the 30-second delay when state resolves or Activity is destroyed. Use a named `PENDING_UI_TIMEOUT_MS = 30_000L` constant.
- [ ] **Step 5: Implement narrow state handling**: keep `Pending(timedOut=true)` locked, show no “invalid” status until SDK resolves; update SDK entitlement and show late result once.
- [ ] **Step 6: Rerun targeted Activity tests**; expected pass without real network or signed token generation.
- [ ] **Step 7: Re-run `VipKeysTest`, `VipRedeemRoutingRoboTest`, `VipManagementActivityRoboTest`, and `VipRewardedPolicyRoboTest`.**

## Task 4: Full verification and audit diff

**Files:**
- No additional production files unless tests expose a defect.

- [ ] **Step 1: Run all VIP tests**: `./gradlew :app:testDebugUnitTest --tests "*.Vip*Test" --tests "*.Vip*RoboTest"`.
- [ ] **Step 2: Run all unit tests**: `./gradlew :app:testDebugUnitTest`.
- [ ] **Step 3: Run lint and release compilation**: `./gradlew :app:lint :app:assembleRelease`.
- [ ] **Step 4: Review `git diff --check` and `git diff`**; verify no Activity retained in singleton, no new ad trigger, no offline-redeem claim, no second-pending submission, and no unlocalized strings.
- [ ] **Step 5: Report test counts and unresolved limits**; do not claim end-to-end SDK token signature verification unless executed with a genuine test token and applicable network/init state.
