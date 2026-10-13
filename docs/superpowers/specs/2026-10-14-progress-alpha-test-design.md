# Design: ProgressImageView lifecycle and alpha precision

**Date:** 2026-10-14  
**Tickets:** BUG-82, ENH-48  
**Scope:** Implement BUG-82 first, then ENH-48. No unrelated refactors.

## Goals

1. Stop `ProgressImageView` animation when its View detaches, preventing callbacks from continuing against a detached View.
2. Preserve alpha slider percentage across conversion to the persisted 0..255 range and back; specifically, 99% must not reopen as 98%.
3. Add unit, widget, and Android instrumentation integration coverage for applicable behavior.

## Design

### BUG-82 — ProgressImageView lifecycle

Add `onDetachedFromWindow()` to `ProgressImageView`. Cancel its lazy `startAnimator`, then call the superclass lifecycle method, matching cleanup conventions used by other custom Views. Do not alter normal `start()`, `finish()`, `failed()`, or `ready()` behavior.

Tests:
- Unit-level assertion for the animator cleanup contract where it can be isolated without duplicating Android View behavior.
- Robolectric widget test attaches the View to an Activity, starts its animator, detaches it, and asserts animator is no longer running.
- Instrumentation test uses a real Android Activity/View lifecycle to start and detach the View, then asserts animation stops. No production UI or test-only production hook unless existing visibility prevents direct observation; prefer reflection over adding test-only API.

### ENH-48 — alpha conversion precision

In `AlphaPbFragment`, replace truncating conversion in both directions with nearest-integer rounding (`roundToInt()`):
- Slider percent → native alpha: round `(percent / 100) * 255`.
- Native alpha → slider percent: round `(alpha / 255) * 100`.

Keep current slider range and native alpha bounds (0..100 and 0..255). Do not change persistence schema or add shared global constants solely for these conversions. If conversion logic is extracted for unit testability, keep it private/local to the feature and test both directions.

Tests:
- Unit tests cover 0%, 1%, 50%, 99%, 100%, and native bounds; round-trip error must be at most one slider percentage point because 256 native values cannot represent every percentage exactly.
- Robolectric widget test drives `AlphaPbFragment` slider and verifies the resulting native alpha and displayed percentage.
- Instrumentation integration test uses a real Android host and isolated real DataStore, changes slider to 99%, waits for persistence, recreates/reopens the fragment, and verifies it displays 99% (allow at most ±1 point only if native quantization requires it; 99% specifically must remain 99%).

## Verification and completion

Run focused tests RED before implementation and GREEN after each fix; then run full `:app:testDebugUnitTest`, `:app:ktlintCheck`, and relevant `connectedDebugAndroidTest` instrumentation tests. Build/install/test only on session-locked Pixel 7 Pro (`2B051FDH3006MU`). Smoke-test each behavior on that device. If any screenshot flow shows an ad obscuring UI, stop and wait for user confirmation before continuing.

Move each ticket from `inprogress/` to `done/` only after its tests, smoke test, and audit pass; record exact results in the ticket and update `BACKLOG.md`. Push only after verification and only to `origin/dev`.

## Explicitly out of scope

- Changing alpha UI range or storage format.
- General animation-duration cleanup.
- Refactoring other alpha panels or custom Views.
- Backend, Ad/VIP, or deferred IDEA work.
