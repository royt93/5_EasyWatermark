---
id: BUG-85
type: Bug
priority: P2
effort: S
sources: R5 perf re-audit 2026-10-14 (verify tay: HiddenWatermarkReader + AboutViewModel)
files:
  - app/src/main/java/com/mckimquyen/watermark/export/stego/HiddenWatermarkReader.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/about/AboutViewModel.kt
---

# BUG-85: "Kiểm tra chứng thực" ảnh lớn bị OOM nhưng báo như "không có watermark"

**Priority:** P2 (kết quả xác thực SAI, không crash)
**Effort:** S
**Status:** todo

## Vấn đề

`HiddenWatermarkReader.read()` decode ảnh ĐÚNG kích thước gốc ARGB_8888 (cố ý, không `inSampleSize`: lớp ẩn nằm trên lưới 8x8, downsample làm lệch lưới). Ảnh 48MP ≈ 195MB, 108MP ≈ 430MB; manifest không có `largeHeap` → `OutOfMemoryError`. Hàm bắt OOM và trả `null`.

`null` cũng chính là giá trị "ảnh không mang watermark ẩn" nên `AboutViewModel.verifyAuthenticity()` gộp 2 trường hợp, UI báo "không tìm thấy" cho ảnh thực ra có watermark. Trong công cụ truy nguồn rò rỉ (IDEA-02/10), âm tính giả là kết quả nguy hiểm: người dùng kết luận ảnh không phải của mình.

## Đề xuất

1. Đổi kiểu trả về thành kết quả có phân biệt (sealed: `Found`/`NotFound`/`TooLarge`/`Unreadable`) thay vì `InvisibleWatermark.Result?`.
2. Trước khi decode: `inJustDecodeBounds=true` đọc `outWidth*outHeight`, so với ngưỡng tính từ `Runtime.maxMemory()` (ARGB_8888 = 4 byte/pixel, chừa dư địa cho `extract()` cấp thêm mảng); vượt ngưỡng → trả `TooLarge` KHÔNG thử decode.
3. UI hiển thị thông báo riêng "ảnh quá lớn để kiểm tra lớp ẩn", vẫn hiện kết quả con dấu EXIF (tầng 1) độc lập.
4. Giữ nguyên `catch OutOfMemoryError` làm lưới cuối, map sang `TooLarge`.

Không đổi thuật toán stego, không thêm `largeHeap`.

## Test

- Unit: hàm quyết định ngưỡng thuần (`pixels, maxMemory → fits?`) với các mốc 12/48/108MP và heap 256/512MB.
- Unit: `AboutViewModel` map `TooLarge` sang report có cờ riêng, không trùng `NotFound`.
- Widget: màn xác thực hiện đúng chuỗi cho 4 trạng thái (cần thêm string + dịch đủ locale).
- Integration (androidTest): ảnh thật có watermark ẩn đọc được (`HiddenWatermarkReaderIntegrationTest` đã có); thêm case file giả header 108MP → `TooLarge`, không OOM thật.

## Prompt loop

Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).
