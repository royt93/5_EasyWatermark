---
id: BUG-07
type: Bug
priority: P1
effort: S
sources: Agy, Codex (2/4, verify trực tiếp — điều chỉnh mô tả cho chính xác hơn báo cáo gốc)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
verified: true
---

# Text shader: `indexOf` sai dòng trùng lặp + kích thước bitmap có thể ≤0

## Mô tả
Đã đọc trực tiếp `buildTextBitmapShader()` (dòng 724-816), xác nhận 2 vấn đề:

1. **Dòng 738**: `val startIndex = config.text.indexOf(it).coerceAtLeast(0)` — dùng `String.indexOf(it)` để tìm vị trí của TỪNG dòng sau khi `split("\n")`. Nếu 2 dòng trong watermark trùng nội dung (hoặc có dòng rỗng `""`), `indexOf` luôn trả về vị trí của lần xuất hiện ĐẦU TIÊN, khiến `startIndex`/`endIndex` dùng để `measureText` sai lệch → `maxLineWidth` tính sai cho các dòng lặp lại.
2. **Dòng 775-777**: `finalWidth`/`finalHeight` (từ `adjustHorizontalGap`/`adjustVerticalGap` áp lên `fixWidth`/`fixHeight`) được truyền thẳng vào `Bitmap.createBitmap(finalWidth, finalHeight, ...)` **không** có `coerceAtLeast(1)` (khác với `textWidth`/`textHeight` ở dòng 758-759 đã được coerce). Nếu gap âm đủ lớn (so với kích thước text đã xoay), kết quả có thể ≤ 0 → `IllegalArgumentException: width and height must be > 0`, crash.
3. **Dòng 796-798**: `translate` theo chiều dọc dùng `staticLayout.getLineBottom(0) - getLineTop(0)` (chỉ dòng đầu tiên) thay vì tổng chiều cao toàn bộ `staticLayout` — với text nhiều dòng, canvas có thể bị căn giữa lệch theo trục dọc so với vị trí mong muốn.

## Cách fix đề xuất
- Thay `indexOf` bằng tính offset tuyến tính thực tế của từng dòng (cộng dồn độ dài + số ký tự `\n` đã đi qua), hoặc dùng trực tiếp `staticLayout.getLineWidth(lineIndex)` sau khi đã build `StaticLayout` thay vì tự đo lại bằng `measureText`.
- Thêm `.coerceAtLeast(1)` cho `finalWidth`/`finalHeight` trước khi gọi `Bitmap.createBitmap`.
- Xem lại công thức translate dọc, dùng `staticLayout.height` thay vì chỉ dòng đầu.

## Acceptance Criteria
- [ ] Watermark text nhiều dòng có nội dung trùng lặp giữa các dòng vẽ đúng kích thước khung.
- [ ] Text watermark với gap âm lớn không crash `IllegalArgumentException`.
- [ ] Text nhiều dòng căn giữa đúng theo chiều dọc trong bitmap kết quả (kiểm tra trực quan).
