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

## Bug phát sinh #2 sau khi bật pinch: "slide slider rồi pinch thì pinch không work"
Người dùng test thật phát hiện: dùng slider `TextSizePbFragment` đặt `textSize` trực tiếp, sau đó pinch lại thì watermark không đổi kích thước (hoặc nhảy vọt/kẹt).

**Nguyên nhân xác nhận qua đọc code**: `scaleListener.mScaleFactor` là biến tích luỹ nhân dồn xuyên suốt VÒNG ĐỜI VIEW (không phải theo từng phiên pinch) — dòng reset `mScaleFactor = 1f` trong `onScaleEnd()` bị comment sẵn từ trước. Slider ghi `config.textSize` trực tiếp qua path khác (ViewModel → DataStore → Flow), hoàn toàn không đụng tới `mScaleFactor`. Khi pinch lại, công thức `onScale()` dùng `mScaleFactor` CŨ (có thể đã gần sát biên 0.1/5.0 từ phiên pinch trước đó) làm hệ số nhân lên `textSize` MỚI (do slider vừa đặt) — kết quả tính sai, dễ vọt qua `MAX_TEXT_SIZE`/`MIN_TEXT_SIZE` ngay bước đầu và bị early-return (giữ nguyên, "không work").

**Fix**: thêm override `onScaleBegin()` — chụp `config.textSize` hiện tại làm `baselineTextSize` VÀ reset `mScaleFactor = 1f` ngay khi bắt đầu MỖI phiên pinch mới (bất kể `textSize` đến từ đâu — slider hay phiên pinch trước). `onScale()` đổi sang nhân với `baselineTextSize` cố định trong phiên thay vì `config?.textSize` đọc lại mỗi frame.

**Kiểm chứng**: log logcat thật trên Tecno KJ7 xác nhận đúng hành vi mới — mỗi phiên pinch mới bắt đầu bằng `onScale 1.0, textSize: X ==> X` (mScaleFactor reset đúng, baseline lấy đúng giá trị hiện tại), tiến triển mượt qua các frame tiếp theo, không nhảy vọt. Người dùng tự test kịch bản gốc (slide → pinch) trên device thật, xác nhận **đã hết bug**, pinch hoạt động bình thường sau khi dùng slider.

**Còn lại**: người dùng xác nhận pinch vẫn "rất lag" — đúng như dự đoán, đây là chi phí rebuild `buildIconBitmapShader`/`buildTextBitmapShader` (cấp phát bitmap mới) mỗi frame `onScale`, KHÔNG phải do bug vừa fix. Thuộc phạm vi `ENH-02`, chưa làm.
