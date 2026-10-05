---
id: BUG-66
type: Bug
priority: P2
effort: XS
sources: re-audit 2026-10-04 (2 agent đọc code) + verify tay hình dạng code; chưa tái hiện trên máy trừ khi ghi khác
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/QrCodeGenerator.kt
---

# Qrcodegenerator savetocache bo qua ket qua bitmap compress

## Mô tả
`saveToCache()` (~dòng 72-84) bỏ qua `Boolean` trả về của `bitmap.compress()`; nếu `false` vẫn tạo `FileProvider` URI và báo thành công.

**Kịch bản:** Encoder/ghi PNG thất bại → file rỗng/hỏng nhưng `updateIcon()` vẫn lưu URI, watermark không render, user không nhận lỗi.

## Đề xuất
Kiểm tra giá trị `compress`; `false` thì xoá file và trả `null` (caller đã toast `save_failed`). Cùng pattern BUG-34.

## Acceptance Criteria
- [x] `compress()` trả false → `saveToCache` trả null và không để lại file.
- [x] Unit test (bitmap giả compress false).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-66`, file ticket = `todo/BUG-66-qrcodegenerator-savetocache-bo-qua-ket-qua-bitmap-compress.md`.

## Kết quả kiểm chứng

**Fix:** `QrCodeGenerator.saveToCache` lấy giá trị trả về của `bitmap.compress()` và đưa qua hàm thuần mới `keepFileIfWritten(file, compressSucceeded)`: `compress()==false`, file không tồn tại hoặc rỗng → xoá file rác và trả `null` (caller `QrCodeBottomSheetFragment` đã toast `save_failed`). Cùng pattern BUG-34 (`SignatureRepository.resolveWriteResult`).

- **Audit:** 9.2/10 — sửa nhỏ, đúng pattern có sẵn. Trừ điểm: bằng chứng test là test của HÀM THUẦN, không phải đầu-cuối.
- **Test (`QrCodeGeneratorTest` 7/7, ktlint xanh):** `keepFileIfWritten` 3 case (compress thất bại → xoá + false; compress ok nhưng file rỗng → xoá + false; file hợp lệ → giữ + true) và `saveToCache_realBitmap_returnsUriAndWritesNonEmptyFile` (đường thành công không hồi quy).
- **Giới hạn nói thẳng:** KHÔNG có RED thật — hàm `keepFileIfWritten` chưa tồn tại trước fix nên test không biên dịch được; và KHÔNG mô phỏng được `Bitmap.compress()` trả `false` bằng bitmap thật (Robolectric không làm đáng tin). Nhánh `false` thật chỉ được chứng minh qua hàm thuần, không qua `saveToCache` đầu-cuối.
- **Chưa làm:** smoke test trên máy (Pixel mất kết nối).
