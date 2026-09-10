---
id: IDEA-09
type: Idea
priority: P2
effort: L
sources: codex exec (external CLI, re-audit 2026-09-10)
files: []
---

# Watermark Survivability Preview — mô phỏng crop/recompress mạng xã hội

## Mô tả
Trước khi export, mô phỏng watermark trông ra sao sau khi ảnh bị các nền tảng xử lý lại (Facebook/Zalo/Instagram thường crop tỉ lệ, recompress mạnh, downscale) — chấm điểm mức độ watermark còn đọc được/nhìn thấy được sau các phép biến đổi này, tự đề xuất size/vị trí/opacity tối ưu hơn nếu điểm thấp.

## Vì sao đáng làm
Đây đúng use-case cốt lõi của app (bảo vệ ảnh khi chia sẻ) nhưng hiện tại người dùng chỉ thấy preview watermark trên ảnh gốc, không biết nó có "sống sót" nổi qua 1 lượt share Facebook/Zalo hay không cho tới khi tự kiểm chứng thủ công. Đã có sẵn preset resize theo nền tảng ([FEAT-09](FEAT-09-preset-resize-theo-nen-tang.md)) làm nền tảng dữ liệu về hành vi từng platform.

## Gợi ý triển khai (sơ bộ, cần thiết kế riêng)
- Bộ profile biến đổi giả lập cho từng nền tảng phổ biến (tỉ lệ crop, mức JPEG quality recompress, max resolution) — có thể ước lượng dựa trên thông tin công khai, không cần chính xác tuyệt đối.
- Áp transform giả lập lên canvas preview, tính điểm dựa trên contrast/kích thước watermark còn lại so với khung hình.

## Acceptance Criteria
- [ ] Chọn 1 nền tảng mục tiêu → preview hiển thị watermark sau khi mô phỏng biến đổi của nền tảng đó.
- [ ] Có điểm số/mức cảnh báo khi watermark dự đoán khó nhìn thấy sau biến đổi.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-09`, file ticket = `todo/IDEA-09-watermark-survivability-preview.md`.
