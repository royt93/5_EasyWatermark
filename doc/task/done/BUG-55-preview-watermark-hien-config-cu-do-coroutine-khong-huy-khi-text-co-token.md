---
id: BUG-55
type: Bug
priority: P2
effort: S
sources: re-audit 2026-10-04 (agent ui) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainActivity.kt
---

# Preview watermark có thể kẹt ở config cũ khi text chứa token `{...}` (coroutine không huỷ, hoàn tất lệch thứ tự)

## Mô tả
Observer `waterMark` (`MainActivity.kt` ~546-558) mỗi lần emit lại `lifecycleScope.launch` coroutine mới, không huỷ coroutine trước. `resolvePreviewText` chạy `Dispatchers.IO` khi text có token → các coroutine có thể xong lệch thứ tự, coroutine cũ xong sau cùng ghi đè `ivPhoto.config` bằng giá trị cũ. Nhánh `selectedImage` (~583-588) ghi `ivPhoto.config` trực tiếp (không qua `launchView.post`), thêm race.
Kịch bản: text `{date} © Roy`, kéo slider alpha/size liên tục → preview dừng ở giá trị trung gian, export dùng giá trị cuối → preview ≠ kết quả.

## Đề xuất
Giữ 1 `private var previewConfigJob: Job?`, `cancel()` trước mỗi lần launch ở cả 2 observer; hoặc gộp `combine(waterMark, selectedImage).collectLatest`.

## Acceptance Criteria
- [x] Emit liên tiếp N config, coroutine trước chậm hơn coroutine sau → `ivPhoto.config` luôn = config emit cuối.
- [x] Hai nhánh (waterMark / selectedImage) không ghi đè nhau lệch thứ tự.
- [x] Tách logic chọn-config-mới-nhất thành hàm thuần để unit test được; test chứng minh thứ tự (RED trước fix).
- [x] Không leak Job; không đổi hành vi khi text không có token.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-55`, file ticket = `todo/BUG-55-preview-watermark-hien-config-cu-do-coroutine-khong-huy-khi-text-co-token.md`.

## Kết quả kiểm chứng

**Fix:** thêm `PreviewConfigUpdater<T>` (giữ đúng 1 `Job`, mỗi `update()` huỷ job trước, `cancel()` ở `onDestroy`); `MainActivity` dùng chung cho observer `waterMark` và `selectedImage` thay vì `lifecycleScope.launch` rời rạc.

- **Audit:** 9.3/10 — logic chọn-job-mới-nhất tách thành class thuần generic nên test được không cần Activity; generic `T` để test không phụ thuộc `android.net.Uri` (bài học `returnDefaultValues`). Trừ điểm: không có UI test tự động cho đường observer thật.
- **Unit test:** `PreviewConfigUpdaterTest` 4 test (job cũ hoàn tất SAU job mới không ghi đè; chưa chọn ảnh thì không resolve; chỉ nội dung cuối được áp; `cancel()` bỏ job đang chờ). RED thật khi bỏ `job?.cancel()` (2 test chính fail), GREEN khi có.
- **Smoke test thật** (Pixel 7 Pro, serial `2B051FDH3006MU`, ngày 2026-10-04): chọn ảnh → sửa text thành `{filename}{date}` bằng chip → preview resolve đúng thành `ewm_<timestamp>` + `2026-10-04` → mở "Độ trong", kéo slider qua lại 8 lượt rồi dừng → preview cập nhật theo giá trị cuối (40%), không kẹt, logcat không có `FATAL EXCEPTION`.
