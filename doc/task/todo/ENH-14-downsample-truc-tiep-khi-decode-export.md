---
id: ENH-14
type: Enhancement
effort: M
sources: Tách từ BUG-05 (phần "downsample chủ động khi decode") — quá rủi ro để làm chung 1 loop iteration với phần recycle bitmap an toàn
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/BitmapUtils.kt
related: BUG-05
---

# Downsample trực tiếp khi decode ảnh export (thay vì decode full-res rồi resize sau)

## Mô tả
`generateImage()` gọi `decodeBitmapFromUri()` decode ảnh export ở **full resolution** (không set `inSampleSize`), sau đó nếu user chọn resize (`maxOutputLongEdge` != Original) mới thu nhỏ **sau khi đã vẽ watermark xong** qua `OutputImageUtils.resizeIfNeeded()`. Với ảnh 12-48MP, đây vẫn là nguồn gây áp lực bộ nhớ đỉnh (peak memory) dù đã recycle bitmap trung gian (xem BUG-05 phần đã fix) — vì tại thời điểm vẽ watermark, bitmap vẫn đang ở full-res.

## Vì sao tách riêng khỏi BUG-05
Thay đổi độ phân giải decode ảnh hưởng trực tiếp tới toàn bộ phép tính toạ độ/tỷ lệ trong `generateImage()` (`imageInfo.width/height`, `imageMatrix`, `scaleX/scaleY`, vị trí watermark theo offset chuẩn hoá) — rủi ro cao gây lệch vị trí/kích thước watermark nếu tính sai. Cũng cần quyết định sản phẩm rõ ràng: chỉ downsample khi user đã chọn resize (`maxOutputLongEdge` != 0/Original), giữ nguyên full-res khi user chọn "Original" (không đổi hành vi mặc định).

## Triển khai (gợi ý sơ bộ)
- Khi `maxOutputLongEdge != 0`: tính `inSampleSize` phù hợp dựa trên kích thước ảnh gốc (đọc qua `inJustDecodeBounds`) và `maxOutputLongEdge`, truyền vào `decodeBitmapFromUri` (cần thêm overload/param `reqWidth/reqHeight` tương tự `decodeSampledBitmapFromResource`).
- Đảm bảo toàn bộ phép tính toạ độ watermark (offset, scale) vẫn đúng theo kích thước bitmap MỚI (đã downsample) — cần test kỹ vị trí watermark không bị lệch so với trước khi thay đổi.
- Khi `maxOutputLongEdge == 0` (Original): giữ nguyên hành vi hiện tại (decode full-res), không đổi.

## Acceptance Criteria
- [ ] Export với resize option (1080/2048/4096) giảm peak memory rõ rệt so với hiện tại (đo qua Memory Profiler, so sánh trước/sau).
- [ ] Vị trí/kích thước watermark trên ảnh output không đổi so với trước khi thay đổi (test trực quan, so sánh pixel-level nếu có thể).
- [ ] Export với "Original" (không resize) không đổi hành vi.
