---
id: IDEA-17
type: Idea
effort: L
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/BatchCaptionBSDialogFragment.kt
---

# Voice-to-text caption khi batch nhiều ảnh (mở rộng FEAT-13)

## Mô tả
FEAT-13 chỉ cho nhập/dán caption dạng text (nhiều dòng/CSV) — chưa có cách nhập bằng GIỌNG NÓI. Khi xử lý hàng chục ảnh liên tiếp, đọc to caption cho từng ảnh có thể nhanh hơn gõ tay, đặc biệt lúc đang thao tác 1 tay hoặc caption dài.

## Đề xuất
Thêm nút micro cạnh mỗi dòng trong `BatchCaptionBSDialogFragment`, dùng `SpeechRecognizer` on-device (Android built-in, không cần key ngoài) điền thẳng kết quả nhận diện vào đúng dòng caption tương ứng ảnh đang duyệt.

## Acceptance Criteria
- [ ] Bấm micro, đọc to 1 câu caption cho ảnh đang duyệt — text nhận diện đúng điền vào đúng dòng caption của ảnh đó, không lẫn sang ảnh khác.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-17`, file ticket = `todo/IDEA-17-voice-to-text-caption-khi-batch-nhieu-anh-mo-rong-feat-13.md`.
