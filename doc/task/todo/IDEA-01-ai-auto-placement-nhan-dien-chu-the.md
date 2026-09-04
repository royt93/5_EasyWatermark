---
id: IDEA-01
type: Idea
effort: L
sources: Codex, Claude, Agy, Internal (4/4 — ĐỒNG THUẬN CAO NHẤT toàn bộ review, tất cả 4 agent độc lập đều đề xuất ý này đầu tiên)
files: []
---

# Auto-placement bằng on-device ML (né mặt người/chủ thể)

## Mô tả
Dùng model on-device (Google ML Kit Face Detection / Object Detection / Selfie Segmentation, hoặc Saliency detection) để tự động nhận diện khuôn mặt/người/vật thể chính trên từng ảnh trong batch, từ đó tự động đề xuất vị trí đặt watermark vào vùng "negative space" (nền ít chi tiết, tránh che chủ thể) — toàn bộ chạy local, không cần cloud, không tốn chi phí server.

## Vì sao đáng làm
Cả 4 nguồn AI độc lập (khác model, khác hãng) đều tự đưa ra ý tưởng này ở vị trí ưu tiên cao nhất khi được hỏi "tính năng độc quyền" — tín hiệu mạnh rằng đây là gap thực sự trên thị trường app watermark hiện tại (đa số chỉ hỗ trợ vị trí cố định/kéo tay), và khả thi kỹ thuật cao vì ML Kit chạy on-device miễn phí, không cần hạ tầng backend.

## Triển khai (gợi ý sơ bộ)
- Thêm dependency ML Kit (Face Detection hoặc Object Detection, chọn model nhẹ để không phình APK/thời gian xử lý batch quá lâu).
- Chạy detection song song lúc decode ảnh trong batch, tính vùng "an toàn" (không giao với bounding box chủ thể).
- Map kết quả vào cơ chế offset 0..1 đã có sẵn (giống `FEAT-01`), có thể để user bật/tắt tự động hoặc chỉ dùng làm gợi ý ban đầu (vẫn cho chỉnh tay sau đó).

## Acceptance Criteria (mức khởi tạo, cần refine khi lên kế hoạch chi tiết)
- [ ] Có toggle "tự động đặt vị trí" trong editor.
- [ ] Với ảnh có mặt người rõ ràng, watermark tự động không đè lên vùng mặt.
- [ ] Thời gian xử lý thêm không làm chậm batch quá đáng kể (đo benchmark cụ thể khi triển khai).
- [ ] Vẫn cho phép user override thủ công sau khi có gợi ý tự động.
