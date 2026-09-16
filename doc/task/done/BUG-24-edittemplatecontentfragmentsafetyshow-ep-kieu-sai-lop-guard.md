---
id: BUG-24
priority: P1
type: Bug
effort: XS
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/EditTemplateContentFragment.kt
---

# `EditTemplateContentFragment.safetyShow()` ép kiểu sai lớp, guard chống trùng dialog vô hiệu

## Mô tả
Copy-paste từ `SaveImageBSDialogFragment.safetyShow()` nhưng quên đổi type: `manager.findFragmentByTag(TAG) as? SaveImageBSDialogFragment` — tag của `EditTemplateContentFragment` không bao giờ khớp lớp `SaveImageBSDialogFragment`, `as?` luôn trả null. Guard chống show trùng dialog mất tác dụng: bấm nhanh nút sửa template liên tiếp tạo nhiều dialog chồng lên nhau, có thể `IllegalStateException` (fragment đã add).

## Triển khai
Sửa `as? SaveImageBSDialogFragment` thành `as? EditTemplateContentFragment` (đúng lớp hiện tại).

## Acceptance Criteria
- [x] Bấm nút sửa template 2 lần liên tiếp thật nhanh — chỉ 1 dialog hiện ra, không crash `IllegalStateException`.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-24`, file ticket = `todo/BUG-24-edittemplatecontentfragmentsafetyshow-ep-kieu-sai-lop-guard.md`.

## Kết quả kiểm chứng (2026-09-16)

**Root cause & fix**: `EditTemplateContentFragment.kt` dòng 81 — `manager.findFragmentByTag(TAG) as? SaveImageBSDialogFragment` (copy-paste sai lớp) sửa thành `as? EditTemplateContentFragment`.

**Unit test mới**: `app/src/test/java/com/mckimquyen/watermark/ui/dlg/EditTemplateContentFragmentSafetyShowRoboTest.kt` — gọi `safetyShow()` 2 lần liên tiếp qua `FragmentManager` thật (Robolectric), assert chỉ 1 instance `EditTemplateContentFragment` tồn tại.
- Đã verify test THẬT SỰ bắt được bug: revert tạm fix (đổi lại `as? SaveImageBSDialogFragment`) → test FAILED đúng như dự đoán (`ComparisonFailureWithFacts`, expected size 1 nhưng có 2 instance). Khôi phục fix → test PASS.
- Full suite: `./gradlew testAppReleaseDebugUnitTest` — 272 tests, 0 failures, 0 skipped. Không có regression.
- `./gradlew ktlintCheck` — BUILD SUCCESSFUL, không vi phạm style.

**Smoke test trên device thật** (Samsung Galaxy S24 Ultra, `SM-S928B`, serial `R5CX613VZBR` — máy được user chỉ định dùng giữa chừng session bằng lệnh "dùng s24u", thay cho Tecno KJ7 đã khóa trước đó):
- Cài APK debug (`assembleAppReleaseDebug`), mở app, chọn ảnh, vào màn Text Watermark → "Edit watermark" → icon Template List → double-tap thật nhanh nút edit (pencil) trên 1 template có sẵn.
- Kết quả: chỉ 1 dialog "Edit Template" xuất hiện (xác nhận qua 2 lần back: lần 1 đóng bàn phím, lần 2 đóng dialog về thẳng Template List — không cần back lần 3 cho dialog chồng thứ 2).
- `adb logcat -d "*:E"` sau double-tap: không có `FATAL EXCEPTION` / `IllegalStateException` liên quan fragment, chỉ có log hệ thống không liên quan (SurfaceFlinger, DNS resolver của app khác).

**Lưu ý trung thực**: không có smoke test nào thực hiện trên Tecno KJ7 cho ticket này — toàn bộ device verification chạy trên Samsung S24 Ultra theo yêu cầu tường minh của user giữa chừng phiên làm việc.

**Tự chấm điểm**: 9.5/10 — root cause đúng, test tự-verify (đã chứng minh test bắt được bug qua revert), full suite xanh, smoke test thật trên device với bằng chứng logcat + hành vi back-stack. Trừ 0.5 vì chỉ test 1 kịch bản double-tap (chưa test tạo template mới `Add` hay case tag trùng với dialog khác).
