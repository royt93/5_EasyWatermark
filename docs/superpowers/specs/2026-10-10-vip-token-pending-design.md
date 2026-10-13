# VIP token pending: thiết kế xử lý kết quả muộn

Ngày: 2026-10-10
Phạm vi: Android `VipManagementActivity`, wrapper `AdmobApplovinWrapper:1.8.5`.

## Bối cảnh đã xác minh

`AdManager.activateVipByToken(context, token)` có thể trả `false` ngay khi token device-bound gặp `currentDeviceStableId` rỗng, đồng thời giữ một pending token để retry. Kết quả thực được gửi qua `setPendingVipTokenResultListener`. SDK lưu listener global; chỉ `AdManager.destroy()` clear callback. Nếu listener capture Activity, callback có thể giữ Activity cũ. Nếu không đăng ký listener, kết quả retry không được UI nhận.

## Thiết kế

- Đăng ký listener đúng một lần ở `MyApplication` sau `setConfig` và trước `initialize`, dùng một bus application-scoped không giữ Activity.
- Bus giữ trạng thái `Idle`, `Pending`, `Resolved(success)`; kết quả listener đến muộn vẫn được ghi nhận sau khi Activity đóng/recreated.
- Khi UI gọi token API: `true` nghĩa là kích hoạt ngay thành công. `false` chỉ được coi là pending nếu SDK báo `isVipTokenPending` hoặc `isVipTokenResolutionInFlight`; nếu không, hiển thị lỗi như hiện tại.
- Pending UI hiện “Đang xác minh”, khóa input/nút redeem và không gửi token thứ hai. Sau 30 giây, chuyển sang hướng dẫn “Vẫn đang xác minh; giữ mạng, không nhập mã khác”; trạng thái vẫn khóa vì SDK giữ một slot.
- Listener thành công/thất bại kết thúc pending, refresh trạng thái SDK, mở khóa UI. Khi Activity mở lại, đọc trạng thái bus/SDK và trình bày kết quả pending đã resolve.
- Timeout chỉ đổi thông báo UI, không hủy token SDK và không coi pending là sai. Callback muộn cập nhật trạng thái khi Activity đang mở; nếu Activity đã đóng, lần mở VIP tiếp theo đọc trạng thái SDK/bus.
- Rewarded/legacy-key flow không đổi.

## Kiểm thử

- Unit test bus: pending → success/failure, kết quả lưu khi không có observer, callback không giữ Activity.
- Robolectric Activity: API token trả false + SDK pending → UI pending/khóa; callback success/failure → cập nhật UI; timeout không mở khóa; callback muộn sau timeout → cập nhật; Activity destroy/recreate không mất kết quả.
- Test legacy code và token routing hiện có tiếp tục pass.

## Harness debug-only để kiểm chứng pending trên thiết bị (2026-10-11)

Mục tiêu: tạo một token ECDSA test gắn đúng thiết bị QA đã khóa/package mà không đóng gói private key vào APK, rồi xác nhận SDK 1.8.5 thực sự queue token trước init và trả kết quả qua listener sau init.

### Luồng hai lần khởi chạy

1. Người vận hành gửi private key qua USB `adb -s <thiết bị đã khóa serial> shell run-as <package> ...` trực tiếp vào app-private `noBackupFilesDir`; key không đi qua argv, shared storage, logcat, screenshot, source, BuildConfig hay test report.
2. Harness chỉ tồn tại trong `src/debug`; UI có xác nhận một lần rõ ràng. Harness đọc key vào bộ nhớ, xác nhận `BuildConfig.DEBUG` + package mong đợi + state VIP hiện tại, gọi SDK `generateVipToken(privateKey, 1, bindToCurrentDevice=true, bindToApplication=true)`, rồi xoá file key trong `finally` (kể cả ký lỗi). Token được ghi app-private no-backup file, không log.
3. Người vận hành force-stop/mở lại app trên đúng thiết bị đã khóa. Trong `MyApplication.onCreate`, release path vẫn giữ nguyên thứ tự và hành vi hiện tại. Debug-only hook chạy sau `AdManager.setConfig` và `setPendingVipTokenResultListener`, nhưng trước `AdManager.initialize`: đọc token vào bộ nhớ, xoá file token ngay, gọi `activateVipByToken(applicationContext, token)` và assert trạng thái SDK là `isVipTokenPending=true` hoặc `isVipTokenResolutionInFlight=true`. Sau đó gọi `initialize()` bình thường để SDK retry token. Pending UI/result listener là phần được kiểm chứng.
4. Harness ghi duy nhất kết quả không bí mật (`QUEUED`, `IMMEDIATE_RESULT`, `REJECTED`, `INIT_ORDER_UNSUPPORTED`) vào app-private status; không ghi key/token. Sau khi test xong, xoá status/handoff files. Không tự clear/revoke entitlement đang có.

### Guardrails / giới hạn

- Không thêm private key vào APK, Gradle-generated BuildConfig, instrumentation APK, test resource, intent/command argument, log, screenshot hay repo.
- Harness debug-only; không sửa production/release init path. Build release không được chứa class/UI/branch này, cần kiểm tra APK/source-set.
- Chỉ chạy trên thiết bị QA đã khóa đã khóa (`<serial-thiết-bị-đã-khóa>`); chỉ gửi lệnh tới thiết bị đã khóa.
- Không dùng lại legacy key đã tiêu thụ. Token test phải hạn 1 ngày, bind current device và application ID.
- Mỗi bước UI phải có screenshot kiểm tra; nếu có ad thì dừng ngay và chờ user xác nhận.
- Nếu `activateVipByToken` không queue trước init theo contract bytecode, nếu stable ID/package binding không khớp, hoặc nếu secret transport không thể tránh process args/logs, dừng; pending smoke không được báo pass và không push.

## Harness debug-only kiểm chứng pending thật trên thiết bị đã khóa (2026-10-11)

### Mục tiêu

Xác minh trên thiết bị QA đã khóa (`<serial-thiết-bị-đã-khóa>`) rằng token device-bound đúng chữ ký có thể vào one-slot pending trước khi SDK có stable device ID, sau đó được retry và trả kết quả qua listener. Thiết bị QA đổi theo phiên: xem mục "Kết quả thực tế" trong `plans/2026-10-11-vip-verification-plan.md`. Không dùng lại legacy key 3 ngày đã tiêu thụ.

**Trạng thái phê duyệt:** User duyệt debug harness hai lần khởi chạy; private key có thể được chuyển tạm vào app-private storage qua USB, ký trong RAM và xóa ngay; token được lưu tạm để submit trước init lần kế tiếp.

**Điều kiện kỹ thuật đã xác minh bằng SDK 1.8.5 bytecode:** `AdManager.earlyInit()` gọi `DeviceId.stableId(context)` ngay trước khi load pending token; `activateVipByToken()` trước `earlyInit()` kiểm tra token chữ ký, thấy `boundDeviceId != null` và `currentDeviceStableId` rỗng thì `compareAndSet(null, token)` queue vào một slot. Đây là thứ tự dự kiến để tạo pending.

### Thiết kế hai lần khởi chạy

1. **Mint sau init:** debug-only harness nhận private key qua USB trực tiếp vào app-private storage, đọc một lần vào RAM, gọi SDK `generateVipToken(privateKey, days=1, bindToCurrentDevice=true, bindToApplication=true)`, xóa key trong `finally`, lưu token ngắn hạn vào app-private no-backup storage. Không log hoặc hiển thị key/token.
2. **Submit trước init:** lần khởi chạy debug tiếp theo, debug-only hook chạy sau `AdManager.setConfig` và listener registration nhưng trước `AdManager.initialize`; hook đọc token, xóa bản lưu ngay, gọi `activateVipByToken` với application context, xác nhận SDK trả false và `isVipTokenPending` hoặc `isVipTokenResolutionInFlight` bật, rồi tiếp tục `initialize` bình thường.
3. Listener toàn cục vẫn do `MyApplication` đăng ký; khi SDK có device ID, nó retry pending slot và Activity VIP hiển thị kết quả. Outcome chỉ ghi trạng thái không bí mật.

### Ràng buộc cấu hình

- Harness chỉ nằm trong debug source set (`app/src/debug`) và debug manifest. Release APK không được chứa harness, màn hình, hook hoặc private key.
- `MyApplication` hiện là `@HiltAndroidApp` và `final`; subclass debug app chỉ được dùng nếu Hilt generation/build xác nhận tương thích. Nếu không, không sửa release path; dừng và xin duyệt phương án debug-only branch riêng trong `MyApplication`.
- Phần production `setupAdmob`/init order không đổi. Debug hook chỉ tiêu thụ file test khi có one-shot sentinel rõ ràng và xóa mọi file ở cả success/failure.
- Khóa chuyển qua pipe stdin vào `run-as <applicationId>`; không truyền key làm command argument, shared storage, intent extra, BuildConfig, resource hay instrumentation APK.
- Token bind đúng package `com.mckimquyen.watermark` và stable ID SDK lấy từ cùng thiết bị đã khóa sau init.
- Nếu pre-init call bị SDK từ chối thay vì queue, nếu token pair không khớp, hoặc nếu không thể bảo đảm secret hygiene, dừng. Không giả lập cờ SDK, không thay đổi mạng để lách điều kiện, không tuyên bố pending smoke pass.
- Trước mọi UI/device interaction: chụх màn hình kiểm tra quảng cáo; nếu có ad, dừng và chờ user “done”.

### Kiểm thử

- Unit: handoff file one-shot, key file deleted in `finally`, token file deleted before submit, explicit opt-in required, no key/token in status/output.
- Robolectric: debug harness dispatches expected two phases; production path remains unchanged when no sentinel exists.
- Build/source-set: compile debug and release; inspect release APK class list/manifest to prove harness absent.
- Device: thiết bị QA đã khóa only. Legacy smoke đã pass riêng bằng mã 3 ngày; không redeem lại. Token smoke phải quan sát SDK pending/resolving thật trước khi init completes, sau đó callback resolves. Nếu kết quả immediate-success thì chỉ chứng minh signed-token redemption, không chứng minh pending.

## Ngoài phạm vi

- Sửa SDK wrapper hoặc đảm bảo redeem offline. Redeem vẫn cần Internet theo API/docs wrapper.
- Thay đổi ECDSA format, public key, trial, AdMob consent, billing/IAP.
- Harness/token/key được ship trong release hoặc dùng ngoài QA thiết bị đã khóa.
