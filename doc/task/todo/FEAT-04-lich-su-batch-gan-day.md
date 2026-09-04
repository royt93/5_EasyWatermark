---
id: FEAT-04
type: Feature
effort: M
sources: Codex (1/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
  - app/src/main/java/com/mckimquyen/watermark/data/db/
---

# Lịch sử batch export gần đây

## Mô tả
Thêm màn hình lịch sử các batch export gần đây — lưu cấu hình đã dùng, danh sách file output, và danh sách ảnh lỗi (liên quan `BUG-03` — cần biết chính xác ảnh nào fail) — cho phép mở lại thư mục, chia sẻ, hoặc chạy lại batch.

## Triển khai
Chỉ cần lưu metadata nhẹ (không nhân bản ảnh input): thêm bảng Room mới (vd `BatchHistory`) lưu timestamp, config snapshot, danh sách URI output + trạng thái từng ảnh.

## Acceptance Criteria
- [ ] Sau mỗi batch export, có 1 entry lịch sử mới xuất hiện.
- [ ] Mở lại entry cũ xem được danh sách ảnh output + ảnh lỗi (nếu có).
- [ ] Có nút "chạy lại" áp dụng lại đúng config batch cũ.
