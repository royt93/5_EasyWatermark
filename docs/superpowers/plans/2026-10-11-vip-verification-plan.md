# VIP Debug Pending Harness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove SDK 1.8.5 VIP device-bound token queue/retry on authorized thiết bị đã khóa, without putting private key in an APK and without changing production behavior.

**Architecture:** Two-launch debug harness. Launch A runs after normal SDK init, reads one-shot key from app-private storage, mints a one-day token bound to device/app, deletes key, and stores token privately. Launch B consumes token after SDK config/listener registration but before `initialize()`, requiring SDK pending/in-flight evidence, then continues normal init. Use source-set-specific hook implementations: main code calls a hook interface/object; debug implementation handles handoff; release implementation is no-op. Release init ordering stays unchanged and release artifact must contain no mint UI or secret handling.

**Tech Stack:** Kotlin, Android View system/ViewBinding, debug/release source sets, JUnit/Truth/Robolectric/MockK, Hilt, SDK `AdmobApplovinWrapper:1.8.5`, Gradle 8.9.

## Global Constraints

- Preserve pre-existing working changes; never reset/revert unrelated files.
- Stay on `dev`; never merge to `master`.
- Only Android device: thiết bị QA đã khóa serial `<serial-thiết-bị-đã-khóa>`; chỉ gửi lệnh tới thiết bị đã khóa.
- Private key transfer: read local property into host process memory and pipe via stdin to `adb -s <serial-thiết-bị-đã-khóa> shell run-as com.mckimquyen.watermark ...`; never include secret in command argv, shared storage, APK, `BuildConfig`, source, log, screenshot, report, or commit.
- User approved one short-lived device-bound test token. Existing three-day legacy key was already consumed and must never be retried/revoked; preserve current active entitlement.
- Key deleted in `finally` after mint. Token handoff deleted before SDK submit. Handoff uses `noBackupFilesDir`, one-shot sentinel, no data clear/revoke.
- Before every device UI interaction, capture screenshot. If any ad appears, stop immediately and wait for user “done”. No rewarded-ad interaction.
- Debug harness must be explicit opt-in and inert when files/sentinel absent. Production init order/behavior unchanged. Release hook no-op; release DEX/manifest must not contain harness UI, private-key handling, or token handoff code.
- If Hilt/source-set compile, secret handoff, key pairing, or SDK pre-init queue assertion fails, stop safely and do not push.
- Push only if real pending is demonstrated on thiết bị đã khóa, independent score >9/10, all checks pass, and user approves exact staged file list. Exclude unrelated pre-existing changes; never push `master`.

---

## File Map

- Add variant objects with identical FQNs, referenced from main source and resolved per build type: `VipTokenSmokeHook.consumePendingToken(context)` and `VipTokenSmokeEntryPoint.open(activity)`. Debug implementations handle harness; release implementations are no-op and do not reference debug classes.
- Create debug `VipTokenSmokeHarness.kt`, `VipTokenSmokeActivity.kt`, layout, and `app/src/debug/AndroidManifest.xml` registration.
- Add debug-only long-press on About version row; call `VipTokenSmokeEntryPoint.open(this)` only under `BuildConfig.DEBUG`. Release About behavior remains unchanged.
- Modify `MyApplication.kt` after listener registration and before initialize to call `VipTokenSmokeHook.consumePendingToken(this)`; release implementation no-op, preserving production flow.
- Add tests for one-shot handoff cleanup, non-secret result enums, and variant hook ordering; generate opaque test payloads at runtime, never log or persist them in source/report.
- Modify `app/src/main/java/com/mckimquyen/watermark/MyApplication.kt`: one common hook call after listener registration and before `initialize()`; variant implementation decides behavior.
- Create `app/src/test/java/com/mckimquyen/watermark/feature/vip/VipTokenSmokeHarnessTest.kt`: one-shot files, cleanup, and non-secret outcomes.
- Modify `app/src/test/java/com/mckimquyen/watermark/feature/vip/VipTokenPendingActivityRoboTest.kt` only if harness exposes regression.
- Update `.superpowers/sdd/task-3-report.md`, `.superpowers/sdd/progress.md`, and this plan with non-secret evidence.

---

## Task 1: Verify thiết bị đã khóa and safe stdin file transfer with dummy data

**Files:** none.

- [x] Verify locked target only: `adb -s <serial-thiết-bị-đã-khóa> shell getprop ro.product.model`; expected `thiết bị QA đã khóa`.
- [x] Safe dummy stdin roundtrip passed using `adb -s <serial-thiết-bị-đã-khóa> exec-in run-as com.mckimquyen.watermark sh -c 'cat > files/noBackupFilesDir/<dummy>'`; compare hash in host memory and immediately delete. Do not use `adb shell` for stdin payload: it attempts shell redirection before run-as and cannot access app-private path.
- [ ] Read bytes back into host process memory, compare SHA-256, then delete temporary file. Print only `dummy_pipe=pass|fail`, not bytes/hash. Do not hardcode test payload that resembles a VIP token or key.
- [ ] If `run-as`/stdin pipe fails, stop. Do not fall back to command-line key, shared storage, clipboard, or email.

---

## Task 2: Implement common hook and variant implementations, test-first

**Files:** common hook contract, debug/release implementations, `VipTokenSmokeHarnessTest.kt`.

**Interfaces:**

```kotlin
internal interface VipTokenSmokeHook {
    fun consumePendingToken(context: Context, submit: (String) -> Boolean): SubmitResult
}

internal interface VipTokenSmokeEntryPoint {
    fun open(activity: Activity)
}

internal class VipTokenSmokeHarness(
    private val filesDir: File,
    private val signer: (String) -> String,
) {
    fun mintToken(): MintResult
    fun consumePendingToken(context: Context, submit: (String) -> Boolean): SubmitResult
}

internal sealed interface MintResult {
    data object NotArmed : MintResult
    data object Created : MintResult
    data object Failed : MintResult
}

internal sealed interface SubmitResult {
    data object NotArmed : SubmitResult
    data object Queued : SubmitResult
    data object NotQueued : SubmitResult
}
```

Private file names remain internal implementation constants; result objects carry no secret data.

- [ ] Write test: when sentinel/token absent, hook returns `NotArmed`, does not call submit, and leaves ordinary init untouched.
- [ ] Run targeted test; expected RED because hook implementations are absent.
- [ ] Write test: create an unpredictable token string in memory at runtime (never use a source literal, log, assertion message, or report field containing token material); when armed, hook reads it once, deletes token + sentinel before invoking callback, and second invocation returns `NotArmed`. Assert callback count and result, not token contents.
- [ ] Run test; expected RED.
- [ ] Implement release variant `VipTokenSmokeHookImpl` as a no-op with no file reads or SDK calls.
- [ ] Implement debug variant delegating to `VipTokenSmokeHarness.consumePendingToken` only when a one-shot sentinel exists.
- [ ] Run focused tests; expected PASS.

---

## Task 3: Implement debug key minting and one-shot file safety

**Files:** common `VipTokenSmokeHarness.kt`, `VipTokenSmokeHarnessTest.kt`.

- [ ] Write test: signer succeeds → key file deleted in `finally`, token file and sentinel written, result contains only `Created` enum. Generate signer output at runtime; do not hardcode token-like strings or include token data in assertion messages.
- [ ] Run exact test; expected RED.
- [ ] Write test: signer throws → key file still deleted, no token/sentinel written, result contains only `Failed` enum (or exception is safely rethrown without message/logging).
- [ ] Run exact test; expected RED.
- [ ] Implement `VipTokenSmokeHarness(filesDir, signer)` with `mintToken()` and `consumePendingToken(context, submit)`; private filenames are constants, files are app-private `noBackupFilesDir`, and secret/token stay local to method stack.
- [ ] `mintToken()` fails closed if sentinel missing, reads key once, invokes injected signer, deletes key in `finally`, stores token+sentinel only on success, returns enum only.
- [ ] Do not put SDK references in common main harness. Debug Activity supplies signer closure calling `AdManager.generateVipToken(privateKey, 1, bindToCurrentDevice=true, bindToApplication=true)` after checking debug/package/init; no internal ID reflection.
- [ ] Run harness unit tests and debug compilation; expected PASS.

---

## Task 4: Add debug-only UI entry point

**Files:** debug `VipTokenSmokeActivity.kt`, debug layout/manifest, common `VipTokenSmokeEntryPoint.kt`, debug/release entry-point implementations, `AboutActivity.kt`.

- [ ] Add long-press on existing version row as hidden debug entry, without replacing/subclassing AboutActivity. Call common `VipTokenSmokeEntryPoint.open(this)` only under `BuildConfig.DEBUG`; debug implementation starts harness Activity, release implementation no-op and references no debug class.
- [ ] Debug-only Activity has simple state text and one “Tạo mã thử 1 ngày” button; no EditText for secret and no TextView containing key/token.
- [ ] The key arrives only via already-tested app-private file transfer. Activity never accepts key through Intent/Bundle.
- [ ] Add Robolectric test asserting release behavior does nothing and debug entry opens harness; assert displayed status strings exclude key/token values.
- [ ] Register Activity only in `app/src/debug/AndroidManifest.xml`, `exported=false`.
- [ ] Run focused Activity tests and manifest merge; expected PASS.

---

## Task 5: Add pre-init debug hook without changing release behavior

**Files:** common hook contract, debug/release hook implementations, `MyApplication.kt`, hook tests.

- [ ] Write test asserting event order in debug: `setConfig` → pending listener registration → `consumePendingToken` → `initialize`.
- [ ] Run test; expected RED.
- [ ] Add one common hook call after listener registration and before `initialize()`. Do not wrap/reorder existing setup calls.
- [ ] Debug hook with absent sentinel returns `NotArmed` immediately; standard init continues. Release hook implementation is no-op and has no debug-file references.
- [ ] Debug hook with sentinel consumes/deletes token before calling `activateVipByToken(applicationContext, token)`; asserts false and at least one SDK flag pending/in-flight. If no pending flag, write only safe `NotQueued` status; continue normal `initialize()` and never resubmit.
- [ ] Hook event order test proves release hook no-op and production setup still calls listener then initialize; debug hook inserted between them only when one-shot sentinel exists.
- [ ] Run focused tests; expected PASS.

---

## Task 6: Verify release isolation

- [ ] Run `./gradlew :app:assembleRelease`.
- [ ] Inspect merged release manifest; confirm debug `AboutActivity`/harness Activity overrides are absent and manifest resolves to main Activity.
- [ ] Inspect release DEX/class list; confirm no `VipTokenSmokeHarness`, `VipTokenSmokeActivity`, handoff file names, or mint UI strings. Verify release hook is a no-op and references no debug code.
- [ ] If any harness/secret code remains in release, stop; do not push.

---

## Task 7: thiết bị đã khóa two-launch smoke

**Device:** thiết bị QA đã khóa `<serial-thiết-bị-đã-khóa>` only; chỉ gửi lệnh tới thiết bị đã khóa.

- [x] Verify device model with `adb -s <serial-thiết-bị-đã-khóa> shell getprop ro.product.model`.
- [ ] Capture screenshot; if ad visible, stop and wait for user “done”. Confirm VIP still active through 14 Oct 2026; do not redeem legacy key, revoke, or clear app data.
- [ ] Transfer private key via stdin pipe directly to `run-as` app-private no-backup directory. Confirm only file presence/permissions; do not read/print contents.
- [ ] Open debug About version-row long press; mint one-day bound token. Confirm key file deleted. Do not screenshot while any secret/token text might be on screen; UI should show status only.
- [ ] Force-stop and relaunch the same debug app on thiết bị đã khóa. Before UI interaction, capture screenshot and re-check no ad. Debug hook consumes token before SDK initialize.
- [ ] Assert `activateVipByToken` returned false and SDK pending/in-flight flag became true. If immediate success/rejected/not queued, record outcome, do not claim pending, and stop further token attempts.
- [ ] If pending shown, capture sanitized screenshot (no key/token) and wait for SDK callback; verify result UI and key/token/sentinel files absent. Do not reset or revoke existing entitlement.
- [ ] If SDK cannot queue before early init because `generateVipToken` requires stable ID, stop and report blocker; no reflection, fake flags, network disabling, or test-only SDK mutation.

---

## Task 8: Final audit and push gate

- [ ] Run `./gradlew :app:ktlintCheck`.
- [ ] Run `./gradlew :app:testDebugUnitTest`; count XML tests/failures.
- [ ] Run `./gradlew :app:lint :app:assembleRelease`.
- [ ] Run `git diff --check`.
- [ ] Independent review actual primary `dev` checkout; score correctness 4, tests 2, lifecycle 1, secret hygiene 1, device evidence 2. Award pending device points only if real pending + callback observed.
- [ ] If score >9/10, all checks pass, pending smoke proven, prepare focused file list excluding all pre-existing changes and ask user to approve that exact list before commit/push.
- [ ] Push only `dev` after explicit approval; never push `master`.
- [ ] If any gate fails, leave uncommitted and state exact blocker.

## Kết quả thực tế (2026-10-12)

- Thiết bị khóa theo phiên: ban đầu Pixel 7 Pro; theo yêu cầu mới của chủ dự án đổi sang TECNO KJ7. Các tham chiếu "Pixel" ở trên đọc là "thiết bị đã khóa". Không có lệnh nào gửi tới thiết bị còn lại sau khi đổi.
- Pending thật đã quan sát bằng hook debug `vip-smoke-init-delay` (hoãn `initialize` 55 giây): màn VIP hiện "Đang xác minh mã VIP…", SDK retry sau khi init, dialog "Thành công" hiện đúng một lần, `no_backup/` sạch.
- Giới hạn trung thực: ảnh pending không chứng minh việc khoá nút. Nút "Kích hoạt VIP" xám vì ô nhập rỗng; khi pending, ô nhập và nút vẫn bật cho mã legacy và chỉ chặn token thứ hai (có Snackbar giải thích).
- Hai lỗi nền không thuộc diff này, xuất hiện ở 4/7 lần chạy full: lint crash FIR ở `AppLogGatingTest.kt` và `CardFrameRendererRoboTest` (`FileSystemAlreadyExistsException`). Chạy riêng đều pass; cần ticket riêng.

## Bổ sung sau audit lần 3 (2026-10-12)

- **Lỗi thật tìm thấy trên thiết bị, không phải trong test:** token resolve khi app nằm nền (không màn VIP nào mở) làm nhãn "Kích hoạt" lệch: ghi mốc cấp nằm trong Activity nên chỉ chạy khi mở lại màn VIP (16:11 trong khi token kích hoạt ~16:06).
- **Sửa:** `VipTokenResultListener` (đăng ký ở `MyApplication` trước `initialize`) ghi mốc cấp ngay lúc SDK resolve, rồi mới công bố `VipTokenPendingState`. `VipManagementActivity` chỉ hiển thị, không ghi prefs.
- **Xác minh lại trên thiết bị:** token mint lúc 17:04, app mở nền, SDK retry và kích hoạt, mốc cấp trong prefs = 17:06:14 (đúng lúc kích hoạt, trước đó 16:11:49), `no_backup/` sạch.
- **Chưa kiểm:** Snackbar chặn token thứ hai trên thiết bị (ad toàn màn hình chen ngang hai lần); chỉ có test Robolectric.
- Số lần lỗi nền (lint FIR, `CardFrameRendererRoboTest`) vẫn chưa tìm ra nguyên nhân gốc; lần chạy full thứ 8 xanh.
