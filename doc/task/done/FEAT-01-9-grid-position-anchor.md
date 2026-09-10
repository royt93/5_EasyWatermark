---
id: FEAT-01
type: Feature
effort: S
sources: Codex, Claude, doc/feat.md mục D (đã đề xuất, nâng thành task cụ thể)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
---

# Preset vị trí neo 9-grid + margin %

## Mô tả
Ngoài kéo thả tự do hiện có, thêm preset neo watermark theo lưới 3x3 (4 góc, 4 cạnh giữa, trung tâm) kèm margin tuỳ chỉnh theo %. Thao tác phổ biến hơn kéo tay, đặc biệt cần kết quả đồng nhất trên hàng trăm ảnh trong batch.

## Triển khai
`WaterMarkImageView` đã dùng `offsetX/offsetY` chuẩn hoá 0..1 trong `ImageInfo` — map preset anchor → giá trị offset tương ứng, không cần đổi cơ chế vẽ hiện có. Chỉ cần thêm UI chọn preset (9 nút/lưới) + input margin.

## Acceptance Criteria
- [x] Chọn 1 trong 9 vị trí preset đặt đúng watermark vào góc/cạnh/giữa tương ứng.
- [x] Margin áp dụng đúng theo % trên mọi tỷ lệ ảnh khác nhau trong batch.
- [x] Vẫn giữ được chế độ kéo tay tự do song song.

## Kết quả kiểm chứng (re-audit 2026-09-10)
Đã triển khai đầy đủ ngoài luồng backlog (2026-09-05) — xem `doc/feat.md` mục 7. `data/model/Anchor.kt`, `WaterMarkImageView.applyAnchor()`, `PositionAnchorBottomSheetFragment.kt` + persist qua `WaterMarkRepository.updateAnchor()/updateMargin()`. Đã verify trên thiết bị thật (TECNO KJ7) theo ghi chú trong feat.md. Xác nhận lại bởi `claude -p` (external CLI, đọc trực tiếp code) trong đợt re-audit 2026-09-10. Move sang `done/`.
