---
id: BUG-52
type: Bug
priority: P2
effort: S
sources: full codebase audit (general-purpose agent, 2026-10-01) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/LocationNameResolver.kt
---

# `LocationNameResolver` — nhánh Geocoder legacy (API < 33) không có timeout, có thể treo export

## Mô tả
Nhánh `Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU` (dòng 94-97 trở lên) dùng callback API mới có bọc `CountDownLatch.await(GEOCODE_TIMEOUT_MS, TimeUnit.MILLISECONDS)` để giới hạn thời gian chờ. Nhánh `else` (API 24-32 — vẫn nằm trong minSdk 24 của app) gọi thẳng `geocoder.getFromLocation(...)` là hàm **blocking đồng bộ**, không có bất kỳ cơ chế timeout nào bọc quanh.

Trên thiết bị cũ (API < 33, vẫn là nhóm thiết bị được app hỗ trợ chính thức) với mạng yếu hoặc Geocoder service hệ thống phản hồi chậm/treo, lệnh gọi này có thể block vô thời hạn trên thread export (chạy trong WorkManager/`Dispatchers.IO` của batch export) — vi phạm đúng ngưỡng `GEOCODE_TIMEOUT_MS` mà chính file này tự định nghĩa và áp dụng cho nhánh mới, kéo dài/treo cả batch export không giới hạn.

## Đề xuất
Bọc lệnh gọi `geocoder.getFromLocation()` ở nhánh legacy bằng cơ chế timeout tương đương (ví dụ chạy trong `Future`/coroutine riêng rồi `get(GEOCODE_TIMEOUT_MS, TimeUnit.MILLISECONDS)`, hoặc `withTimeoutOrNull(GEOCODE_TIMEOUT_MS)` nếu đang ở ngữ cảnh coroutine), nhất quán với nhánh API mới.

## Acceptance Criteria
- [x] Nhánh API 24-32 khi `geocoder.getFromLocation()` giả lập treo lâu hơn `GEOCODE_TIMEOUT_MS` → hàm trả về (null/fallback) đúng trong khoảng thời gian timeout đã định nghĩa, không treo vô hạn.
- [x] Hành vi khi Geocoder trả về bình thường (không treo) không đổi so với hiện tại.
- [x] Unit/integration test mô phỏng Geocoder chậm trên nhánh legacy xác nhận timeout hoạt động đúng.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-52`, file ticket = `todo/BUG-52-locationnameresolver-nhanh-geocoder-legacy-khong-timeout.md`.

## Kết quả kiểm chứng

**Fix:** tách hàm `runWithTimeout(timeoutMs, block)` (companion object) — chạy `block` trên 1 `Executors.newSingleThreadExecutor()` riêng (shutdown ngay trong `finally`, không giữ pool sống), chờ tối đa `timeoutMs` qua `Future.get(timeout)`. Nhánh legacy đổi từ gọi thẳng `geocoder.getFromLocation(...)` sang bọc qua `runWithTimeout(GEOCODE_TIMEOUT_MS) { ... }`, nhất quán ngưỡng timeout với nhánh callback mới (`CountDownLatch.await(timeout)`).

- **Audit:** 9/10 — dùng thuần `java.util.concurrent` (stdlib, không thêm dependency), `shutdownNow()` trong `finally` đảm bảo không leak executor theo đúng R5; cùng triết lý với nhánh mới (chỉ NGỪNG CHỜ chứ không huỷ được lệnh gọi hệ thống đang treo — Java không ép dừng code blocking không hợp tác, đã ghi rõ trong comment để không ai hiểu nhầm là "huỷ hẳn request").
- **Unit test:** 3 test mới trong `LocationNameResolverTest.kt` — `runWithTimeout_blockHangsLongerThanTimeout_returnsNull_withoutBlockingCaller` (block ngủ 5s, timeout 200ms, assert kết quả `null` VÀ thời gian caller nhận lại < 2s — chứng minh caller không bị treo theo), `runWithTimeout_blockFinishesInTime_returnsValue` (không regression khi gọi bình thường), `runWithTimeout_blockThrows_returnsNull`. TDD: RED thật trước fix (`Unresolved reference 'runWithTimeout'`) → GREEN. `./gradlew :app:testDebugUnitTest` + `ktlintCheck` toàn bộ xanh.
- **Giới hạn xác nhận trên device thật:** device đã khoá session (TECNO KJ7) chạy **API 34** — nhánh legacy (`SDK_INT < 33`) về mặt vật lý KHÔNG thể trigger qua `Build.VERSION.SDK_INT` thật trên thiết bị này (không phải lựa chọn bỏ qua, mà là ràng buộc phần cứng; đổi sang thiết bị khác cần user yêu cầu tường minh theo R3). Đã verify thay thế bằng: (1) `connectedDebugAndroidTest` — `LocationTokenIntegrationTest` (3/3 PASS trên TECNO KJ7) xác nhận nhánh mới (không đổi) vẫn resolve Geocoder thật đúng, không regression; (2) cài `assembleDebug` chứa fix lên device, mở app bình thường, không crash, logcat sạch.
