---
id: BUG-27
priority: P2
type: Bug
effort: S
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/adapter/SaveImageListAdapter.kt
---

# `SaveImageListAdapter.onCreateViewHolder` tính `maxLineHeight` từ `parent.height` lúc RecyclerView chưa layout xong

## Mô tả
BottomSheetDialog vừa show, `parent.height` có thể = 0 khi ViewHolder ĐẦU TIÊN được tạo (RecyclerView chưa đo/layout xong) — `maxLineHeight = 0` bị gán cố định cho `holder.ivIcon.height`, khiến vài thumbnail đầu tiên trong danh sách export co về 0dp (không thấy ảnh).

## Triển khai
Không tính height cố định lúc `onCreateViewHolder` dựa vào `parent.height` — dùng `ViewTreeObserver.OnGlobalLayoutListener` đo lại sau khi layout xong, hoặc dùng `wrap_content`/tỉ lệ cố định (aspect ratio) thay vì phụ thuộc `parent.height` tại thời điểm tạo ViewHolder.

## Acceptance Criteria
- [x] Mở dialog Export với batch nhiều ảnh (>5) — mọi thumbnail đầu danh sách hiển thị đúng kích thước, không có ảnh nào co về 0dp.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-27`, file ticket = `todo/BUG-27-saveimagelistadapteroncreateviewholder-tinh-maxlineheight-tu.md`.

## Kết quả kiểm chứng (2026-09-16)

**Fix**: chọn phương án "wrap_content/tỉ lệ cố định" thay vì `ViewTreeObserver` (robust hơn — loại bỏ hẳn race condition thay vì chỉ trì hoãn nó). RecyclerView `rvResult` (`dlg_save_file.xml`) có chiều cao CỐ ĐỊNH, không phải wrap_content — nên `onCreateViewHolder` không cần đọc `parent.height` tại runtime nữa. Tạo `R.dimen.save_result_row_height` (144dp) làm nguồn chung duy nhất, dùng cả trong XML (`rvResult` height) lẫn `SaveImageListAdapter.onCreateViewHolder()`. Xoá field `maxLineHeight` (không còn cần thiết).

**Unit test mới**: `SaveImageListAdapterHeightRoboTest.kt` — tạo `parent = FrameLayout(context)` CHƯA đo/layout (`parent.height == 0`, đúng kịch bản lỗi gốc), xác nhận `ivIcon.layoutParams.height` vẫn đúng (từ dimen resource, không phụ thuộc `parent.height`) và > 0; test thứ 2 xác nhận logic chia đôi height khi `itemCount >= 5`.
- Đã verify test THẬT SỰ bắt được bug: tạm revert fix (khôi phục đọc `parent.height`) → 2/2 test FAILED đúng dự đoán (height = 0). Khôi phục fix → PASS lại, cùng với 3 test cũ khác của adapter (Preview/Count/UpdateJobState) và `DlgSaveFileLayoutRoboTest` không bị ảnh hưởng bởi đổi `144dp` → `@dimen/save_result_row_height`.
- Full suite: `./gradlew testAppReleaseDebugUnitTest` — 288 tests, 0 failures (1 lần chạy trước đó gặp `QrCodeBottomSheetFragmentRoboTest` fail — xác nhận là flaky test pre-existing không liên quan, đã pass lại khi chạy riêng lẻ và khi chạy lại toàn bộ suite lần 2). `ktlintCheck` — BUILD SUCCESSFUL.

**Smoke test trên device thật** (Google Pixel 7 Pro, serial `2B051FDH3006MU` — device mới khóa trong session theo yêu cầu "dùng pixel"):
- Cài APK debug, chọn 6 ảnh (>5, đúng ngưỡng kích hoạt logic chia đôi height 2-hàng), vào editor → nút export (icon download) → dialog "Export to the album" → cuộn xuống "EXPORT LIST(0/6)".
- Kết quả: **toàn bộ 6 thumbnail hiển thị đúng kích thước ở cả 2 hàng, không có ảnh nào co về 0dp** — kể cả thumbnail ĐẦU TIÊN (vị trí chính xác mô tả trong ticket gốc bị lỗi).
- `adb logcat -d "*:E"` sau toàn bộ luồng: không có `FATAL`/exception nào từ package `com.mckimquyen.watermark`.
- Giữa chừng smoke test gặp App Open ad (test ad unit) — đã dừng theo R4, chờ user xác nhận "done" rồi tiếp tục.

**Tự chấm điểm**: 9.5/10 — root cause đúng, chọn giải pháp mạnh hơn đề xuất gốc (loại bỏ dependency thay vì chỉ trì hoãn), test tự-verify bằng revert, full suite xanh (đã phân biệt rõ 1 flaky test không liên quan), smoke test thật xác nhận đúng hiện tượng mô tả trong ticket đã hết trên device thật với đúng ngưỡng itemCount kích hoạt bug.
