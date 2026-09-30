---
id: ENH-38
type: Enhancement
priority: P2
effort: S
sources: /code-review --level high (review pass 12, 2026-09-30) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/export/stego/StegoCodec.kt
  - app/src/main/java/com/mckimquyen/watermark/export/stego/StegoPayload.kt
  - app/src/test/java/com/mckimquyen/watermark/export/stego/StegoRobustnessTest.kt
---

# `StegoCodec.decode()` — confidence luôn = 1.0 khi ảnh chỉ vừa đúng 1 vòng payload

## Mô tả
`decode()` tính `confidence` là tỉ lệ phiếu của phe thắng (`win/total`) trên mỗi bit, trung bình toàn payload — xem `StegoCodec.kt:133-140`. Khi ảnh chỉ vừa đủ chứa **1 vòng** payload (`votesTotal[i] == 1` cho mọi bit — đúng trường hợp ảnh nhỏ nhất được chấp nhận, 64x64px với `TOTAL_BITS=64`), công thức này LUÔN trả `1.0` bất kể dữ liệu đọc được có thật là watermark hay chỉ là quan hệ lớn-bé ngẫu nhiên của nhiễu ảnh — vì chỉ có 1 phiếu, phe "thắng" luôn thắng tuyệt đối 1/1.

`InvisibleWatermark.extract()` (dòng 50-51) và `StegoPayload.MIN_CONFIDENCE` (dòng 36-37) mô tả ngưỡng tin cậy là "tuyến phòng thủ thứ hai sau MAGIC+CRC" — nhưng ở đúng trường hợp ảnh nhỏ nhất, tuyến phòng thủ này không có tác dụng gì cả (luôn qua ngưỡng 0.90). Thực tế rủi ro gán nhầm chủ sở hữu vẫn thấp vì MAGIC(16 bit)+CRC(16 bit) độc lập chặn ở mức ~1/2^32 — nhưng doc comment đang mô tả sai hành vi thật của code.

## Đề xuất
Chọn 1 trong 2 hướng (cần quyết định, không tự ý chọn):
1. **Sửa lại doc comment** cho đúng thực tế (confidence chỉ có ý nghĩa khi `rounds >= 2`, tuyến phòng thủ thật là MAGIC+CRC) — rẻ, không đổi hành vi.
2. **Đổi công thức confidence** để phản ánh đúng độ tin cậy kể cả ở `rounds=1` — vd trộn thêm biên độ chênh lệch `|A|-|B|` thực tế (không chỉ tỉ lệ phiếu thắng/thua) — đắt hơn, cần đo lại số liệu qua `StegoRobustnessTest` để không phá robustness đã tune.

## Acceptance Criteria
- [ ] Ảnh 64x64px (đúng 1 vòng) đọc từ ảnh SẠCH (không nhúng gì) phải cho `confidence` phản ánh đúng "không đáng tin" (nếu chọn hướng 2), hoặc doc comment không còn mô tả sai (nếu chọn hướng 1).
- [ ] Toàn bộ `StegoRobustnessTest` hiện có vẫn PASS (không phá robustness JPEG q>=70 đã đo).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-38`, file ticket = `todo/ENH-38-stego-confidence-vo-tac-dung-o-anh-vua-du-1-vong.md`.
