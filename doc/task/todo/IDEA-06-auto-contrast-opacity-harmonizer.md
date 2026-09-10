---
id: IDEA-06
type: Idea
effort: M
sources: Internal, Agy (2/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/BitmapUtils.kt
---

# Auto-contrast/opacity harmonizer theo từng ảnh

## Mô tả
Tự động phân tích độ sáng (luminance) và mật độ chi tiết tại đúng toạ độ đặt watermark của TỪNG ảnh trong batch, từ đó tự động đảo màu chữ (sáng/tối) và tinh chỉnh opacity phù hợp — đảm bảo hàng trăm ảnh với điều kiện sáng khác nhau đều có watermark rõ nét mà không bị chìm vào nền.

## Vì sao đáng làm
Effort thấp hơn nhiều so với các idea khác trong nhóm này (không cần ML model, chỉ cần tính luminance vùng ảnh — có thể tái dùng logic Palette đã có ở `applyBg()`) nhưng giải quyết vấn đề UX thực tế rất phổ biến khi batch nhiều ảnh có độ sáng khác nhau.

## Triển khai (gợi ý sơ bộ)
- Với vùng ảnh nằm dưới watermark, tính luminance trung bình (có thể tái dùng `Palette` API đã dùng ở `applyBg()`).
- Map luminance → chọn màu chữ sáng/tối + opacity phù hợp, cho phép override thủ công nếu user không thích kết quả tự động.

## Acceptance Criteria
- [ ] Batch ảnh có độ sáng nền khác nhau tại vị trí watermark tự động chọn màu/opacity đọc rõ.
- [ ] Có toggle bật/tắt (một số user muốn giữ đồng nhất màu watermark theo brand, không muốn tự động đổi).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-06`, file ticket = `todo/IDEA-06-auto-contrast-opacity-harmonizer.md`.
