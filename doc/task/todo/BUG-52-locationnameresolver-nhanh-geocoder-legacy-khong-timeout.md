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
- [ ] Nhánh API 24-32 khi `geocoder.getFromLocation()` giả lập treo lâu hơn `GEOCODE_TIMEOUT_MS` → hàm trả về (null/fallback) đúng trong khoảng thời gian timeout đã định nghĩa, không treo vô hạn.
- [ ] Hành vi khi Geocoder trả về bình thường (không treo) không đổi so với hiện tại.
- [ ] Unit/integration test mô phỏng Geocoder chậm trên nhánh legacy xác nhận timeout hoạt động đúng.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-52`, file ticket = `todo/BUG-52-locationnameresolver-nhanh-geocoder-legacy-khong-timeout.md`.
