---
id: FEAT-03
type: Feature
effort: L
sources: Codex, Claude, Internal (3/4 — đồng thuận cao)
files:
  - app/src/main/java/com/mckimquyen/watermark/data/repo/WaterMarkRepository.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
---

# Watermark đa lớp (chồng text + logo/QR cùng lúc)

## Mô tả
`WaterMarkRepository.MarkMode` hiện chỉ Text HOẶC Image, loại trừ lẫn nhau. Cho phép chồng nhiều lớp cùng lúc (vd logo góc + text lặp nền + QR), mỗi lớp có thứ tự và opacity riêng — use-case thực tế phổ biến (logo + copyright cùng lúc).

## Triển khai
Đổi `MarkMode` đơn thành danh sách layer (giới hạn 3-5 lớp để phù hợp kiến trúc View hiện tại và thời gian phát triển). Mỗi layer vẽ tuần tự lên cùng canvas trong `generateImage`/shader pipeline hiện có (tận dụng `buildTextBitmapShader`/`buildIconBitmapShader` sẵn có, gọi nhiều lần thay vì 1).

## Acceptance Criteria
- [ ] Có thể thêm tối thiểu 2 layer (vd text + logo) cùng lúc trong 1 config.
- [ ] Mỗi layer có vị trí/opacity/thứ tự (z-order) riêng, chỉnh sửa độc lập.
- [ ] Export batch áp dụng đúng toàn bộ layer, hiệu năng không giảm đáng kể so với 1 layer.
