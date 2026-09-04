---
id: FEAT-11
type: Feature
effort: S
sources: Agy (1/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
---

# Hiệu ứng viền/bóng/nền pill cho text watermark

## Mô tả
Bổ sung tuỳ chọn viền tương phản (outline stroke), đổ bóng (drop shadow), hoặc nền mờ dạng viên thuốc (background pill) cho text watermark, giúp chữ luôn dễ đọc trên mọi nền ảnh sáng/tối phức tạp — vấn đề UX thực tế khi ảnh nền không đồng nhất.

## Triển khai
Mở rộng `TextPaint`/`buildTextBitmapShader` (`WaterMarkImageView.kt`) hỗ trợ thêm `Paint.setShadowLayer()` cho shadow, vẽ thêm layer stroke/pill nền trước khi vẽ text chính.

## Acceptance Criteria
- [ ] Có tối thiểu 2 hiệu ứng mới (vd stroke + shadow) chọn được độc lập hoặc kết hợp.
- [ ] Text watermark trên ảnh nền phức tạp (nhiều màu/độ tương phản thấp) vẫn đọc rõ khi bật hiệu ứng.
