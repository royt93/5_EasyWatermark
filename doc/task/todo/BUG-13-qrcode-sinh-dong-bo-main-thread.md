---
id: BUG-13
type: Bug
priority: P2
effort: XS
sources: Agy (1/4, verify trực tiếp xác nhận đúng dòng)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/QrCodeBottomSheetFragment.kt
verified: true
---

# Sinh QR đồng bộ trên Main thread mỗi ký tự gõ

## Mô tả
`refreshPreview()` dòng 74:
```kotlin
val bitmap = QrCodeGenerator.generate(content, size = QrCodeGenerator.DEFAULT_SIZE)
```
Được gọi từ `afterTextChanged` (dòng 47) — tức MỖI KÝ TỰ người dùng gõ vào ô nhập nội dung QR. `QrCodeGenerator.generate` dùng ZXing encode ma trận (mặc định 512x512 = 262144 pixel duyệt) chạy đồng bộ ngay trên Main thread → giật lag bàn phím rõ rệt, đặc biệt trên thiết bị cấu hình thấp.

## Cách fix đề xuất
- Debounce input (200-300ms) trước khi trigger sinh QR.
- Chuyển việc generate sang `Dispatchers.Default`, dùng coroutine (`viewLifecycleOwner.lifecycleScope`) rồi cập nhật preview trên Main.

## Acceptance Criteria
- [ ] Gõ liên tục vào ô nhập nội dung QR không giật lag bàn phím.
- [ ] `QrCodeGenerator.generate` không còn chạy trên Main thread.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-13`, file ticket = `todo/BUG-13-qrcode-sinh-dong-bo-main-thread.md`.
