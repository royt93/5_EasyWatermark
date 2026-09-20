---
id: ENH-29
type: Enhancement
effort: XS
sources: Codex + Claude (2 nguồn đồng thuận)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/QrCodeBottomSheetFragment.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SignatureBottomSheetFragment.kt
---

# Không dọn file cache tạm QR/signature cũ, tích luỹ rác không giới hạn

## Mô tả
Mỗi lần dùng, `QrCodeBottomSheetFragment` ghi file mới `qr_temp_${timestamp}.png` vào `cacheDir/qrcodes`, `SignatureBottomSheetFragment` ghi `sig_temp_${timestamp}.png` vào `cacheDir/signatures` (tên thư mục cũ, khác `SignatureRepository.signatureDir` chính thức) — không file nào bị xoá sau khi dùng xong. Dùng nhiều lần theo thời gian tích luỹ rác dung lượng không giới hạn trong cache app.

## Triển khai
Thêm policy xoá file cũ hơn N ngày HOẶC xoá thẳng file `*_temp_*` cũ ngay khi tạo file mới thành công (chỉ giữ file gần nhất cần dùng).

## Acceptance Criteria
- [ ] Dùng tính năng QR/Signature nhiều lần liên tiếp — thư mục cache không tích luỹ vô hạn file `*_temp_*` cũ.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-29`, file ticket = `todo/ENH-29-khong-don-file-cache-tam-qrsignature-cu-tich-luy-rac-khong-g.md`.
