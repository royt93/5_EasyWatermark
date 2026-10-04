---
id: BUG-70
type: Bug
priority: P2
effort: XS
sources: re-audit 2026-10-04 (2 agent đọc code) + verify tay hình dạng code; chưa tái hiện trên máy trừ khi ghi khác
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SaveImageBSDialogFragment.kt
  - app/src/main/res/values*/strings.xml
---

# Toast loi mo gallery hien chu 1s tho va sai ngu canh

## Mô tả
`toast(R.string.share_error)` (~dòng 593) dùng `getString(res)` không truyền args nhưng chuỗi là `Share error with %1$s` → hiện nguyên `%1$s`; đồng thời nội dung sai ngữ cảnh (đây là lỗi MỞ ảnh, không phải chia sẻ). `e.message` null ở các dòng ~611/664/702 cũng ra "...with null".

**Kịch bản:** `startActivity(ACTION_VIEW)` ném exception → user thấy "Share error with %1$s".

## Đề xuất
Thêm string riêng `open_image_error` (đủ 14 locale, có test parity); các chỗ dùng `e.message` thì `?: getString(R.string.tips_error)`.

## Acceptance Criteria
- [ ] Không còn `%1$s` thô trong toast.
- [ ] `StringLocalizationParityTest` xanh.
- [ ] Test Robolectric: ép `startActivity` ném → text toast đúng.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-70`, file ticket = `todo/BUG-70-toast-loi-mo-gallery-hien-chu-1s-tho-va-sai-ngu-canh.md`.
