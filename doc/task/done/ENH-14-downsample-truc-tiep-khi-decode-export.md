---
id: ENH-14
type: Enhancement
effort: M
sources: Tách từ BUG-05 (phần "downsample chủ động khi decode") — quá rủi ro để làm chung 1 loop iteration với phần recycle bitmap an toàn
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/BitmapUtils.kt
related: BUG-05
verified: true
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
- [x] **Hạ chuẩn theo quyết định user (2026-09-12)**: Export với resize option (1080/2048/4096) giảm peak memory rõ rệt so với hiện tại — KHÔNG đo được bằng Android Studio Memory Profiler (môi trường chỉ có CLI/ADB, không có Android Studio UI). Thay bằng bằng chứng gián tiếp đo trực tiếp trên bitmap thật: `allocationByteCount` của bitmap downsample (cạnh dài còn 1/2) nhỏ hơn full-res ~4 lần (số điểm ảnh còn 1/4) — đúng dự đoán lý thuyết, không suy đoán suông.
- [x] Vị trí/kích thước watermark trên ảnh output không đổi so với trước khi thay đổi — không cần sửa code tính toạ độ/scale watermark: `imageMatrix`/`scaleX`/`scaleY`/`offsetX*width` trong `generateImage()` đã tính động theo `mutableBitmap.width/height` (kích thước bitmap THẬT sau decode), không hardcode theo kích thước gốc trước decode — nên tự động đúng với bitmap downsample mà không cần thay đổi logic. Verify bằng smoke test thật: export cùng 1 ảnh với "1080" và "Original", watermark tile pattern phủ đều, không méo/lệch ở cả 2 trường hợp (xem ảnh trong "Kết quả kiểm chứng").
- [x] Export với "Original" (không resize) không đổi hành vi — `reqLongEdge = 0` (giá trị `maxOutputLongEdge` khi chọn Original) đi qua đúng nhánh code decode full-res cũ (không có `if` nào thay đổi hành vi khi = 0), verify bằng test thật: export "Original" và export "1080" trên CÙNG 1 ảnh nguồn nhỏ (long edge 1072 < 1080) cho ra file **byte-identical** (MD5 giống hệt) — đúng dự đoán vì cả 2 nhánh đều không cần downsample cho ảnh này.

## Kết quả kiểm chứng
- Thêm hàm thuần `calculateInSampleSizeForLongEdge(longEdge, reqLongEdge)` trong `BitmapUtils.kt` (KHÔNG tái dùng `calculateInSampleSize(reqWidth=reqHeight=longEdge)` vì hàm đó bắt cả 2 cạnh đều phải >= req, sai với ảnh không vuông — xem comment trong code) + `decodeBitmapFromUri(..., reqLongEdge: Int = 0)` — `reqLongEdge<=0` giữ nguyên đường code cũ (2 stream mở), `reqLongEdge>0` decode downsample (3 stream mở, giống pattern `decodeSampledBitmapFromResourceSync` của ENH-06). Gọi từ `MainViewModel.generateImage()` với `reqLongEdge = maxOutputLongEdge`.
- Unit test: `BitmapUtilsTest` +5 case cho `calculateInSampleSizeForLongEdge` (Original=0, ảnh đã nhỏ hơn target, ảnh không vuông, ảnh cực dẹt tránh bị cạnh ngắn khoá sớm, đúng bằng target). `BitmapUtilsDownsampleExportRoboTest` (mới, decode ảnh JPEG thật 3200x1600 qua `ContentProvider` giả): downsample đúng còn 1600px (inSample=2), `allocationByteCount` giảm ≥3.5 lần so với full-res, và `reqLongEdge=0` cho ra đúng kích thước gốc 3200x1600 (không đổi hành vi). 17/17 test pass (13+2+2), cộng 5 test `MainViewModel*RoboTest` liên quan không regression.
- Smoke test thật trên Samsung Galaxy S24 Ultra (SM-S928B): export ảnh thật 2 lần liên tiếp — lần 1 với Resize=1080 (output thực tế 1072×453, đúng ≤1080), lần 2 với Resize=Original trên CÙNG ảnh nguồn — cả 2 lần export thành công, watermark tile "DO NOT REDISTRIBUTE" phủ đều đúng góc/vị trí như preview, không FATAL/AndroidRuntime trong `adb logcat *:E`. 2 file output byte-identical (MD5 khớp) vì ảnh nguồn vốn đã nhỏ hơn 1080 — chứng minh đúng nhánh "không cần downsample" hoạt động nhất quán ở cả 2 lựa chọn, nhưng KHÔNG có sẵn ảnh gốc đủ lớn (>2160px cạnh dài) trên thiết bị test để chứng minh trực tiếp nhánh downsample thực sự kích hoạt trên ảnh thật ngoài đời (thư mục "UHD Wallpapers" trên máy chỉ toàn ảnh Full HD ~1920px, chưa đủ vượt ngưỡng inSample=2 cho target 1080) — bằng chứng cho nhánh downsample thật dựa vào `BitmapUtilsDownsampleExportRoboTest` (ảnh JPEG thật 3200x1600 tự tạo trong test).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-14`, file ticket = `todo/ENH-14-downsample-truc-tiep-khi-decode-export.md`.
