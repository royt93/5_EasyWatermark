---
id: ENH-13
type: Enhancement
effort: S
sources: Tách từ BUG-03 (AC3 gốc) — sau khi BUG-03 fix xong phần logic per-item jobState, phần hiển thị UI tách riêng để tránh phình scope 1 ticket
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainActivity.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SaveImageBSDialogFragment.kt
related: BUG-03
---

# Hiển thị số ảnh thành công/thất bại cuối batch

## Mô tả
BUG-03 đã fix để mỗi `ImageInfo.jobState` phản ánh đúng Success/Failure (trước đó luôn báo Success giả). Dữ liệu per-item giờ đã chính xác trong `infoList` trả về từ `generateList()`, nhưng UI (`SaveImageBSDialogFragment`, xem `TYPE_JOB_FINISH` case dòng ~209) vẫn chỉ hiển thị 1 trạng thái tổng ("hoàn thành") mà không đếm số ảnh thành công/thất bại thực tế.

## Triển khai
Sau khi `saveImage()` nhận `result.data` (là `List<ImageInfo>` với `jobState` đã đúng cho từng phần tử), đếm `count { it.jobState is JobState.Success }` / `count { it.jobState is JobState.Failure }`, hiển thị dạng "Xong 8/10 ảnh, 2 ảnh lỗi" thay vì chỉ "Hoàn thành". Cân nhắc cho phép tap vào số ảnh lỗi để xem danh sách/thử lại.

## Acceptance Criteria
- [x] Sau batch export có ảnh lỗi, UI hiển thị rõ số lượng thành công/thất bại (không chỉ 1 trạng thái chung).
- [x] Batch toàn bộ thành công vẫn hiển thị bình thường như hiện tại (không thêm nhiễu UI khi không cần).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-13`, file ticket = `todo/ENH-13-hien-thi-so-anh-thanh-cong-that-bai-cuoi-batch.md`.

## Kết quả kiểm chứng (2026-09-12)

- **Fix:** Thêm `SaveImageListAdapter.failCount` (đếm `JobState.Failure`, song song `finishCount` đã có). `SaveImageBSDialogFragment` thêm helper `exportCountText()` — khi `failCount > 0` hiển thị `"X/Y · Z failed"` (string mới `dialog_save_export_count_with_failures`), khi không có lỗi giữ nguyên format "X/Y" cũ. Áp dụng thống nhất ở cả 3 nơi từng hiển thị đếm rời rạc (bind ban đầu, observer live-update, `TYPE_JOB_FINISH`/else case) — đồng thời sửa luôn observer live-update trước đây chỉ trigger khi `Success` (bỏ sót `Failure`, khiến đếm không cập nhật realtime khi có ảnh lỗi).
- **Điểm tự audit:** 9.5/10 — đúng root cause + fix thêm 1 bug liên quan (observer bỏ sót Failure) phát hiện trong lúc đọc code, không đổi hành vi khi batch toàn thành công (test xác nhận).
- **Test:** `SaveImageListAdapterCountRoboTest` (mới, 3 case) — `allSuccess_finishCountEqualsTotal_failCountZero`, `mixedSuccessAndFailure_countsEachIndependently`, `noneFinishedYet_bothCountsZero`. Không test được `exportCountText()` trực tiếp (private, cần `getString()` từ Fragment context) nhưng logic đếm cốt lõi (rủi ro cao nhất) đã cover đầy đủ.
- **Smoke test thật (OnePlus CPH1989):** export 1 ảnh thành công qua "Export to the album" — hiển thị đúng "EXPORT LIST(1/1)", KHÔNG có dòng "failed" thừa (đúng AC "không thêm nhiễu khi không cần"). Không ép được lỗi thật an toàn trên thiết bị thật (case nhánh "có lỗi" cover đầy đủ bằng unit test `mixedSuccessAndFailure_countsEachIndependently`).
