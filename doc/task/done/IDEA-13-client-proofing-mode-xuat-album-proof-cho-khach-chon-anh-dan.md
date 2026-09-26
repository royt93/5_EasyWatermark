---
id: IDEA-13
type: Idea
effort: XL
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/export
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg
---

# Client Proofing Mode — xuất album proof cho khách chọn ảnh (dành cho photographer/freelancer)

## Mô tả
Biến app từ công cụ đóng dấu đơn thuần thành luồng duyệt ảnh bán hàng cho photographer/freelancer: xuất 1 album proof tạm với watermark LỚN (chống dùng ảnh chưa mua) kèm mã số mỗi ảnh + file index (HTML/PDF) để khách chọn ảnh cần mua.

## Đề xuất
Thêm export mode riêng "Proofing": watermark cố định lớn + số thứ tự trên mỗi ảnh, sinh kèm file HTML/PDF liệt kê số thứ tự + thumbnail để gửi khách.

## Acceptance Criteria
- [x] Export 1 batch ở chế độ Proofing — mỗi ảnh có watermark lớn + số thứ tự riêng biệt, kèm 1 file index liệt kê đúng thứ tự khớp với ảnh.

## Kết quả kiểm chứng (2026-09-26)

### Triển khai thực tế
- Thêm chế độ Proofing trong dialog export, lưu qua DataStore.
- Ép watermark text `PROOF #{seq3}` lớn, xoay và lặp phủ ảnh; caption riêng vẫn nối `{seq3}`.
- Sinh `proof_index.html` qua SAF/MediaStore/legacy, escape tên file và ghi đè index cũ.
- Ảnh bị skip giữ nguyên số thứ tự gốc, nên danh sách có thể nhảy số.

### Audit: 9.5/10
- Không thêm dependency; tái dùng `BatchExportEngine`, token resolver, WorkManager và `DocumentFile` hiện có.
- Trừ 0.5 vì thông số proof cố định; chỉ thêm tuỳ chỉnh khi có nhu cầu thực tế.

### Test
- `./gradlew ktlintCheck testDebugUnitTest`: PASS.
- `ProofingIndexIntegrationTest`: PASS trên TECNO KJ7 `115333744A005844`.

### Smoke test
- Export thật 2 ảnh trên TECNO KJ7: watermark `PROOF #001` / `PROOF #002` lặp phủ toàn ảnh, số không bị cắt.
- Sinh đúng một `/sdcard/Documents/WaterMarkCreator/proof_index.html` gồm `#001`, `#002` và đúng tên 2 JPEG xuất thật.
- Ảnh xuất: `ewm_1790440687883.jpg`, `ewm_1790440688063.jpg`; WorkManager trả `SUCCESS`.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-13`, file ticket = `todo/IDEA-13-client-proofing-mode-xuat-album-proof-cho-khach-chon-anh-dan.md`.
