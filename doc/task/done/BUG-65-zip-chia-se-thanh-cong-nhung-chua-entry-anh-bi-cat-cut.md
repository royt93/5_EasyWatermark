---
id: BUG-65
type: Bug
priority: P1
effort: S
sources: re-audit 2026-10-04 (2 agent đọc code) + verify tay hình dạng code; chưa tái hiện trên máy trừ khi ghi khác
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/ExportZipHelper.kt
---

# Zip chia se thanh cong nhung chua entry anh bi cat cut

## Mô tả
`ExportZipHelper` (~dòng 128-143): sau `putNextEntry()` mà `copyTo()` ném lỗi giữa chừng, `catch` chỉ bỏ qua URI; entry đã ghi dở vẫn nằm trong ZIP. Nếu ít nhất 1 ảnh khác thành công, hàm trả ZIP như thành công.

**Kịch bản:** URI từ cloud provider mất mạng giữa lúc đọc → ZIP chia sẻ chứa file đúng tên nhưng chỉ có 1 phần byte, mở ra ảnh hỏng; UI không báo lỗi một phần.

## Đề xuất
Stage từng URI vào file tạm rồi mới `putNextEntry` (lỗi thì bỏ qua sạch), hoặc huỷ cả ZIP khi lỗi sau khi đã mở entry. Không rollback riêng entry được vì ghi thẳng vào stream.

## Acceptance Criteria
- [x] Đọc lỗi giữa chừng 1 URI → ZIP không chứa entry cắt cụt của URI đó.
- [x] Các URI tốt vẫn vào ZIP nguyên vẹn.
- [x] Unit test với InputStream giả ném IOException sau N byte.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-65`, file ticket = `todo/BUG-65-zip-chia-se-thanh-cong-nhung-chua-entry-anh-bi-cat-cut.md`.

## Kết quả kiểm chứng

**Tái hiện thật:** `ExportZipHelperTest.createZipArchive_whenReadFailsMidStream_doesNotLeaveTruncatedEntry` → RED `expected [good.jpg] but was [partial.jpg, good.jpg]` (entry cắt cụt nằm trong ZIP).
**Lưu ý về test:** bản đầu dùng provider + `createReliablePipe().closeWithError` nhưng Robolectric coi lỗi là EOF sạch nên không tái hiện đúng (test vẫn đỏ sau fix → bằng chứng test sai chứ không phải fix sai). Đã đổi sang `createZipArchiveWithOpener` (internal, nhận `openInput`) + `ThrowAfterBytesInputStream` ném `IOException` thật sau 100 byte.
**Fix:** `ExportZipHelper` đọc TRỌN VẸN từng URI vào file tạm (`File.createTempFile`) rồi mới `putNextEntry`; lỗi giữa chừng → không tạo entry; file tạm luôn xoá ở `finally`. `createZipArchive` public giữ nguyên chữ ký, uỷ quyền cho hàm internal.

- **Audit:** 9.2/10 — chi phí thêm 1 lần ghi file tạm/ảnh (đổi lấy tính đúng đắn); chưa đo ảnh rất lớn.
- **Test:** `ExportZipHelperTest` 6/6, toàn lô `FileUtils*`/`ClipboardImageHelperTest`/`ExportZipHelperTest` **33 test, 0 fail**, ktlint xanh.
- **Chưa làm:** smoke test thật chia sẻ ZIP trên máy (Pixel mất kết nối).
