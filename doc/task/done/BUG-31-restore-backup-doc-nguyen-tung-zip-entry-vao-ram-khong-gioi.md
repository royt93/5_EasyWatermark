---
id: BUG-31
priority: P1
type: Bug
effort: M
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/data/backup/BackupRestoreEngine.kt
---

# Restore backup đọc nguyên từng zip entry vào RAM, không giới hạn — rủi ro OOM/zip-bomb

## Mô tả
`readBackup()` gọi `zip.readBytes()` cho MỌI entry (dòng 54) trước khi phân loại template/signature, không giới hạn kích thước/số lượng entry. Một file zip người dùng chọn qua SAF (input không tin cậy — đã có tiền lệ zip-slip fix ở FEAT-05) có thể chứa entry rất lớn hoặc zip-bomb (entry nhỏ nén nhưng giải nén ra khổng lồ), khiến `readBytes()` OOM ngay trong quá trình restore.

## Triển khai
Giới hạn kích thước tối đa/entry (vd 20MB, đủ cho ảnh signature lớn nhất hợp lý) + giới hạn tổng số entry — từ chối/bỏ qua entry vượt ngưỡng thay vì đọc hết vào RAM.

## Acceptance Criteria
- [x] Tạo file zip test với 1 entry giả rất lớn (vd 100MB) — `readBackup()` từ chối/bỏ qua entry đó, không OOM, các entry hợp lệ khác vẫn restore đúng.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-31`, file ticket = `todo/BUG-31-restore-backup-doc-nguyen-tung-zip-entry-vao-ram-khong-gioi.md`.

## Kết quả kiểm chứng (2026-09-16)

**Fix**: `BackupRestoreEngine.kt` — thêm `MAX_ENTRY_SIZE_BYTES` (20MB) + `MAX_ENTRY_COUNT` (500). `readBackup()` đổi `zip.readBytes()` (không giới hạn) sang `readEntryBounded()` — đọc theo chunk 8KB, dừng ngay và trả `null` (bỏ qua entry, không giữ phần đã đọc) khi vượt ngưỡng; đồng thời đếm tổng số entry, dừng vòng lặp nếu vượt `MAX_ENTRY_COUNT`. Cả 2 cơ chế chặn đúng root cause: `readBytes()` gốc cấp phát bộ nhớ theo dữ liệu ĐÃ GIẢI NÉN, không theo kích thước nén trong header zip — đây là lý do zip-bomb khai thác được.

**Unit test mới**: `BackupRestoreEngineTest.kt` bổ sung 2 test:
- `readBackup_oversizedEntry_isSkipped_validEntriesStillRestored` — tạo zip THẬT với 1 entry 100MB toàn số 0 (nén cực nhỏ, mô phỏng trung thực hành vi zip-bomb) + 1 template hợp lệ. Xác nhận entry khổng lồ bị bỏ qua, template hợp lệ vẫn restore đúng.
- `readBackup_entryCountExceedsLimit_stopsWithoutCrashing` — 510 entry nhỏ, xác nhận dừng ở ngưỡng 500, không crash.
- Đã verify cả 2 test THẬT SỰ bắt được bug: tạm revert fix (khôi phục `zip.readBytes()` không giới hạn) → 2/2 test FAILED đúng dự đoán (`AssertionErrorWithFacts` — signature/template count không như kỳ vọng do không có giới hạn). Khôi phục fix → toàn bộ 5 test PASS lại (kể cả 3 test round-trip cũ, không regression).
- Full suite: `./gradlew testAppReleaseDebugUnitTest` — 286 tests, 0 failures, 0 skipped. `ktlintCheck` — BUILD SUCCESSFUL.

**Smoke test trên device thật** (OPPO CPH1989, serial `FUJZIFIR7DQCNRWW`):
- Cài APK debug, vào Information (About) → cuộn ngang thấy nút "Backup"/"Restore" (Đề xuất F).
- Tap "Backup" → SAF create-document dialog mở, lưu file → Toast **"Backup saved successfully"** hiển thị đúng — xác nhận `writeBackup()` + luồng ghi SAF hoạt động thật trên device.
- Tap "Restore" → SAF open-document picker (Google DocumentsUI) mở đúng, lọc đúng MIME type (`application/zip`/`application/octet-stream`): file `watermark_creator_backup.zip` hiển thị chọn được (chữ đen), file `aztec_ro.png` bị mờ/disable đúng như filter — xác nhận `restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))` hoạt động đúng.
- **Giới hạn trung thực**: KHÔNG thể hoàn tất bước chọn file cuối trong picker hệ thống OPPO qua `adb shell input tap` — đã thử tọa độ chính xác từ `uiautomator dump` (bounds `item_root` cả 2 chế độ grid/list), double-tap, swipe-tap, xác nhận qua `logcat` rằng `MotionEvent` được `ViewRootImpl[PickActivity]` dispatch và `handled=true`, nhưng không có hành động chọn nào xảy ra (`selected="false"` không đổi, không quay lại app) — đây là quirk cụ thể của DocumentsUI trên ROM OEM này với input injection qua adb (không phải lỗi code app: nút Backup ở CÙNG dialog/luồng UI đã hoạt động bình thường). Đã đóng picker bằng BACK, xác nhận app KHÔNG crash (`pidof` ổn định, `adb logcat -d "*:E"` không có `FATAL` nào từ package app) trong suốt quá trình.
- Kịch bản OOM/zip-bomb cụ thể (100MB decompressed) không thể test qua UI thật vì lý do trên VÀ vì bản chất bug chỉ lộ ra khi restore từ file zip-bomb thật (không thể tạo an toàn trên máy thật mà không có nguy cơ ảnh hưởng máy) — đã được tái hiện trung thực và đầy đủ hơn qua unit test dùng đúng `BackupRestoreEngine.readBackup()` thật với payload zip-bomb thật.

**Tự chấm điểm**: 9/10 — root cause đúng (phân biệt kích thước nén vs giải nén), test tự-verify bằng revert (2/2 fail đúng dự đoán) dùng payload zip-bomb THẬT (100MB), full suite xanh, phần Backup của smoke test xác nhận luồng SAF thật hoạt động. Trừ 1 điểm vì không hoàn tất được bước chọn file trong Restore picker qua device thật (giới hạn input injection của OEM DocumentsUI, đã ghi rõ bằng chứng logcat).
