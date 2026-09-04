---
id: FEAT-06
type: Feature
effort: M
sources: Internal, Agy (2/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/data/db/
  - app/src/main/java/com/mckimquyen/watermark/data/repo/TemplateRepository.kt
---

# Watermark profile đầy đủ (không chỉ text, đặt tên tái dùng)

## Mô tả
`Template` (Room) hiện chỉ lưu nội dung text watermark. Thêm khả năng lưu/tái dùng TOÀN BỘ cấu hình `WaterMark` (font, màu, alpha, gap, degree, tile mode, icon...) thành các "profile" đặt tên (vd "Instagram", "Khách A"), chuyển đổi nhanh qua dropdown thay vì chỉnh tay lại từ đầu mỗi lần.

## Triển khai
Mở rộng schema `Template` (hoặc bảng mới `WatermarkProfile`) lưu toàn bộ field của `WaterMark`, thêm UI dropdown chọn nhanh profile trong editor.

## Acceptance Criteria
- [ ] Lưu được 1 profile chứa toàn bộ cấu hình watermark hiện tại (không chỉ text).
- [ ] Áp dụng lại profile khôi phục đúng toàn bộ cấu hình đã lưu.
- [ ] Danh sách profile hiển thị dễ chọn (dropdown/list có tên).
