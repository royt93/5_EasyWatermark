---
id: BUG-25
priority: P1
type: Bug
effort: S
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/data/repo/WaterMarkRepository.kt
---

# `WaterMarkRepository.waterMark` Flow: `.catch` không bắt exception từ `obtainSealedClass` trong `.map`

## Mô tả
`.catch { }` gắn ngay sau `dataStore.data` chỉ bắt được exception xảy ra TRƯỚC nó trong chain (đúng ngữ nghĩa Flow — `catch` chỉ bắt upstream). `TextPaintStyle.obtainSealedClass()`/`TextTypeface.obtainSealedClass()` gọi bên trong `.map` PHÍA SAU `.catch` — nếu ordinal lưu trong DataStore ngoài range hợp lệ (DataStore hỏng, restore từ backup cũ/version khác), các hàm này `throw IllegalArgumentException`, văng thẳng ra mọi collector (`MainViewModel`, `AboutViewModel`...) không qua `.catch`. `Anchor.obtain()`/`ExifFrameStyle.obtain()` cùng file đã xử lý an toàn bằng `entries.getOrElse(ordinal) { default }` — 2 hàm kia thì chưa.

## Triển khai
Đổi `TextPaintStyle.obtainSealedClass()`/`TextTypeface.obtainSealedClass()` sang pattern `getOrElse(ordinal) { default }` giống `Anchor.obtain()`/`ExifFrameStyle.obtain()`, hoặc di chuyển `.catch` xuống sau toàn bộ `.map`.

## Acceptance Criteria
- [ ] Ghi giá trị ordinal ngoài range hợp lệ trực tiếp vào DataStore (test) — `waterMark` Flow không crash, fallback về giá trị mặc định.
- [ ] Giá trị hợp lệ bình thường không đổi hành vi.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-25`, file ticket = `todo/BUG-25-watermarkrepositorywatermark-flow-catch-khong-bat-exception.md`.
