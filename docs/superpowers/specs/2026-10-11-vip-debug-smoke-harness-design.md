# VIP Debug Smoke Harness Design

**Ngày:** 2026-10-11
**Phạm vi:** Xác minh VIP device-bound token/pending thật trên thiết bị QA đã khóa bằng debug-only harness cho `AdmobApplovinWrapper:1.8.5`.

## Mục tiêu

Tạo một harness chỉ có trong debug để mint token VIP ECDSA gắn thiết bị đã khóa/app sau khi SDK init, rồi gửi token đó ở lần mở app kế tiếp trước SDK `initialize()`. Mục tiêu là xác nhận SDK queue token vào single pending slot, hiển thị pending UI, rồi retry và gửi kết quả qua application-scoped listener.

Legacy 3-day smoke đã pass trước đó; key đã dùng và không được redeem lại.

## Bối cảnh SDK đã kiểm chứng

- `generateVipToken(privateKeyBase64, days, bindToCurrentDevice, bindToApplication, ...)` chỉ hoạt động khi `AdSdkConfig.isDebug=true` và SDK đã initialize, vì generator đọc package name và internal stable device ID.
- `AdManager.earlyInit(context)` tạo stable ID trước khi load pending token đã persist.
- `activateVipByToken()` gọi `TokenSigner.verifyToken()` trước nhánh queue. Token hợp lệ có `boundDeviceId`, trong khi `currentDeviceStableId` rỗng thì SDK queue token qua `pendingActivateToken.compareAndSet(null, token)` và trả `false`.
- `setPendingVipTokenResultListener` callback chỉ mang Boolean; app đã đăng ký listener trước `initialize()`.
- Thiết bị duy nhất được dùng cho kiểm chứng: **thiết bị QA đã khóa**, serial `<serial-thiết-bị-đã-khóa>`. Chỉ gửi lệnh tới thiết bị đã khóa.

## Thiết kế harness hai lần khởi chạy

### Giai đoạn A — mint sau init

1. Debug-only harness Activity mở từ hidden developer entry point (long-press version row trong `AboutActivity`); Activity và entry point chỉ có trong debug manifest/source set.
2. Người vận hành đưa private key vào vùng app-private qua `adb -s <serial-thiết-bị-đã-khóa> shell run-as com.mckimquyen.watermark ...` với stdin pipe; không truyền key qua command argument, shared storage, clipboard, intent, screenshot, log hoặc BuildConfig.
3. Harness xác nhận debug build, package `com.mckimquyen.watermark`, device serial/target đã chọn, app VIP hiện có, và trạng thái SDK initialized.
4. Harness đọc key vào RAM, gọi SDK generator với thời hạn 1 ngày và `bindToCurrentDevice=true`, `bindToApplication=true`.
5. `finally` xoá file key dù ký thành công hay lỗi. Token chỉ ghi vào `noBackupFilesDir` để chuyển sang lần mở kế tiếp; token không xuất hiện trong UI/logcat.

### Giai đoạn B — queue trước init

1. Debug-only hook trong `MyApplication` chạy sau `AdManager.setConfig()` và `setPendingVipTokenResultListener(...)`, nhưng trước `AdManager.initialize()`.
2. Hook chỉ chạy khi explicit one-shot sentinel và token handoff file đều tồn tại; nếu thiếu một trong hai, app dùng luồng init mặc định.
3. Hook đọc token vào RAM rồi xoá file trước khi gọi `activateVipByToken(applicationContext, token)`.
4. Harness yêu cầu kết quả `false` và ít nhất một SDK flag `isVipTokenPending`/`isVipTokenResolutionInFlight` bật. Nếu API trả immediate-success, invalid, hoặc không queue, ghi trạng thái không nhạy cảm rồi tiếp tục init bình thường; không gọi đó là pending pass.
5. Gọi `AdManager.initialize()` theo đúng flow hiện có. SDK nhận stable device ID và retry; listener application-scoped cập nhật `VipTokenPendingState`, Activity hiển thị pending/result.
6. Sau callback, harness xoá sentinel/status/token files. Không revoke/clear VIP hoặc app data.

## Cô lập release và Hilt

- Harness Activity/UI nằm trong `app/src/debug/java`; không cần Hilt injection, chỉ gọi common entrypoint.
- Không thay `Application` subclass: `MyApplication` là `@HiltAndroidApp`, class final; subclass có thể phá Hilt/WorkManager.
- Tạo common `VipTokenSmokeHook.consumePendingToken(context)` contract; debug/release implementations cùng FQN theo source set. `MyApplication` gọi hook sau `setConfig`/listener và trước `initialize`; debug không sentinel trả no-op, release implementation no-op và không có secret/file code.
- Tạo common `VipTokenSmokeEntryPoint.open(activity)` contract; debug implementation mở Activity, release implementation no-op. `AboutActivity` chỉ gọi khi `BuildConfig.DEBUG` và version row long-press; release UI không đổi.
- Mint Activity dùng SDK public `generateVipToken()` sau normal init; private key không vào Application, Gradle, BuildConfig hay APK.
- Release artifact phải không chứa harness Activity/file handling/sentinel parser/private key. R8/manifest inspection bắt buộc.
- Handoff chỉ xử lý khi one-shot sentinel tồn tại; không sentinel nghĩa là normal app startup không đọc file key/token.

## Guardrails và lỗi

- Handoff file: `noBackupFilesDir`, one-shot. Thư mục app-data là 0700 nên app khác không đọc được; file key do `adb run-as` tạo có mode 0666 trong thư mục đó (chấp nhận vì chỉ build debug). Không có ghi đè byte khi xoá — `delete()` chỉ unlink. Chỉ status enum không nhạy cảm được phép lưu.
- Key bị xoá trong `finally` sau mint; token bị xoá trước submit trong giai đoạn B; cleanup lặp lại khi startup/callback/failure.
- Không log token, private key, key fragments, token fingerprints, GAID hoặc stable ID.
- Nếu file permission/pipe, key-pair verification, package bind, SDK pending condition, hoặc Hilt subclass fail: dừng, xoá handoff files an toàn, báo blocker. Không hạ mức binding và không fake SDK flags.
- Trước mọi thao tác UI trên thiết bị đã khóa phải chụp màn hình. Nếu thấy ad, dừng test và chờ user “done”.
- Không build/install/chạy lệnh trên thiết bị khác ngoài thiết bị đã khóa.

## Kiểm thử

### Unit tests

- Key handoff file được đọc một lần và bị xoá cả success/failure.
- Token handoff bị xoá trước SDK API call.
- Hook không chạy nếu debug build/sentinel/token không đúng.
- Trạng thái failure không chứa key/token.

### Robolectric/Activity tests

- Debug harness UI/one-shot state không xuất hiện hoặc không active khi release config.
- SDK `isVipTokenResolutionInFlight` ngăn submit thứ hai; pending restore/resolution/timeout hiện có tiếp tục pass.
- Finishing Activity success giữ cờ VIP nhưng không mở dialog; failure không ghi success marker.

### Android build/device tests

- `compileDebugAndroidTestKotlin` pass nếu instrumentation wrapper có thể chạy mà không vận chuyển secret qua APK.
- thiết bị đã khóa giai đoạn A: mint token; xác nhận key file đã bị xoá, token không hiển thị.
- thiết bị đã khóa giai đoạn B: xác nhận pre-init API false + pending flag true; chụp pending UI; chờ SDK callback và xác nhận terminal UI.
- Nếu chỉ immediate success/rejection xảy ra, ghi đúng outcome; không tính pending smoke là pass.
- `assembleRelease` + kiểm tra DEX/manifest xác nhận debug harness không có trong release.

## Hoàn tất / push gate

- Toàn bộ unit suite, `ktlintCheck`, lint, `assembleRelease`, và `git diff --check` đều xanh.
- Independent review chạy trên đúng primary `dev` working tree.
- Chỉ chấm >9/10 khi pending thật đã được quan sát trên thiết bị đã khóa bằng token device-bound hợp lệ, secret hygiene đạt, và regression suite xanh.
- Không commit/push cho đến khi user xác nhận chính xác file list sẽ stage; chỉ push `dev`, không push `master`.
