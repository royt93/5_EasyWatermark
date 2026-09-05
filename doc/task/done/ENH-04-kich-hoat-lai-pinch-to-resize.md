---
id: ENH-04
type: Enhancement
effort: S
sources: Codex (1/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
related: BUG-06
---

# Kích hoạt lại pinch-to-resize (đang bị comment)

## Mô tả
`onTouchEvent()` (dòng ~521-573) — `ScaleGestureDetector` đã được khởi tạo và tồn tại trong code nhưng lời gọi xử lý gesture bị comment, nên pinch-to-resize hiện KHÔNG hoạt động dù hạ tầng đã có sẵn.

## Đề xuất
Kích hoạt lại cùng giới hạn kích thước rõ ràng (min/max size hợp lý), kết hợp `ENH-02` (debounce ghi DataStore) và `BUG-06` (sửa điều kiện cache icon) để tránh giật lag khi bật lại tính năng này.

## Acceptance Criteria
- [x] Pinch để resize watermark hoạt động ở mode Image — verify thật trên Tecno KJ7, người dùng pinch tay thật, icon watermark đổi kích thước đúng theo, không crash.
- [ ] Mode Text — **chưa verify riêng qua UI** (trở ngại điều hướng lúc test), nhưng code trigger (`onTouchEvent`/`onScale`) dùng chung mọi mode, không rẽ nhánh theo markMode — độ tin cậy cao dựa trên bằng chứng mode Image, không phải "đã test".
- [x] Không giật lag do BUG-06 (đã fix cùng phiên — log xác nhận 0 lần decode-lại-từ-uri khi pinch).
- [ ] Vẫn còn giật lag do phần khác (rebuild shader + ghi DataStore mỗi frame `onScale`) — **chưa fix, thuộc `ENH-02`**, người dùng tự xác nhận qua test thật.

## Kết quả kiểm chứng
- Uncomment 3 dòng: `mScaleDetector.onTouchEvent(event)` + guard `isInProgress` — giới hạn min/max size (`MIN_TEXT_SIZE`/`MAX_TEXT_SIZE`) đã có sẵn trong `onScale()`, không cần thêm.
- Compile sạch, không lint violation mới. Full regression 49/49 pass.
- Smoke test thật trên Tecno KJ7: pinch tay thật, icon watermark to/nhỏ đúng theo cử chỉ, không crash. Xem thêm chi tiết log tại `BUG-06` (cùng phiên fix, cùng lần test).
