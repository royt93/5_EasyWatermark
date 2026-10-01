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
