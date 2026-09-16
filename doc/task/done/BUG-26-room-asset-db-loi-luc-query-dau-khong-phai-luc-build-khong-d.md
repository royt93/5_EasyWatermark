---
id: BUG-26
priority: P1
type: Bug
effort: S
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/di/AppModule.kt
  - app/src/main/java/com/mckimquyen/watermark/data/repo/TemplateRepository.kt
---

# Room asset DB lỗi lúc query đầu (không phải lúc `build()`) không được try/catch bảo vệ

## Mô tả
`AppModule.provideYourDatabase()` try/catch chỉ bọc `builder.build()` — Room mở kết nối thật/copy asset DB (`createFromAsset`) LAZY tại QUERY ĐẦU TIÊN, không phải lúc `build()`. `TemplateRepository.getAllTemplate()`/`insertTemplate()`/`deleteTemplate()`/`updateTemplate()` gọi thẳng `templateDao?.xxx()` không có try/catch nào — nếu asset DB hỏng/thiếu, crash ngay tại query đầu tiên thay vì được null-safe như thiết kế `checkIfIsDaoNull()` đang cố làm.

## Triển khai
Bọc try/catch ở `TemplateRepository` quanh các lời gọi `templateDao?.xxx()`, trả kết quả rỗng/thất bại an toàn thay vì để exception văng ra UI layer.

## Acceptance Criteria
- [x] Giả lập asset DB hỏng (xoá/đổi tên file asset trong test) — `TemplateRepository.getAllTemplate()` không crash, trả Flow rỗng thay vì exception.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-26`, file ticket = `todo/BUG-26-room-asset-db-loi-luc-query-dau-khong-phai-luc-build-khong-d.md`.

## Kết quả kiểm chứng (2026-09-16)

**Fix**: `TemplateRepository.kt` — `getAllTemplate()` thêm `.catch { emit(listOf()) }` (chặn exception xảy ra lúc COLLECT Flow, đúng chỗ Room thực sự chạy query lazy lần đầu); `insertTemplate()`/`deleteTemplate()`/`updateTemplate()` bọc try/catch quanh `templateDao?.xxx()`, log lỗi thay vì để exception văng ra `viewModelScope`/UI layer. Cả 3 hàm suspend đổi sang khai báo trả về `Unit` tường minh (tránh Kotlin suy luận kiểu trả về mơ hồ từ nhánh try/catch khác nhau).

**Unit test mới**: `TemplateRepositoryCorruptedDbTest.kt` (pure JUnit) — fake `TemplateDao` (`ThrowingTemplateDao`) ném `SQLiteException` ở cả 4 method, mô phỏng đúng tình huống asset DB hỏng mà không cần Robolectric/DB thật. 5 test: 4 case DAO throw (đều không crash) + 1 case `templateDao == null` (đã hoạt động từ trước, giữ để chống regression).
- Đã verify test THẬT SỰ bắt được bug: tạm revert fix (khôi phục code gốc không try/catch) → 4/5 test FAILED với đúng `SQLiteException` ném ra không bị bắt. Khôi phục fix → toàn bộ PASS lại.
- Full suite: `./gradlew testAppReleaseDebugUnitTest` — 284 tests, 0 failures, 0 skipped. `ktlintCheck` — BUILD SUCCESSFUL (sau 1 lần `ktlintFormat` tự sửa thứ tự import).

**Smoke test trên device thật** (OPPO CPH1989, serial `FUJZIFIR7DQCNRWW` — device đã khóa từ BUG-26, không đổi trong ticket này):
- Cài APK debug, mở app, chọn ảnh, vào Text Watermark editor → Edit watermark → Template List: danh sách load thành công (1 template có sẵn hiển thị đúng) — xác nhận `getAllTemplate()` Flow hoạt động bình thường trên DataStore/Room thật, không regression.
- Tap "Add" để tạo template mới, gõ text — dialog "Edit Template" mở nhưng do bottom-sheet lồng bottom-sheet + bàn phím ảo che gần hết màn hình, không thể quan sát trực quan bước "Confirm" cuối để xác nhận `insertTemplate()` chạy qua UI thật (giới hạn thao tác adb mù trên UI lồng nhau, không phải lỗi code). Đã đóng toàn bộ dialog bằng nhiều lần BACK — app KHÔNG crash, PID (`2152`) ổn định xuyên suốt, `adb logcat -d "*:E"` không có `FATAL`/exception nào từ package `com.mckimquyen.watermark` trong toàn bộ quá trình (kể cả lúc gặp quảng cáo Compass promo xen giữa — đã dừng theo R4, chờ user đóng ad rồi tiếp tục).
- Giữa chừng smoke test gặp interstitial ad (Compass) — đã dừng, thông báo, chờ user xác nhận "done" trước khi tiếp tục, đúng theo quy tắc R4.

**Tự chấm điểm**: 9/10 — root cause đúng (phân biệt đúng thời điểm Room lazy-init vs `build()`), test tự-verify bằng revert (4/5 fail đúng dự đoán), full suite xanh, smoke test xác nhận golden-path Template List load thật trên device không crash. Trừ 1 điểm vì không xác nhận trực quan được bước cuối `insertTemplate()` qua UI thật (giới hạn thao tác UI lồng nhau qua adb, đã bù bằng unit test tái hiện đúng exception path cho cả 3 hàm ghi).
