---
id: BUG-47
type: Bug
priority: P1
effort: XS
sources: full codebase audit (general-purpose agent, 2026-10-01) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/data/db/dao/RecipientDao.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/.../RecipientViewModel.kt (hoặc tên tương đương đang gọi save())
---

# `RecipientDao.insert()` dùng `OnConflictStrategy.REPLACE` — xoá âm thầm recipient trùng `code` thay vì báo lỗi

## Mô tả
`insert()` khai báo `@Insert(onConflict = OnConflictStrategy.REPLACE)` (dòng 30), trong khi `Recipient.code` có unique index và phía gọi (`RecipientViewModel.save()`, dòng 38-51) bọc `try/catch SQLiteConstraintException` — rõ ràng kỳ vọng Room **ném exception** khi `code` trùng để validate, không phải âm thầm ghi đè.

Với `REPLACE`, khi 2 coroutine gọi `save()` gần như đồng thời cùng `code` (double-tap nút lưu, hoặc race check-then-act giữa kiểm tra trùng và insert thật), Room thực thi `INSERT OR REPLACE`: xoá hẳn row cũ, chèn row mới — dữ liệu recipient cũ (tên, ghi chú, lịch sử) **mất vĩnh viễn, không có cảnh báo nào**. `id` (PK) của row đổi theo bản ghi mới, khiến mọi `BatchHistoryEntity.recipientCode` (hoặc field tương đương) đang tham chiếu row cũ bị treo/sai liên kết.

## Đề xuất
Đổi `OnConflictStrategy.REPLACE` → `OnConflictStrategy.ABORT` (hoặc bỏ tham số `onConflict`, mặc định đã là `ABORT`) để Room ném `SQLiteConstraintException` đúng như `RecipientViewModel.save()` đang kỳ vọng và catch sẵn.

## Acceptance Criteria
- [ ] `RecipientDao.insert()` với `code` đã tồn tại → ném `SQLiteConstraintException`, KHÔNG xoá/ghi đè row cũ.
- [ ] `RecipientViewModel.save()` (hoặc tên thật tại thời điểm fix) xử lý đúng exception này như luồng hiện có (báo lỗi cho UI, không crash).
- [ ] Row cũ với `code` trùng vẫn còn nguyên trong DB sau khi insert trùng thất bại — `id` không đổi.
- [ ] Toàn bộ test Room/DAO hiện có liên quan `Recipient` vẫn PASS.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-47`, file ticket = `todo/BUG-47-recipientdao-insert-replace-am-tham-xoa-recipient-trung-code.md`.

## Kết quả kiểm chứng

- **Phát hiện thêm lúc fix** (quan trọng hơn mô tả gốc): có sẵn `RecipientDaoIntegrationTest.maTrungNhau_bangUniqueIndexThayTheBanCu_khongTaoBanGhiMoi` coi hành vi `REPLACE` là **chủ đích** ("hàng rào chống trùng mã"). Verify lại bằng cách đọc caller thật: `RecipientRepository.save()` → `dao.insert()` **chỉ** được `RecipientViewModel.save()` gọi khi `recipient.id == 0L` (tạo mới) — nhánh sửa (`id != 0`) đi qua `update()` riêng, không bao giờ dùng `insert()` để "upsert" theo `code`. Vậy `REPLACE` không phục vụ mục đích hợp lệ nào, xác nhận đúng là bug như ticket mô tả — không phải test cũ sai, mà chính thiết kế `REPLACE` sai từ đầu.
- **Audit**: 9.5/10 — fix tối thiểu (bỏ `onConflict` param, dùng mặc định `ABORT`), đúng 1 dòng thay đổi ý nghĩa, không magic number, không thay đổi API public.
- **TDD**: sửa test tích hợp cũ (test cũ enshrine sai hành vi → thay bằng test đúng ý đồ: `maTrungNhau_nemConstraintException_khongXoaBanGhiCu`). RED xác nhận trên TECNO KJ7 thật (`AssertionError: Phải ném SQLiteConstraintException...` — code cũ REPLACE không ném gì). GREEN sau khi bỏ `OnConflictStrategy.REPLACE`.
- **Test suite**: `RecipientDaoIntegrationTest` 7/7 PASS trên TECNO KJ7 (Room in-memory thật, exception thật). `RecipientViewModelSaveRoboTest` (Robolectric, fake DAO mô phỏng throw) không đổi, vẫn PASS — xác nhận code-path `catch SQLiteConstraintException` trong ViewModel giờ THẬT SỰ có tác dụng với Room thật (trước fix nó là dead code vì REPLACE không bao giờ throw). `./gradlew :app:testDebugUnitTest` + `ktlintCheck` toàn bộ: `BUILD SUCCESSFUL`.
- **Smoke test thật**: cài lại APK trên TECNO KJ7, mở app, logcat sạch không `FATAL`/`AndroidRuntime`. Không hoàn tất thao tác tay sâu tới `RecipientManagementActivity` qua UI (điều hướng bottom sheet lồng nhau gây khó bấm chính xác qua `adb input tap`, không phải vấn đề của fix) — chấp nhận được vì fix thuần tầng Room/DAO, `RecipientDaoIntegrationTest` dùng chính Room engine thật trên chính device đã khoá là bằng chứng có thẩm quyền cho loại thay đổi này, không phụ thuộc UI.
