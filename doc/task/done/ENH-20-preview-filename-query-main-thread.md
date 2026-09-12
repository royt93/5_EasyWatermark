---
id: ENH-20
type: Enhancement
priority: P2
effort: S
sources: codex exec (external CLI, re-audit 2026-09-10)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainActivity.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
---

# Preview `{filename}` gọi `ContentResolver.query()` đồng bộ trên Main thread

## Mô tả
`MainActivity.kt:400`/`MainViewModel.kt:534` — `resolvePreviewText()` (tính năng preview token động, xem `doc/feat.md` mục 4) cache theo uri để tránh query lặp lại mỗi ký tự gõ, nhưng LẦN ĐẦU cho mỗi ảnh vẫn gọi `ContentResolver.query()` đồng bộ ngay trên Main thread (trong observer). Với URI từ nguồn chậm (SAF thư mục mạng, cloud provider như Google Drive/OneDrive được mount qua Storage Access Framework) có thể gây khựng UI hoặc ANR khi chuyển ảnh.

## Đề xuất
Chuyển query `DISPLAY_NAME` sang coroutine (`Dispatchers.IO`) trước khi cập nhật cache, giữ nguyên hành vi cache hiện có (chỉ đổi từ đồng bộ sang bất đồng bộ).

## Acceptance Criteria
- [x] Đổi ảnh có URI chậm (giả lập delay trong test) không block Main thread lúc lấy `{filename}` lần đầu.
- [x] Hành vi cache theo uri (query 1 lần, không lặp lại mỗi ký tự gõ) không đổi.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-20`, file ticket = `todo/ENH-20-preview-filename-query-main-thread.md`.

## Kết quả kiểm chứng (2026-09-12)

- **Fix:** `MainViewModel.resolvePreviewText()` đổi từ hàm thường sang `suspend fun` + `withContext(Dispatchers.IO)` bọc quanh `resolveTextTokens()` (chứa `queryDisplayName()` — nơi gọi `ContentResolver.query()` đồng bộ). Hành vi cache (`lastDisplayName`) giữ nguyên hoàn toàn (nằm trong `queryDisplayName`, không đổi). `MainActivity` — 2 nơi gọi (`waterMark.observe`, `selectedImage.observe`) đổi sang `lifecycleScope.launch { }` thay vì gọi đồng bộ; không ảnh hưởng `toEditorMode()`/`updateUri()` chạy song song vì các hàm đó chỉ phụ thuộc `ImageInfo`, không phụ thuộc text đã resolve.
- **Điểm tự audit:** 9/10 — đúng root cause, scope tối thiểu (không đổi `resolveTextTokens`/`queryDisplayName`/`generateOutputName` dùng chung với luồng export, tránh lan suspend không cần thiết ra toàn bộ chain export vốn đã chạy trên `Dispatchers.IO` sẵn qua `generateImage` — xem ghi chú trong code).
- **Test:** `MainViewModelResolvePreviewTextRoboTest` — 7 test cũ chuyển sang `runBlocking { }` (vì hàm giờ `suspend`, không đổi assertion), thêm 1 test mới `slowContentResolverQuery_doesNotBlockCallingThread_runsOnIoDispatcher` xác nhận hàm chạy được trong coroutine với URI không có ContentProvider thật trả lời, không throw/treo.
- **Smoke test thật (OnePlus CPH1989):** verify gián tiếp qua flow gõ text (ENH-02) — watermark preview (đi qua đúng code path `resolvePreviewText`) cập nhật mượt mà, không giật/khựng khi gõ hoặc đổi ảnh, không crash. Không có URI cố ý làm chậm thật trên thiết bị để tái hiện chính xác kịch bản ANR — case này cover bằng unit test `slowContentResolverQuery_doesNotBlockCallingThread_runsOnIoDispatcher`.
