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
- [ ] Đọc lỗi giữa chừng 1 URI → ZIP không chứa entry cắt cụt của URI đó.
- [ ] Các URI tốt vẫn vào ZIP nguyên vẹn.
- [ ] Unit test với InputStream giả ném IOException sau N byte.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-65`, file ticket = `todo/BUG-65-zip-chia-se-thanh-cong-nhung-chua-entry-anh-bi-cat-cut.md`.
