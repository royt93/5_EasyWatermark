---
id: ENH-39
type: Enhancement
priority: P2
effort: M
sources: /code-review --level high (review pass 12, 2026-09-30) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/export/stego/StegoCodec.kt
  - app/src/test/java/com/mckimquyen/watermark/export/stego/StegoRobustnessTest.kt
---

# `StegoCodec.applyBit()` — ép đủ STRENGTH bất kể khối ảnh gốc phẳng đến đâu, dễ lộ vệt trên vùng phẳng thật

## Mô tả
`applyBit()` (dòng 162-181) luôn ép khoảng cách `|A|-|B| >= STRENGTH (26.0)` bất kể biên độ hệ số DCT gốc của khối 8x8 lớn hay nhỏ. Với khối phủ vùng gần như phẳng tuyệt đối trong ảnh thật (bầu trời quang, tường sơn phẳng, phông nền studio, background app một màu) — nơi hệ số tần số giữa gốc gần 0 — phép ép này bơm một thành phần tần số giữa lớn TỔNG HỢP vào khối vốn gần như trống, đúng nguyên nhân kinh điển gây vệt block/ringing thấy được trong watermark miền DCT.

`StegoRobustnessTest.texturedPixels()` (comment dòng 27) tự nhận: *"ảnh phẳng tuyệt đối không đại diện ảnh thật"* — nghĩa là case này CHƯA từng được đo/test, dù đây là loại ảnh người dùng app này (đóng dấu bản quyền hàng loạt) khả năng cao sẽ gặp (ảnh sản phẩm nền trắng, slide, ảnh chụp màn hình...).

## Đề xuất
Cần quyết định đánh đổi trước khi sửa (đụng công thức lõi đã tune theo `StegoRobustnessTest`):
- Đo PSNR/artifact thật trên ảnh phẳng tuyệt đối (thêm case vào `StegoRobustnessTest`/`InvisibleWatermarkIntegrationTest`) để biết mức độ ảnh hưởng thật (có thể không tệ như lo ngại — cần số liệu trước khi sửa, không đoán).
- Nếu xác nhận có vệt thấy được: cân nhắc STRENGTH thích ứng theo biên độ gốc (giảm STRENGTH khi khối phẳng) — đổi lại phải đo lại toàn bộ `StegoRobustnessTest` (robustness JPEG q=50..85) để không phá độ bền đã đạt.

## Acceptance Criteria
- [ ] Có số liệu PSNR/so sánh trực quan thật trên ít nhất 1 ảnh nền phẳng tuyệt đối (solid color hoặc gradient rất nhẹ) trước và sau nhúng.
- [ ] Nếu sửa STRENGTH: toàn bộ `StegoRobustnessTest` (JPEG q=50/70/75/85, 2 vòng nén liên tiếp) vẫn đạt 100% bit đúng như hiện tại.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-39`, file ticket = `todo/ENH-39-stego-applybit-lo-vet-tren-khoi-anh-phang.md`.
