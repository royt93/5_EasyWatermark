---
id: IDEA-05
type: Idea
effort: XL
sources: Codex, Claude, Internal (3/4)
files: []
---

# Chợ template cộng đồng (network effect)

## Mô tả
Cho phép người dùng chia sẻ/tải preset watermark (font, layout, khung EXIF style, cấu hình `WaterMark` dạng JSON) của nhau qua backend nhẹ, có rating — biến tính năng Template local sẵn có thành mạng lưới cộng đồng, tạo network effect thay vì chỉ là công cụ đơn lẻ.

## Vì sao đáng làm
Khác biệt hẳn so với đối thủ (không app watermark phổ thông nào có template marketplace) — tạo lý do user quay lại app thường xuyên hơn thay vì dùng 1 lần rồi quên.

## Rủi ro / cân nhắc
Effort cao nhất trong nhóm ý tưởng (cần backend, moderation nội dung chia sẻ, vấn đề bản quyền/tên thương hiệu trong preset chia sẻ). Nên là hướng xa, chỉ cân nhắc sau khi `FEAT-05`/`FEAT-06` (backup/profile local) đã hoàn thiện và có traction người dùng.

## Acceptance Criteria (sơ bộ, cần thiết kế sản phẩm + backend riêng)
- [ ] User export được 1 template thành định dạng chia sẻ được (JSON).
- [ ] Có nơi (trong app) duyệt/tải template người khác chia sẻ.
- [ ] Có cơ chế kiểm duyệt cơ bản (tránh nội dung vi phạm bản quyền/không phù hợp).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-05`, file ticket = `todo/IDEA-05-cho-template-cong-dong.md`.
