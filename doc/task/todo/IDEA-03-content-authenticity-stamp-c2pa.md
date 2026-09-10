---
id: IDEA-03
type: Idea
effort: L
sources: Codex, Claude, Internal (3/4)
files: []
---

# Content authenticity stamp kiểu C2PA

## Mô tả
Mỗi ảnh xuất ra chứa watermark hiển thị bình thường KÈM payload xác minh (chữ ký số/hash) nhúng vào EXIF/metadata, liên kết hash ảnh gốc + thời gian + chủ sở hữu — theo hướng chuẩn C2PA (Content Credentials) đang được ngành nhiếp ảnh/báo chí quan tâm để chống ảnh giả mạo/AI-generated. Người xem/1 trang scanner có thể kiểm tra ảnh đã bị chỉnh sửa sau khi phát hành hay chưa.

## Vì sao đáng làm
Hợp lý với đối tượng nhiếp ảnh gia đang lo bị đánh cắp ảnh cho AI training hoặc bị chỉnh sửa giả mạo — xu hướng ngành đang đi theo hướng này (Adobe, Leica, Nikon đã hỗ trợ C2PA phần cứng/phần mềm).

## Triển khai (gợi ý sơ bộ)
- Nhúng metadata chuẩn (hoặc rút gọn, không cần full C2PA spec phức tạp ban đầu) vào EXIF/XMP khi export.
- Cân nhắc dùng key ký cục bộ trên máy (không cần server) cho bản MVP, nâng cấp lên có server xác thực sau nếu cần độ tin cậy cao hơn.

## Acceptance Criteria (sơ bộ)
- [ ] Ảnh export chứa metadata xác thực (hash + timestamp + owner ID) đọc lại được.
- [ ] Có cách kiểm tra (trong app hoặc công cụ riêng) xác minh ảnh chưa bị chỉnh sửa kể từ lúc xuất.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-03`, file ticket = `todo/IDEA-03-content-authenticity-stamp-c2pa.md`.
