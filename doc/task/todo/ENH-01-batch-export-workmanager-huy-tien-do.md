---
id: ENH-01
type: Enhancement
effort: L
sources: Claude, Agy, Internal (3/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
---

# Batch export chạy qua WorkManager + huỷ + tiến độ tổng

## Mô tả
Batch export hiện chạy trong `viewModelScope` (`saveImage`/`generateList`) — job bị hệ thống throttle/kill khi app xuống nền (Doze, background limits), và không có cách huỷ giữa chừng hay resume ảnh lỗi. Với batch lớn (hàng chục-hàng trăm ảnh), đây là điểm yếu UX rõ rệt.

## Đề xuất
- Chuyển export sang `WorkManager` (hoặc tối thiểu foreground service) để job sống sót khi app xuống nền.
- Emit tiến độ tổng (x/y ảnh xong) qua notification + trong app.
- Cho phép huỷ giữa chừng, và (liên quan `BUG-03`) hiển thị rõ số ảnh thành công/thất bại khi kết thúc.

## Acceptance Criteria
- [ ] Batch export tiếp tục chạy khi app bị đưa xuống nền.
- [ ] Có nút huỷ giữa batch.
- [ ] Notification hiển thị tiến độ %.
