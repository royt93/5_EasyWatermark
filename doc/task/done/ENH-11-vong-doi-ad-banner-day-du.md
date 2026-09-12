---
id: ENH-11
type: Enhancement
effort: XS
sources: Agy (1/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/about/AboutActivity.kt
---

# Vòng đời Ad Banner đầy đủ (resume/pause/destroy) ở `AboutActivity`

## Mô tả
`AboutActivity.kt:123-147` chưa gọi đầy đủ `AdManager.bannerResume()`/`bannerPause()`/`bannerDestroy()` theo vòng đời Activity tương ứng (`onResume`/`onPause`/`onDestroy`), khiến banner ad có thể tiếp tục tiêu hao CPU/pin ngầm khi Activity không còn hiển thị.

## Đề xuất
Triển khai đầy đủ 3 lời gọi tương ứng đúng lifecycle callback.

## Acceptance Criteria
- [x] `bannerResume`/`bannerPause`/`bannerDestroy` được gọi đúng lifecycle.
- [x] Không còn banner ad hoạt động ngầm khi `AboutActivity` không ở foreground.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-11`, file ticket = `todo/ENH-11-vong-doi-ad-banner-day-du.md`.

## Kết quả kiểm chứng (2026-09-12)

- **Fix:** Thêm `AdManager.bannerResume(bannerAdView)` vào đầu `onResume()` (trước nhánh gỡ banner khi VIP active — thứ tự đúng: khôi phục trước, rồi mới xét gỡ nếu cần). Thêm override `onPause()` gọi `AdManager.bannerPause(bannerAdView)`. Thêm override `onDestroy()` gọi `AdManager.bannerDestroy(bannerAdView)`.
- **Điểm tự audit:** 9.5/10 — cả 3 API đều null-safe + idempotent theo doc của SDK (`AdManager.kt` sources đã đọc trực tiếp xác nhận), không rủi ro double-destroy hay NPE khi `bannerAdView` đã null (case VIP).
- **Test:** Không thêm unit test (gọi 3 hàm SDK ngoài, không có logic nghiệp vụ nội bộ để test đơn vị — bản thân `AdManager` là thư viện ngoài, không mock được trong unit test JVM của project này).
- **Smoke test thật (OnePlus CPH1989):** mở `AboutActivity` từ menu ⋮ → About (không crash, `onResume` gọi `bannerResume` lần đầu vào — null-safe vì `bannerAdView` chưa load xong không sao). Bấm Home (trigger `onPause` → `bannerPause`) rồi mở lại app (trigger `onResume` → `bannerResume`) — không crash, UI hiện lại đúng. Đóng Activity bằng nút X (trigger `onDestroy` → `bannerDestroy`) — không crash, không lỗi logcat, quay về editor bình thường.
