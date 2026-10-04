---
id: BUG-67
type: Bug
priority: P1
effort: XS
sources: re-audit 2026-10-04 (2 agent đọc code) + verify tay hình dạng code; chưa tái hiện trên máy trừ khi ghi khác
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/TextWatermarkBSDFragment.kt
---

# Textwatermarkbsdfragment them trung editcontentfragment khi activity recreate

## Mô tả
`onViewCreated` (~dòng 182-197) luôn `add(EditTextContentFragment())` + `addToBackStack`, không kiểm tra `savedInstanceState`. `MainActivity` chỉ khai `configChanges="orientation|keyboardHidden"` nên đổi dark mode/font scale/locale hoặc process death sẽ recreate.

**Kịch bản:** Mở dialog sửa text rồi đổi dark mode → `childFragmentManager` đã restore child cũ, `onViewCreated` add thêm cái nữa → 2 `EditText` chồng nhau, 2 collector chạy, phải bấm back 2 lần.

## Đề xuất
Bọc việc `add` bằng `if (savedInstanceState == null)`.

## Acceptance Criteria
- [ ] Recreate khi dialog mở → chỉ còn đúng 1 `EditTextContentFragment`.
- [ ] Robolectric test recreate (`ActivityController.recreate()`) đếm child fragment.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-67`, file ticket = `todo/BUG-67-textwatermarkbsdfragment-them-trung-editcontentfragment-khi-activity-recreate.md`.
