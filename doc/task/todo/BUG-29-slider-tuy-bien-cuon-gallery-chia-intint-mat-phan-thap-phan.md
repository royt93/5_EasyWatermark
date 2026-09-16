---
id: BUG-29
priority: P2
type: Bug
effort: XS
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/GalleryFragment.kt
---

# Slider tuỳ biến cuộn gallery: chia Int/Int mất phần thập phân, cuộn sai

## Mô tả
`OnTouchListener` của `sliderCard`: `val percent = binding.rvContent.computeVerticalScrollRange() / totalHeight` — cả 2 vế đều `Int`, phép chia mất hoàn toàn phần thập phân (kết quả luôn 0 hoặc số nguyên thô). So sánh `onScrolled()` cùng file dùng đúng `offset.toFloat() / verticalScrollRange`. Kéo thanh trượt tuỳ biến khiến cuộn không hết list hoặc giật cục/nhảy sai vị trí.

## Triển khai
Đổi phép chia sang `Float`/`Double` (`.toFloat()` một trong hai vế) giống pattern đã đúng ở `onScrolled()`.

## Acceptance Criteria
- [ ] Gallery có đủ ảnh để hiện slider tuỳ biến, kéo từ đầu tới cuối — cuộn mượt, tới đúng vị trí cuối danh sách.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-29`, file ticket = `todo/BUG-29-slider-tuy-bien-cuon-gallery-chia-intint-mat-phan-thap-phan.md`.
