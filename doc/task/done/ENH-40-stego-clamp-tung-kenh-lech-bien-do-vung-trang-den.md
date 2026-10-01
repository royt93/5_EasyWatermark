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

## Kết quả kiểm chứng (2026-10-01)
Phát hiện khi đo số liệu cho ENH-39 (cùng session) — **nặng hơn mô tả gốc**: không phải "lệch biên độ" nhẹ mà **mất watermark hoàn toàn** ở đúng biên tuyệt đối.

- Số liệu TRƯỚC khi sửa: nền gray=0 hoặc gray=255 (RGB đồng nhất, không cần khác kênh — clamp tại biên tuyệt đối đã đủ gây lỗi dù không có "lệch giữa các kênh") qua JPEG q<=70 chỉ đọc đúng 46.9-56.3% bit (gần ngẫu nhiên) nhưng `confidence` vẫn báo 1.000 — báo nhầm tự tin. Mọi mức gray cách biên dù chỉ 3 đơn vị (gray=3 hoặc 252) đã đọc đúng 100%.
- Root cause xác nhận: `writeLumaBlock()` clamp `[0,255]` cắt mất phần biên độ `applyBit()` vừa ép khi khối (a) phẳng tuyệt đối (không texture ở [COEF_A]/[COEF_B]) VÀ (b) đã ở sát biên 0/255 — một nửa mẫu điểm trong khối cần đẩy theo hướng không còn chỗ.
- **Hướng sửa đã chọn** (đúng đề xuất ticket, nhánh "điều chỉnh để giữ đúng newLuma"): thêm `StegoCodec.compensateRailClipping()` — dịch hệ số DC (độ sáng trung bình khối, không đụng AC/texture) ra xa biên đúng `RAIL_MARGIN=3` đơn vị TRƯỚC khi mã hoá, chỉ khi khối vừa phẳng vừa sát biên. Giá trị `RAIL_MARGIN` chọn bằng đo thật (không đoán): margin=13 (STRENGTH/2) sửa được nhưng PSNR tụt xuống 25.8dB (thấy rõ); margin=5 → 33.7dB; margin=2 vẫn mất bit ở q=50; **margin=3 là giá trị nhỏ nhất** vừa đạt 100% bit mọi mức JPEG q=50..85 vừa giữ PSNR 37.5dB (gần ngưỡng 40dB "mắt thường không phân biệt", chỉ ảnh hưởng vùng gray∈[0,2]∪[253,255] VÀ phẳng tuyệt đối — ảnh chụp thật hầu như không chạm case này do luôn có nhiễu cảm biến).
- Bug trung gian tự phát hiện: lần đầu implement gọi `compensateRailClipping()` SAU `applyBit()` → đọc nhầm biên độ ĐÃ mã hoá thay vì gốc, luôn no-op sai. Sửa thứ tự gọi TRƯỚC `applyBit()`, verify lại bằng test mới thấy fix thật sự có tác dụng.
- Test mới: `ENH-40 nen den trang tuyet doi van doc dung qua JPEG sau khi bu DC` (JVM, gray=0/255 × q=50/70/75/85, assert 100% bit đúng — trước là bằng chứng hồi quy của bug, giờ là guard chống tái phát), `ENH-40 khoi gan bien nhung co texture khong bi dich DC` (xác nhận không đụng ảnh có texture thật). Integration test (Skia thật, theo yêu cầu user): `InvisibleWatermarkIntegrationTest.nenDenTrangTuyetDoi_vanDocDungQuaNenSkiaThat` — PASS thật trên **TECNO KJ7** qua `connectedDebugAndroidTest` (10/10 test file, 0 failures), log xác nhận `[ENH-40] gray=0/255, Skia JPEG q=70 → đọc được`.
- `./gradlew :app:testDebugUnitTest` toàn bộ PASS (không chỉ file này). `ktlintCheck` sạch. Không đụng UI nên không cần widget test.
- Audit: 9/10 — fix đúng gốc rễ, phạm vi hẹp (chỉ khối phẳng+sát biên), có số liệu chọn hằng số thay vì đoán, verify cả JVM simulator lẫn Skia thật trên device. Trừ nhẹ vì PSNR 37.5dB vẫn dưới mốc quy ước 40dB cho riêng case biên hẹp này (đã cân nhắc margin nhỏ hơn nhưng phá robustness JPEG q=50, chọn ưu tiên đúng-bit hơn PSNR tuyệt đối ở case cực hiếm này).
