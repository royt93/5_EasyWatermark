---
id: IDEA-04
type: Idea
effort: XL
sources: Codex, Claude, Internal (3/4)
files: []
---

# Cloud sync Brand Kit đa thiết bị

## Mô tả
Đồng bộ bộ nhận diện thương hiệu (logo, màu, margin chuẩn, preset watermark) qua cloud giữa nhiều thiết bị của cùng 1 nhiếp ảnh gia/studio. Hướng mở rộng ("Brand Guardian" cho team): quản trị viên khoá cấu hình chuẩn, tự động audit output trước khi xuất, đảm bảo hàng nghìn ảnh từ nhiều thiết bị vẫn tuân thủ cùng guideline.

## Vì sao đáng làm
Mở rộng tự nhiên từ tính năng Template local đã có, nhắm vào nhóm khách hàng trả phí cao hơn (studio/team) thay vì chỉ người dùng cá nhân — hướng monetization rõ ràng.

## Rủi ro / cân nhắc
Cần hạ tầng backend (đã có ghi chú TODO Firebase trong `doc/todo.md`) — effort cao, phụ thuộc quyết định có đầu tư backend hay không. Nên coi là hướng dài hạn sau khi backend cơ bản (`FEAT-05` cục bộ trước) đã ổn định.

## Acceptance Criteria (sơ bộ, cần thiết kế backend riêng)
- [ ] Đăng nhập/liên kết tài khoản đồng bộ được cấu hình cơ bản (logo + màu + template) giữa 2 thiết bị.
- [ ] Thay đổi trên 1 thiết bị phản ánh đúng trên thiết bị còn lại trong thời gian hợp lý.
