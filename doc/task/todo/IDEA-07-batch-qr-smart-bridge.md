---
id: IDEA-07
type: Idea
effort: M
sources: Claude, Agy (2/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/QrCodeGenerator.kt
---

# Batch QR Smart-Bridge (hash SHA-256 + xác thực nguồn gốc)

## Mô tả
Mở rộng tính năng QR watermark đã có (`utils/QrCodeGenerator.kt`) — thay vì QR chứa text tĩnh (link/liên hệ), sinh QR chứa thông tin động cho TỪNG ảnh: hash SHA-256 của ảnh gốc + timestamp + link portfolio/mạng xã hội tác giả. Người xem quét mã là xác thực được nguồn gốc ảnh ngay lập tức — hướng nhẹ hơn nhiều so với `IDEA-03` (C2PA) nhưng tận dụng được hạ tầng QR đã có sẵn 100%.

## Vì sao đáng làm
Effort thấp nhất trong nhóm "chứng thực nguồn gốc" (so với `IDEA-02`/`IDEA-03`) vì tái dùng gần như toàn bộ pipeline QR watermark hiện có — chỉ cần đổi nội dung QR từ tĩnh sang động theo từng ảnh trong batch.

## Triển khai (gợi ý sơ bộ)
- Tính SHA-256 của ảnh gốc (trước watermark) lúc bắt đầu xử lý từng ảnh trong batch.
- Encode `{hash}|{timestamp}|{portfolio_link}` (hoặc JSON gọn) vào QR thay vì text cố định nhập tay.
- Cần cân nhắc: `IDEA-07` này khác `FEAT-02`/token hiện có vì cần tính hash thật, không chỉ là token metadata có sẵn.

## Acceptance Criteria
- [ ] Mỗi ảnh trong batch có QR chứa hash riêng của chính ảnh đó (không trùng giữa các ảnh).
- [ ] Quét QR (bằng app QR thường) ra được thông tin hash + timestamp + link đọc được.
