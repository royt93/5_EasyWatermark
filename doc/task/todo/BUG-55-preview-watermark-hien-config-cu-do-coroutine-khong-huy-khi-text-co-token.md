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
- [ ] Emit liên tiếp N config, coroutine trước chậm hơn coroutine sau → `ivPhoto.config` luôn = config emit cuối.
- [ ] Hai nhánh (waterMark / selectedImage) không ghi đè nhau lệch thứ tự.
- [ ] Tách logic chọn-config-mới-nhất thành hàm thuần để unit test được; test chứng minh thứ tự (RED trước fix).
- [ ] Không leak Job; không đổi hành vi khi text không có token.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-55`, file ticket = `todo/BUG-55-preview-watermark-hien-config-cu-do-coroutine-khong-huy-khi-text-co-token.md`.
