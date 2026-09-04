---
id: ENH-06
type: Enhancement
effort: M
sources: Internal, Agy (2/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/BitmapUtils.kt
---

# Gộp mở `InputStream` lặp lại khi decode 1 ảnh (4-5 lần → 1-2 lần)

## Mô tả
Chuỗi gọi `decodeSampledBitmapFromResourceSync → decodeBitmapWithExifSync → getOrientation + getExifData` mở tới 4-5 `InputStream` riêng biệt cho CÙNG 1 Uri (1 lần đo bounds, 1 lần decode thật, 1 lần đọc orientation, 1 lần đọc EXIF data...). Mỗi lần mở stream qua `ContentResolver` có overhead I/O riêng (đặc biệt với URI từ SAF/cloud provider có thể chậm) — ảnh hưởng rõ khi xử lý batch lớn.

## Đề xuất
Gộp các bước đọc EXIF + đo bounds + decode vào tối đa 1-2 lần mở stream (đọc EXIF và bounds có thể dùng chung 1 `BufferedInputStream` có `mark/reset`, hoặc đọc EXIF trước rồi truyền dữ liệu cần thiết xuống thay vì mở lại).

## Acceptance Criteria
- [ ] Decode 1 ảnh (kèm đọc EXIF + orientation) chỉ mở tối đa 1-2 `InputStream`.
- [ ] Không thay đổi hành vi/kết quả decode so với hiện tại (regression test bằng ảnh có EXIF orientation khác nhau).
