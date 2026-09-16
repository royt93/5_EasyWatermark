---
id: BUG-24
priority: P1
type: Bug
effort: XS
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/EditTemplateContentFragment.kt
---

# `EditTemplateContentFragment.safetyShow()` ép kiểu sai lớp, guard chống trùng dialog vô hiệu

## Mô tả
Copy-paste từ `SaveImageBSDialogFragment.safetyShow()` nhưng quên đổi type: `manager.findFragmentByTag(TAG) as? SaveImageBSDialogFragment` — tag của `EditTemplateContentFragment` không bao giờ khớp lớp `SaveImageBSDialogFragment`, `as?` luôn trả null. Guard chống show trùng dialog mất tác dụng: bấm nhanh nút sửa template liên tiếp tạo nhiều dialog chồng lên nhau, có thể `IllegalStateException` (fragment đã add).

## Triển khai
Sửa `as? SaveImageBSDialogFragment` thành `as? EditTemplateContentFragment` (đúng lớp hiện tại).

## Acceptance Criteria
- [ ] Bấm nút sửa template 2 lần liên tiếp thật nhanh — chỉ 1 dialog hiện ra, không crash `IllegalStateException`.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-24`, file ticket = `todo/BUG-24-edittemplatecontentfragmentsafetyshow-ep-kieu-sai-lop-guard.md`.
