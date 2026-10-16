# Thiết kế kiểm thử release AdMob/UMP

- Ngày: 2026-10-15
- Trạng thái: Đã được người dùng duyệt thiết kế
- Phạm vi: app Android hiện tại, `AdmobApplovinWrapper:1.8.5`, release dùng `IS_ENABLE_ADMOB=true`
- Ngoài phạm vi: xác nhận cấu hình AdMob Console (người dùng xác nhận đã hoàn tất), triển khai AppLovin mediation, IAP/Billing, sửa production code hoặc xác nhận pháp lý thay cho bên vận hành.

## 1. Mục tiêu và giới hạn bằng chứng

Tạo bộ kiểm thử có thể tái chạy, kiểm chứng luồng app gọi consent, provider và ad đúng thứ tự; kiểm tra đường vào từ launcher và `ACTION_SEND`, lifecycle/interruption, rewarded VIP, privacy options, network failure/recovery và chất lượng UI/performance.

Không tuyên bố chứng minh “mọi case” hay bảo đảm tuyệt đối. Mỗi ca phải báo một trong các trạng thái `PASS`, `FAIL`, `NOT TESTED`, `N/A`, kèm môi trường và bằng chứng (test result, log hoặc trace). Test tự động chỉ chứng minh hành vi trong điều kiện được dựng; không chứng minh dashboard, chính sách pháp lý, tất cả OEM/mạng hoặc quyết định runtime của server quảng cáo.

## 2. Hiện trạng đã biết

- App phụ thuộc wrapper `1.8.5`; cấu hình runtime hiện đặt `IS_ENABLE_ADMOB=true`.
- `MyApplication` gọi `setConfig()` rồi `initialize()`; Splash gọi `requestConsentInfoUpdate()` trước `awaitSplashComplete()`.
- `MainActivity` cho phép `ACTION_SEND` ảnh. Đã sửa cold share, restored share và `onNewIntent`: interstitial preload chỉ chạy sau callback UMP với `canRequestAds=true`; launcher flow giữ nguyên preload sau Splash. Robolectric bao phủ các nhánh này. Pixel UMP EEA manual Accept và Deny pass (mỗi lượt 1/1; người dùng tự chọn trên form). Deny ghi `PurposeConsents=00000000000`. Instrumentation gate sau Deny pass 1/1: `AdManager.loadInterstitial()` callback trả `SafetyBlocked(Consent not granted)`, diagnostics `WAITING_FOR_CONSENT`; không show ad. Robolectric xác nhận cold-share không preload khi callback deny. HTTP/network traffic chưa capture.
- Đã có Robolectric test một phần cho Splash, MainActivity share intent và About privacy options; có instrumentation test kiểm tra cấu hình ad/test-device IDs.
- Không thấy IAP/BillingClient hoặc cấu hình mediation trong luồng app hiện tại. Hai hạng mục được ghi `N/A`, không tạo mock test tuyên bố tích hợp thật.
- Macrobenchmark hiện chưa được bật theo ghi chú project; profiling/jank cần thiết lập hoặc chạy công cụ Android có sẵn sau khi kiểm tra build infra.

## 3. Nguyên tắc thiết kế

1. Không dùng ad unit production cho automated interaction; không tự click quảng cáo.
2. Test UI app-owned với dữ liệu ổn định. Không chụp/so sánh ad creative do network cung cấp.
3. Test consent bằng UMP test geography và test device theo hướng dẫn SDK; không thay consent thật của người dùng.
4. Giữ cấu hình provider, test geography, test IDs và network conditioning tách khỏi release config.
5. Không thêm thư viện/framework mới nếu test hiện tại hoặc Android tooling sẵn có đáp ứng; đề xuất phụ thuộc mới phải nêu lý do và xin duyệt riêng.
6. Không sửa code tích hợp chỉ để test pass; nếu test lộ sai thứ tự/thực hành thì báo finding, thêm test đỏ trước khi đề xuất sửa.
7. Mọi test Activity/listener/coroutine phải dispose/cancel; xác minh không giữ Activity sau destroy.

## 4. Test layers và ma trận

### 4.1 Unit/Robolectric

| ID | Ca kiểm thử | Kỳ vọng | Bằng chứng |
|---|---|---|---|
| UMP-01 | App khởi động từ Splash | `requestConsentInfoUpdate` hoàn tất trước `awaitSplashComplete`; navigation tiếp tục cả khi callback báo không thể request ad | Verify thứ tự call và callback |
| UMP-02 | Cold/restored `ACTION_SEND` ảnh mở thẳng `MainActivity` | Không gọi preload trước callback; chỉ preload khi `canRequestAds=true` | `MainActivityInterstitialRoboTest` cho phép/từ chối consent; SDK/network vẫn cần device evidence |
| UMP-03 | `onNewIntent` với ảnh mới | Refresh consent; interstitial preload chờ callback `canRequestAds=true` | `MainActivityInterstitialRoboTest`; SDK/network vẫn cần device evidence |
| UMP-04 | Intent không phải ảnh / ordinary launch | Không gọi consent refresh dành riêng cho share | Verify không gọi |
| UMP-05 | Consent callback trả false/error | UI không treo; không tự bypass consent hoặc gọi ad ngoài guard SDK | Assert tiếp tục điều hướng, không gọi app-owned show |
| UI-01 | About → Ad privacy options | Hàng hiện, clickable, gọi đúng API SDK | View assertion + verify |
| VIP-01 | Rewarded callback `earned=true` | Cấp VIP đúng một lần; refresh state; tải lại theo contract | Fake callback và verify grant count |
| VIP-02 | `earned=false`, no-fill, dismiss hoặc error | Không cấp VIP; UI phục hồi trạng thái button/callback | Assert không grant; button state |
| LIFE-01 | Activity destroy/recreate khi consent/ad callback pending | Không crash, không thao tác UI trên Activity cũ, callback xử lý an toàn | Lifecycle test, weak reference nếu khả thi |
| LIFE-02 | Callback lặp hoặc callback trễ | Không cấp thưởng hai lần và không cập nhật UI sau destroy | Assert idempotency/guard |

**Chi tiết UMP-02:** test phải phân biệt hai tín hiệu: app đã gọi `loadInterstitial()` và SDK thực sự bắt đầu provider/network load. Dùng fake boundary nếu có; nếu public wrapper không có injectable seam, kiểm thử logic app ở Robolectric và dùng instrumentation/log/SDK diagnostics để xác minh request thực. Không xem một `verify(loadInterstitial)` đơn lẻ là bằng chứng request đã bị chặn.

### 4.2 Widget và golden

- Widget/Robolectric kiểm tra trạng thái hiển thị, nhãn, enabled/clickable và accessibility của privacy options/reward UI, bao gồm trạng thái consent pending và callback lỗi.
- Golden áp dụng cho layout app-owned, với locale/theme/font scale cố định và dữ liệu fixture. Ad banner container được thay bằng placeholder tĩnh; không render network ad trong golden.
- Trước triển khai, xác định cơ chế snapshot đã có trong repo/CI. Nếu chưa có, dùng instrumentation screenshot với ảnh baseline đã review; không thêm screenshot library mới nếu không có duyệt riêng.
- Golden pass chỉ chứng minh pixel/UI tại cấu hình đã chốt; không chứng minh tất cả kích cỡ/OEM. Bổ sung semantic/widget assertions để tránh golden che lỗi tương tác.

### 4.3 Instrumentation/device integration

Chạy trên thiết bị đã khóa cho session, hiện là Pixel 7 Pro serial `2B051FDH3006MU`; trước lần chạy mới cần xác nhận serial còn kết nối, không tự chuyển sang thiết bị khác.

| ID | Ca kiểm thử | Kỳ vọng | Chứng cứ cần lưu |
|---|---|---|---|
| DEV-01 | Cài sạch, launcher, non-EEA/test config | Luồng init đúng; không crash; app vào editor | Build fingerprint, SDK/app version, log có timestamp |
| DEV-02 | Fresh consent state + UMP test geography EEA, mở launcher | Form UMP xuất hiện; provider/ad request chỉ sau quyết định theo cấu hình và SDK contract | UMP callback, provider init/ad request ordering |
| DEV-03 | EEA accept | Consent form đóng đúng; app tiếp tục; SDK state/diagnostics phù hợp | Manual UMP test trên Pixel pass: callback hoàn tất; `IABTCF_gdprApplies=1`; `IABTCF_PurposeConsents=11111111111`; không lưu full TC string có vendor/consent payload |
| DEV-04 | EEA deny | App tiếp tục; không ép personalized consent hoặc tự mở gate trái kết quả | **PASS 1/1 Pixel**: UMP callback hoàn tất; `gdprApplies=1`; `PurposeConsents=00000000000`; `AdManager.loadInterstitial()` callback trả `SafetyBlocked(Consent not granted)`, provider `WAITING_FOR_CONSENT`; không show ad. HTTP/network traffic chưa capture |
| DEV-05 | Privacy options/re-consent từ About | SDK mở form khi có sẵn; app không crash/treo nếu không cần form | Screen result + callback/log |
| DEV-06 | Mở trực tiếp `ACTION_SEND` khi chưa quyết định consent | Share UI vẫn hoạt động; không có provider/network ad load trước consent decision | **PASS manual Pixel smoke**: `adb am start ACTION_SEND image/jpeg` mở `MainActivity`; UMP callback `canRequestAds=true`; `loadInterstitial` chỉ được gọi sau callback, SDK chặn `consent chưa cho phép (UMP)` khi provider chưa init/consent gate chưa mở; không crash. Không có image thật (URI cố ý không tồn tại), nên ảnh render/import: `NOT TESTED` |
| DEV-07 | Activity interruption: home/background/resume, rotate/recreate, back/cancel trong lúc form | Không show ad đè lên consent/system flow; không callback vào Activity đã hủy; app phục hồi | **PARTIAL Pixel manual**: (a) rotate landscape → portrait trên MainActivity (không có ad): PASS, `topResumedActivity=MainActivity`, không `FATAL EXCEPTION`/`ANR`/`WindowLeaked`/`Activity is destroyed`, rotation đã khôi phục. (b) Home → mở lại app: **App Open test ad toàn màn hình hiện** (`AdActivity`, 22:19:42) — khớp thiết kế SDK (`doc/AD_PROMPT_AOS.MD`: App Open tự resume qua ProcessLifecycle, chỉ loại trừ `SplashActivity`/`VipManagementActivity` và quanh Activity hệ thống bằng `AppOpenSuppressor`); không phải lỗi. Người dùng tự đóng ad, không click. (c) Back/cancel trong consent form, recreate khi consent đang chờ: `NOT TESTED`. Instrumentation `ActivityScenario` cho share + recreate: `NOT TESTED` bằng công cụ này. Sau khi đóng app `reader`, test vẫn treo 90s; log cho thấy MainActivity đã `Resumed` nhưng `ActivityScenario` bỏ qua sự kiện vì intent không khớp (`ACTION_SEND` bị `migrateExtraStreamToClipData` đổi ClipData) — giới hạn công cụ, không phải lỗi app. Đã xoá test. Phủ thay bằng Robolectric (recreate + callback cũ + deny, 14/14 pass); xoay màn hình thủ công trên Pixel pass |
| DEV-08 | Mạng offline trước cold start | Consent fetch lỗi được xử lý; app không treo; gate consent không bypass | **PASS Pixel**: offline `Active default network: none`; fresh state (`pm clear`) cho UMP `code=2`, `canRequestAds=false` 3/3; online consent đã lưu rồi offline trả `canRequestAds=true` 2/2, đúng UMP persisted behavior. EEA debug form Accept và Deny đã lưu đều offline trả `true`; interstitial/App Open không request do `no internet`. Reconnect retry test cold/offline→online pass 3/3: `retryConsentIfNeeded` rồi `canRequestAds=true`, `MainActivity` còn foreground, không crash/ad overlay. Mạng khôi phục sau mỗi ca. Source SDK tag `1.8.5` xác nhận UMP persisted gate và cả ba đường load/show dùng chung `consentAllowsAds`. **Giới hạn:** EEA production geography không-debug chưa test; log ở `/tmp/offline-verify/` (cục bộ, không commit) |
| DEV-09 | Ngắt mạng trong lúc consent fetch/form, rồi phục hồi | Không double-init; retry theo wrapper contract; không tải ad trước quyết định | Log chuyển trạng thái và request count |
| DEV-10 | App background trong lúc đang xem rewarded; callback sau recreate/process interruption nếu có thể dựng ổn định | Chỉ earned callback cấp VIP; không cấp trùng; không access Activity chết | Reward callback, VIP state, lifecycle log |
| DEV-11 | Banner và App Open ở About/foreground | Banner không che điều khiển; App Open không đè Splash/VIP/consent/system activity đã loại trừ | Screenshot/video; event log |

- Dùng test geography/test-device UMP của debug build theo SDK; xác nhận release config không chứa test geography.
- Với ad rendering, chỉ dùng test inventory được network cấp. Không click quảng cáo, không tạo impression/click giả để đạt kết quả.
- Fresh install/clear data/reset consent phá trạng thái cục bộ; chỉ làm trên thiết bị test đã được chấp thuận.

### 4.4 Network conditioning

Các trạng thái tối thiểu:

1. **PASS manual (Pixel)**: airplane mode + Wi‑Fi/mobile data off trước cold start; xác minh `Active default network: none`. UMP fetch lỗi code 2, `canRequestAds=false`; app không treo, App Open bỏ qua, interstitial bị consent block; wrapper chờ validated network. Bật Wi‑Fi/data lại ngay sau ca, validated network phục hồi.
2. Online chậm/latency cao trước consent update — `NOT TESTED`.
3. Ngắt mạng sau khi bắt đầu consent fetch — `NOT TESTED`.
4. Chuyển Wi‑Fi ↔ cellular hoặc mất validated internet trong khi app foreground — `NOT TESTED`.
5. Khôi phục mạng sau consent fetch fail — reconnect callback/retry chưa xác minh trong test có điều khiển tự động.

Với mỗi trạng thái, ghi điều kiện thật (loại mạng, mất kết nối/latency), kết quả callback, screen state và chuỗi event consent → init → load. Không kết luận từ toggle Wi-Fi đơn lẻ rằng internet đã mất; xác minh validated connectivity. Không inject network shaper mới nếu không có tooling hiện hữu/duyệt riêng.

### 4.5 Memory/leak/profile

- Lặp chu kỳ mở/đóng About (banner), VIP (rewarded), Splash/consent test flow và share intent ít nhất 20 vòng trong profile run; chụp heap/native/Perfetto khi bắt đầu và cuối.
- Tìm instance Activity/Window/Dialog/listener còn retained sau khi Activity bị destroy; ghi heap path nếu có leak.
- Kiểm tra timer, coroutine, listener và callback được hủy/gỡ; không bật LeakCanary trong production.
- Kết quả “không phát hiện leak” chỉ áp dụng số vòng, thiết bị và build đo được; không khẳng định không thể có leak.

### 4.6 Frame rate/jank

- Dùng Macrobenchmark/Perfetto hoặc Android Studio profiler trên profileable/profile build để đo cold start, mở About, vào VIP và resume sau system picker/share.
- Báo `frameDurationCpuMs`, frame overrun/jank theo frame timeline, p50/p90/p95, số lần chạy, thiết bị/API/build mode.
- Không đặt ngưỡng tùy tiện: chạy baseline ít nhất 10 lần trên cùng thiết bị; chốt ngưỡng regression so baseline trước khi gate CI. Không gộp thời gian mạng/UMP remote form vào chỉ số UI frame.
- Golden screenshot không thay thế frame-rate profiling. Macrobenchmark module hiện chưa bật; cần khảo sát build setup trước khi chọn đường triển khai.

### 4.7 IAP và mediation

| Hạng mục | Trạng thái | Quy tắc |
|---|---|---|
| IAP/Google Play Billing | `N/A` — chưa phát hiện BillingClient/purchase flow trong source hiện tại | Không viết test giả; nếu tích hợp về sau, mở spec riêng gồm license tester, purchase/acknowledge/restore/refund và entitlement validation. |
| Mediation | `N/A` — app đang chọn provider AdMob; không phát hiện cấu hình waterfall/adapter trong app | Không gọi kiểm thử mediation pass. Nếu mediation được bật trên Console, cần adapter inventory/config và test device/debugger riêng; console-only setup cần evidence vận hành. |
| AppLovin provider thay thế | Ngoài release matrix hiện tại | Chỉ thêm khi release target bật `IS_ENABLE_ADMOB=false` hoặc có variant thực sự ship AppLovin. |

## 5. Thiết kế test seam và dữ liệu

- Tận dụng MockK/Robolectric hiện có cho app orchestration.
- Ưu tiên một recorder nhỏ trong test (không thêm abstraction production) để thu chuỗi sự kiện `consentRequested`, `consentResolved`, `providerInit`, `adLoadRequested`, `rewardGranted`.
- Nếu wrapper không cho inject provider/load seam, không reflection/hook vào private implementation trong test app; dùng AAR 1.8.5 thật ở instrumentation, log/diagnostics public API và test geography.
- Test fixtures không lưu GAID, email, ảnh người dùng hoặc ad response payload. Scrub/redact identifier khỏi artifacts/logs.

## 6. Test execution và release gate

1. `./gradlew :app:testDebugUnitTest` (bao gồm unit/Robolectric/widget/golden phù hợp).
2. `./gradlew assembleDebug` và kiểm tra manifest merged/build config không chứa test geography trong release candidate.
3. Instrumentation trên serial đã khóa, qua Android runner; thiết bị phải dùng test ad units/test mode và UMP test device.
4. Device matrix: Pixel test làm bắt buộc; thêm OEM/API matrix theo thiết bị có sẵn trước release, báo rõ coverage thực.
5. Perf/leak runs tách khỏi correctness gate, lưu trace và baseline; không coi profiler run là test chính sách.
6. Release gate fail khi: test assertion fail; consent-order evidence sai; user bị kẹt; deny vẫn dẫn tới ad request trái SDK state; reward cấp khi không earned/được cấp hai lần; app crash; test inventory không được xác nhận.
7. Nếu test không chạy do thiếu thiết bị/tài khoản/UMP form/adapter, trạng thái là `NOT TESTED`, không chuyển thành pass.

## 7. Tiêu chí chấp nhận

- Có test tự động xác nhận cold/restored share không preload trước callback UMP và `onNewIntent` chờ callback trước lần preload tương ứng.
- Có test riêng cho consent callback false/error, privacy options, rewarded earned/denied, interruption/recreate và cleanup.
- Device evidence xác nhận EEA Accept/Deny callbacks và share-intent manifest trên Pixel. Riêng bằng chứng rằng không phát provider/network ad request sau Deny vẫn cần bổ sung trước khi đánh dấu privacy gate hoàn tất. Bằng chứng kèm SDK/AAR, app build, thiết bị/API và log đã scrub.
- Offline/failure/recovery chạy được ít nhất trên một thiết bị thật; nêu rõ điều kiện network.
- Golden chỉ cover UI app-owned; test interaction/accessibility không phụ thuộc screenshot.
- Leak/profile và jank report có metadata, baseline, số lần lặp; nếu module/công cụ chưa sẵn, ghi `NOT TESTED` và đầu việc riêng.
- IAP/mediation ghi `N/A` theo trạng thái source hiện tại, không dựng test để khẳng định tích hợp.
- Báo cáo cuối không dùng “mọi case đã được chứng minh” hoặc “10/10”; chỉ nêu coverage và bằng chứng thực tế.

## 8. Câu hỏi còn mở trước khi lập implementation plan

- Repo/CI có cơ chế golden screenshot nào được chấp thuận hay cần bắt đầu bằng instrumentation screenshot baseline?
- Macrobenchmark có được bật trong repo cho release test sprint này không, hay profiling thủ công Perfetto đủ cho vòng đầu?
- Có thêm Pixel/OEM/API target nào ngoài Pixel 7 Pro cần khóa vào ma trận không? Không tự chạy trên thiết bị khác.
- QC cần cung cấp format cụ thể nào cho claim “không follow SDK/example” (repro, build, logs, expected vs actual); test suite sẽ bổ sung testcase tương ứng sau khi nhận evidence.
