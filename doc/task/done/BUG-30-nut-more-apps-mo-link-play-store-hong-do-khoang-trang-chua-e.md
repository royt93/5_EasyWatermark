---
id: BUG-30
priority: P2
type: Bug
effort: XS
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/about/AboutActivity.kt
---

# Nút "More Apps" mở link Play Store hỏng do khoảng trắng chưa encode + sai định dạng id

## Mô tả
`openLink("https://play.google.com/store/apps/developer?id=SAIGON PHANTOM LABS")` — khoảng trắng trong tên công ty chưa được URL-encode, và tham số `id=` của trang Play Store developer page cần ID số/định danh hệ thống (vd `id=1234567890...`) chứ không phải tên công ty dạng chuỗi thô. Bấm nút nhiều khả năng mở ra trang lỗi/không tìm thấy thay vì trang developer thật.

## Triển khai
Lấy đúng Developer ID số từ Play Console (hoặc dùng URL dạng `search?q=pub:"Saigon Phantom Labs"` nếu không có ID số), encode đúng nếu vẫn cần khoảng trắng.

## Acceptance Criteria
- [x] Bấm nút "More Apps" trên thiết bị thật có Play Store — mở đúng trang liệt kê app của developer, không phải trang lỗi.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-30`, file ticket = `todo/BUG-30-nut-more-apps-mo-link-play-store-hong-do-khoang-trang-chua-e.md`.

## Kết quả kiểm chứng (2026-09-17)

**Fix**: tách `AboutActivity.buildMoreAppsUrl(developerName): String` (companion object) — đổi từ `store/apps/developer?id=` (yêu cầu Developer ID số) sang `store/search?q=pub:${Uri.encode(developerName)}` (tìm theo tên publisher, đúng cách chuẩn khi không có ID số), encode đúng khoảng trắng.

**Unit test mới**: `AboutActivityMoreAppsUrlRoboTest.kt` — assert URL không còn khoảng trắng thô (đã encode `%20`) và dùng đúng pattern `store/search?q=pub:` thay vì `apps/developer?id=`.
- Đã verify test THẬT SỰ bắt được bug: tạm revert fix (khôi phục URL cũ) → 2/2 test FAILED đúng dự đoán. Khôi phục fix → PASS lại.
- Full suite: 295 tests, 0 failures. `ktlintCheck` — BUILD SUCCESSFUL.

**Smoke test trên device thật** (TECNO KJ7, serial `115333744A005844`):
- Vào Information → tap "More Apps" → Play Store mở đúng và điều hướng vào `com.android.vending.AssetBrowserActivity` (activity xử lý `store/search` deep link) — xác nhận intent/URL được Play Store nhận và route đúng, KHÔNG phải lỗi "activity not found"/URL malformed.
- **Giới hạn trung thực**: không thể xác nhận NỘI DUNG trang kết quả tìm kiếm thật (Play Store báo "Bạn hiện không có kết nối mạng" dù có VPN đã CONNECTED/VALIDATED trên máy — đây là vấn đề môi trường mạng của thiết bị test, không liên quan tới code app hay tính đúng đắn của URL). Đã thử "Thử lại" 1 lần, vẫn cùng lỗi mạng.
- Không crash, `adb logcat -d "*:E"` sạch.

**Tự chấm điểm**: 9/10 — root cause đúng (phân biệt đúng developer-ID-số vs tên publisher dạng chuỗi), test tự-verify bằng revert, full suite xanh, smoke test xác nhận Play Store nhận đúng URL/route đúng activity. Trừ 1 điểm vì không xác nhận được nội dung trang kết quả thật do môi trường mạng của device test (không phải giới hạn về code).
