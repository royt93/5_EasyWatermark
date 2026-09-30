---
id: ENH-40
type: Enhancement
priority: P2
effort: M
sources: /code-review --level high (review pass 12, 2026-09-30) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/export/stego/StegoCodec.kt
  - app/src/test/java/com/mckimquyen/watermark/export/stego/StegoRobustnessTest.kt
---

# `StegoCodec.writeLumaBlock()` — clamp riêng từng kênh RGB sau khi cộng chung 1 delta luma, lệch biên độ ở vùng gần trắng/đen

## Mô tả
`writeLumaBlock()` (dòng 207-230) cộng CHUNG 1 `delta` độ sáng vào cả 3 kênh R/G/B rồi `clamp()` (dòng 232-233) TỪNG kênh độc lập về `[0,255]`. Ở khối phủ vùng gần bão hoà (highlight gần trắng: r/g/b gần 255, hoặc shadow sâu gần đen), nếu `delta` dương lớn, kênh nào gần 255 nhất sẽ bão hoà (`clamp` cắt về 255) trong khi kênh khác chưa — độ sáng THẬT SỰ ghi lại vào pixel (tính lại từ 3 kênh đã clamp) sẽ THẤP HƠN độ sáng dự định (`newLuma`) mà `applyBit()` vừa mã hoá vào hệ số DCT.

Hậu quả: hệ số DCT đọc lại ở lần `decode()` sau (hoặc sau khi JPEG re-encode) không còn khớp đúng biên độ đã ép — giảm độ bền watermark đúng ở nhóm ảnh có vùng trắng/đen lớn (bầu trời, tuyết, nền trắng studio, bóng tối sâu). `texturedPixels()` trong `StegoRobustnessTest` dùng nền RGB tầm trung (base tính từ 60-180, ít khi chạm biên), nên case bão hoà kênh này chưa có test bao phủ.

## Đề xuất
Cần quyết định trước khi sửa (đụng pipeline ghi pixel, có test PSNR phụ thuộc):
- Đo mức độ mất bit thật trên ảnh có vùng trắng/đen lớn (thêm case highlight/shadow vào `StegoRobustnessTest`) trước khi quyết định có đáng sửa không.
- Nếu sửa: cân nhắc điều chỉnh `delta` để giữ đúng `newLuma` sau khi tính lại từ pixel đã clamp (thay vì clamp mù từng kênh) — hoặc chấp nhận giới hạn này và ghi rõ trong doc comment thay vì âm thầm để đó.

## Acceptance Criteria
- [ ] Có số liệu tỉ lệ bit đúng thật trên ít nhất 1 ảnh có vùng trắng gần bão hoà (vd nền RGB (250,250,250)) trước/sau nén JPEG, so sánh với `texturedPixels()` hiện tại.
- [ ] Nếu sửa: toàn bộ `StegoRobustnessTest` hiện có vẫn PASS.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-40`, file ticket = `todo/ENH-40-stego-clamp-tung-kenh-lech-bien-do-vung-trang-den.md`.
