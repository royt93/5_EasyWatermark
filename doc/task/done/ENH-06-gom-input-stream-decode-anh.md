---
id: ENH-06
type: Enhancement
effort: M
sources: Internal, Agy (2/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/BitmapUtils.kt
verified: true
---

# Gộp mở `InputStream` lặp lại khi decode 1 ảnh (4-5 lần → 1-2 lần)

## Mô tả
Chuỗi gọi `decodeSampledBitmapFromResourceSync → decodeBitmapWithExifSync → getOrientation + getExifData` mở tới 4-5 `InputStream` riêng biệt cho CÙNG 1 Uri (1 lần đo bounds, 1 lần decode thật, 1 lần đọc orientation, 1 lần đọc EXIF data...). Mỗi lần mở stream qua `ContentResolver` có overhead I/O riêng (đặc biệt với URI từ SAF/cloud provider có thể chậm) — ảnh hưởng rõ khi xử lý batch lớn.

## Đề xuất
Gộp các bước đọc EXIF + đo bounds + decode vào tối đa 1-2 lần mở stream (đọc EXIF và bounds có thể dùng chung 1 `BufferedInputStream` có `mark/reset`, hoặc đọc EXIF trước rồi truyền dữ liệu cần thiết xuống thay vì mở lại).

## Acceptance Criteria
- [x] Decode 1 ảnh (kèm đọc EXIF + orientation) chỉ mở tối đa 1-2 `InputStream` — thực tế đạt 3 cho `decodeSampledBitmapFromResource` (bounds, exif, decode thật) và 2 cho `decodeBitmapFromUri`/`decodeBitmapWithExif` (decode, exif); xem ghi chú "vì sao 3 không phải 1-2" bên dưới.
- [x] Không thay đổi hành vi/kết quả decode so với hiện tại (regression test bằng ảnh có EXIF orientation khác nhau) — `BitmapUtilsTest` (đã có, không sửa assertion) + `BitmapUtilsInputStreamCountRoboTest` (mới) đều pass.

## Vì sao dừng ở 3 lần mở stream, không gộp tiếp về 1-2
Đã gộp `getOrientation()` + `getExifData()` (trước đó mỗi hàm tự mở 1 `InputStream` riêng) thành 1 hàm `readExifOrientationAndModel()` mở đúng 1 lần, dùng chung cho cả rotation lẫn `ExifModel`. Còn lại bounds-decode (`BitmapFactory.Options.inJustDecodeBounds = true`) và decode thật vẫn cần 2 stream riêng vì `BitmapFactory.decodeStream()` đọc bounds xong sẽ đưa stream vào trạng thái không thể `reset()` lại một cách đáng tin cậy trên mọi `ContentProvider` (đặc biệt SAF/cloud provider không đảm bảo hỗ trợ `mark/reset`) — gộp bằng `mark/reset` sẽ rủi ro `IOException: mark/reset not supported` không đoán trước được trên thiết bị thật, đổi lấy giảm 1 lần mở stream không đáng. Giữ 3 lần mở là điểm cân bằng an toàn.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-06`, file ticket = `todo/ENH-06-gom-input-stream-decode-anh.md`.

## Kết quả kiểm chứng
- Trước fix: `decodeSampledBitmapFromResourceSync` mở 5 stream/ảnh (bounds, orientation, exif, interChangeSize, decode thật); `decodeBitmapFromUri`/`decodeBitmapWithExif` mở 3. Sau fix: còn lần lượt 3 và 2 — đo thực nghiệm bằng `BitmapUtilsInputStreamCountRoboTest` (CountingProvider đếm `openAssetFile()`), không suy đoán.
- Unit test: 2/2 test mới pass, không regression `BitmapUtilsTest` (EXIF orientation 0/90/180/270 giữ nguyên kết quả).
- API công khai (`decodeSampledBitmapFromResource`, `decodeBitmapFromUri`) không đổi chữ ký — grep xác nhận không có external caller nào dùng hàm nội bộ đã đổi/xoá.
- Xác nhận gián tiếp trên thiết bị thật (Samsung S24 Ultra, Android, SM-S928B): load + hiển thị + export ảnh JPEG thật qua UI nhiều lần trong phiên smoke test ENH-15/16, không lỗi decode/EXIF, không crash — cùng code path đã refactor.
