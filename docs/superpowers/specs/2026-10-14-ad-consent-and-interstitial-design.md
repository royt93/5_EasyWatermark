# Thiết kế: Consent và interstitial sau batch export

## Mục tiêu

Xử lý hai finding MEDIUM từ audit `AdmobApplovinWrapper`:

1. Không tự bật interstitial khi batch export kết thúc, thời điểm user có thể đang thao tác với nút Share hoặc đang ở app nền.
2. Gọi UMP consent khi cold start bằng `ACTION_SEND` đi thẳng vào `MainActivity`, bỏ qua `SplashActivity`.

## Quyết định

### Interstitial

Bỏ callsite tự động `AdManager.showInterstitial()` gắn với `TYPE_JOB_FINISH`. Không thay bằng timer, chờ đóng dialog hay điều kiện lifecycle khác. Giữ `AdManager.loadInterstitial()` preload tại `MainActivity.onCreate()`; các callsite khác (nếu có) không thuộc phạm vi.

### Consent cho share-intent

`SplashActivity` tiếp tục là luồng UMP chính và gọi `requestConsentInfoUpdate()` mỗi lần được mở, chờ callback theo thiết kế hiện có. Điều này để UMP tự quyết định có cần hiển thị lại consent form hay không, theo `doc/AD_PROMPT_AOS.MD` và example SDK. `MainActivity` gọi cùng API bất đồng bộ khi được mở bằng cold `ACTION_SEND` có ảnh (không có saved state) hoặc nhận share mới qua `onNewIntent` — các entry này bỏ qua Splash. Không dùng gate toàn process vì sẽ chặn refresh/re-consent trong lần mở app sau. Request share không chặn UI; SDK tự gate quảng cáo tới khi consent sẵn sàng.

## Kiểm thử

- `ShareIntentResolverTest`: ACTION_SEND chỉ là shared-image khi action, MIME `image/*` và URI đều hợp lệ.
- Robolectric test: cold ACTION_SEND image và image `onNewIntent` request UMP; non-image và ordinary launch không request; `TYPE_JOB_FINISH` không gọi `showInterstitial`.
- Widget Robolectric coverage bảo đảm `TYPE_JOB_FINISH` vẫn hiện nút Share/Share ZIP, độc lập với ad.
- Instrumentation TECNO xác nhận PackageManager resolve image ACTION_SEND tới `MainActivity` exported + singleTask; không launch Activity hoặc Ad SDK UI.
- Chạy `ktlintCheck`, `lint`, `:app:testDebugUnitTest`, `:app:testReleaseUnitTest` và test manifest instrumentation riêng.

## Ngoài phạm vi

- Không thay đổi cấu hình provider, ad IDs, consent policy, AdSafety limits hoặc các quảng cáo khác.
- Không sửa các finding LOW của audit trong cùng thay đổi.
- Không yêu cầu build/install/smoke test trên thiết bị cho tới khi cần xác nhận thay đổi UI thực tế.
