---
id: IDEA-02
type: Idea
effort: XL
sources: Codex, Claude, Agy, Internal (4/4 — đồng thuận cao)
files: []
---

# Invisible watermark / steganography chống xoá

## Mô tả
Nhúng thêm 1 lớp watermark VÔ HÌNH (LSB/DCT/DWT frequency-domain) song song với watermark hiển thị bình thường, chứa hash/ID định danh chủ sở hữu. Lớp ẩn này cần sống sót qua các thao tác resize/nén nhẹ (khác LSB đơn giản dễ vỡ khi nén JPEG) — phục vụ nhiếp ảnh gia/dân stock-photo cần bằng chứng sở hữu kín đáo, khó bị content-aware fill xoá như watermark hiển thị.

## Vì sao đáng làm
Xuất hiện ở cả 4 nguồn — góc nhìn "chống trộm ảnh thực sự" khác biệt hẳn so với watermark hiển thị thông thường mà mọi app khác đều có. Đây là tính năng khó làm đúng (cần hiểu DCT/DWT, đánh đổi giữa độ bền vs không ảnh hưởng chất lượng ảnh) nên hiếm app watermark phổ thông triển khai — chính là điểm khác biệt.

## Rủi ro / cân nhắc
Effort rất cao (XL), cần R&D thuật toán DCT/DWT watermarking bền vững (không phải LSB đơn giản, vốn dễ vỡ khi nén JPEG/resize — nhiều report gộp chung 2 khái niệm này, cần làm rõ khi thiết kế). Nên coi là hướng dài hạn, không phải sprint gần.

## Acceptance Criteria (sơ bộ, cần thiết kế kỹ thuật riêng trước khi break-down chi tiết)
- [ ] Nghiên cứu khả thi: chọn thuật toán cụ thể (DCT-based robust watermarking) và đánh giá độ bền qua resize/nén JPEG chất lượng khác nhau.
- [ ] Watermark ẩn không ảnh hưởng nhận biết được chất lượng ảnh hiển thị (blind test).
- [ ] Có công cụ (trong app hoặc riêng) verify được watermark ẩn từ ảnh output.
