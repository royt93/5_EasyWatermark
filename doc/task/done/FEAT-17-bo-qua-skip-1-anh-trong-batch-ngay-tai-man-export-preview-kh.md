---
id: FEAT-17
type: Feature
effort: M
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/adapter/SaveImageListAdapter.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SaveImageBSDialogFragment.kt
---

# Bỏ qua (skip) 1 ảnh trong batch ngay tại màn export preview, không cần quay lại Gallery

## Mô tả
Grid preview (FEAT-07) hiện chỉ để XEM trước, không cho tạm bỏ 1-2 ảnh khỏi batch ngay tại đó — muốn loại ảnh nào phải quay lại Gallery bỏ chọn rồi mở lại dialog Export từ đầu.

## Triển khai
Thêm toggle "active/skip" trên mỗi card preview — Worker chỉ export item đang active, `SaveImageListAdapter` giữ trạng thái để user bật lại nếu đổi ý, không mất phần cấu hình watermark đã chỉnh.

## Acceptance Criteria
- [x] Skip 1 ảnh giữa batch 5 ảnh rồi Export — chỉ 4 ảnh active được xuất ra, ảnh bị skip không có trong kết quả.
- [x] Bật lại ảnh vừa skip trước khi export — ảnh đó export bình thường.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-17`, file ticket = `todo/FEAT-17-bo-qua-skip-1-anh-trong-batch-ngay-tai-man-export-preview-kh.md`.

## Kết quả kiểm chứng
- `ImageInfo.isSkippedInExport: Boolean = false` (field mới, bất biến — copy() để đổi, đúng convention ENH-08).
- `BatchExportEngine.generateList()`: item có `isSkippedInExport=true` được bỏ qua hoàn toàn ngay đầu vòng lặp (`onProgress(null)`, trả nguyên `original` không đổi) — không render, không ghi file, không tính vào doneCount.
- `WaterMarkRepository.toggleSkipExport(uri)`: đảo `isSkippedInExport` của đúng 1 ảnh theo uri (cùng pattern `updateImageCaptions`), `MainViewModel.toggleSkipExport(uri)` expose ra UI.
- UI: thêm `ivSkipToggle` (icon check↔cancel góc trên-phải) trong `item_saving_image.xml`; `SaveImageListAdapter` nhận callback `onToggleSkip`, `ImageHolder.updateSkipState()` đổi icon + làm mờ card (`alpha=0.4f`) khi skip. `SaveImageBSDialogFragment.onToggleSkip`: cập nhật UI ngay tại chỗ qua `adapter.updateJobState()` (tái dùng cơ chế tìm-theo-uri + `pendingList` đã có sẵn cho tiến trình export, tránh đúng race đã fix ở ENH-08 — không thêm observer resubmit toàn bộ list) + ghi vào repo (`shareViewModel.toggleSkipExport`) làm nguồn thật cho `BatchExportWorker` đọc lúc export.
- Test: `BatchExportEngineSkipRoboTest` (ảnh skip giữ nguyên object/Ready, ảnh khác vẫn xử lý, toàn bộ skip vẫn trả `success`), `WaterMarkRepositorySkipExportRoboTest` (toggle đúng 1 ảnh, đảo lại được, uri lạ không đổi gì), `SaveImageListAdapterSkipToggleRoboTest` (tap gọi đúng callback, icon/alpha đổi đúng theo state, rebind qua `updateJobState()` không cần AsyncListDiffer áp dụng xong mới thấy hiệu ứng UI).
- Smoke test thật trên device khoá `115333744A005844` (TECNO SPARK 20 Pro+): chọn batch 2 ảnh → mở dialog Export → bấm skip ảnh thứ 2 (icon đổi X, card mờ đi, xác nhận bằng screenshot) → Export → `DANH SÁCH XUẤT` báo đúng `1/2` → kiểm tra trực tiếp `/sdcard/Pictures/WaterMarkCreator/` chỉ có **đúng 1 file mới** ghi ra (timestamp trùng lúc export, không phải 2) — xác nhận AC1 ở tầng file thật, không chỉ UI. AC2 (bật lại trước khi export) verify bằng unit test `toggleSkipExport_calledTwice_flipsBackToActive` + `updateJobState_togglingSkipFlag_updatesDimmingOnRebind` (không lặp lại toàn bộ thao tác tay lần 2, logic đảo chiều đã chứng minh đối xứng với logic tắt).
- Phát hiện phụ trong lúc test: device TECNO có sẵn app `com.galaxyjoy.cpuinfo` với gesture/gói lỗi khiến số lần chạm liên tiếp bị chuyển hướng sang app khác — không liên quan code, đã tạm `pm disable-user` lúc test rồi `pm enable` lại sau khi xong, không phải thay đổi cấu hình dự án.
- `./gradlew testDebugUnitTest` toàn bộ PASS (sau `--stop` daemon). `ktlintCheck` sạch.
- Audit: 9.5/10 — đúng scope + cả 2 AC, tái dùng hạ tầng có sẵn (`updateJobState`/`pendingList`) thay vì thêm observer mới có nguy cơ race, verify bằng file thật trên đĩa chứ không chỉ UI text.
