---
id: FEAT-09
type: Feature
effort: XS
sources: Claude (1/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/OutputImageUtils.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SaveImageBSDialogFragment.kt
---

# Preset resize theo nền tảng (Instagram/Facebook/Zalo)

## Mô tả
Thêm nút nhanh resize theo kích thước chuẩn mạng xã hội phổ biến (Instagram 1080x1080/1080x1350, Facebook, Zalo...) thay vì chỉ dropdown Original/1080/2048/4096 chung chung hiện có.

## Triển khai
`OutputImageUtils.targetDimensions`/`resizeIfNeeded` đã có sẵn cơ chế resize theo cạnh dài — chỉ cần thêm bộ preset UI (danh sách nền tảng + tỉ lệ tương ứng), tái dùng logic resize hiện tại.

## Acceptance Criteria
- [ ] Chọn preset nền tảng resize đúng kích thước chuẩn tương ứng.
- [ ] Preset không phá vỡ tỉ lệ khung hình gốc trừ khi người dùng chủ động chọn crop.
