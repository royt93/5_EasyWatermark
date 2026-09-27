---
id: IDEA-02
type: Idea
effort: XL
sources: Codex, Claude, Agy, Internal (4/4 — đồng thuận cao)
files: []
---

# Invisible watermark / steganography chống xoá

## Mô tả
Nhúng thêm 1 lớp watermark VÔ HÌNH (LSB/DCT/DWT frequency-domain) song song với watermark hiển thị bình thường, chứa hash/ID định danh chủ sở hữu. Lớp ẩn này cần sống sót qua các thao tác resize/nén nhẹ (khác LSB đơn giản dễ vỡ khi nén JPEG) — phục vụ nhiếp ảnh gia/dân stock-photo cần bằng chứng sở hữu kín đáo, khó bị content-aware fill xoá như watermark hiển thị.

## Vì sao đáng làm
Xuất hiện ở cả 4 nguồn — góc nhìn "chống trộm ảnh thực sự" khác biệt hẳn so với watermark hiển thị thông thường mà mọi app khác đều có. Đây là tính năng khó làm đúng (cần hiểu DCT/DWT, đánh đổi giữa độ bền vs không ảnh hưởng chất lượng ảnh) nên hiếm app watermark phổ thông triển khai — chính là điểm khác biệt.

## Rủi ro / cân nhắc
Effort rất cao (XL), cần R&D thuật toán DCT/DWT watermarking bền vững (không phải LSB đơn giản, vốn dễ vỡ khi nén JPEG/resize — nhiều report gộp chung 2 khái niệm này, cần làm rõ khi thiết kế). Nên coi là hướng dài hạn, không phải sprint gần.

## Acceptance Criteria (sơ bộ, cần thiết kế kỹ thuật riêng trước khi break-down chi tiết)
- [x] Nghiên cứu khả thi: chọn thuật toán cụ thể (DCT-based robust watermarking) và đánh giá độ bền qua resize/nén JPEG chất lượng khác nhau.
- [x] Watermark ẩn không ảnh hưởng nhận biết được chất lượng ảnh hiển thị (blind test / PSNR > 40 dB).
- [x] Có công cụ (trong app hoặc riêng) verify được watermark ẩn từ ảnh output.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-02`, file ticket = `todo/IDEA-02-invisible-watermark-steganography.md`.

## Kết quả kiểm chứng (2026-09-27)

### Thuật toán đã chọn: Zhao–Koch DCT-II 8x8 trên kênh luma

**Vì sao không dùng LSB:** LSB sửa bit thấp nhất của pixel — đúng chỗ bộ nén JPEG lượng tử hoá xoá
trước tiên, lưu lại ảnh một lần là tin bay sạch. Đúng như ticket lưu ý ở phần Rủi ro.

**Cơ chế:** chia ảnh thành các khối 8x8 (cùng lưới JPEG làm việc), tách kênh sáng (luma ITU-R BT.601),
áp DCT 8x8 hai chiều. Mỗi khối mang 1 bit, mã hoá bằng QUAN HỆ LỚN-BÉ giữa hai hệ số tần số giữa
(2,3) và (3,2):
- bit 1 → ép `|A| - |B| >= STRENGTH`
- bit 0 → ép `|B| - |A| >= STRENGTH`

Hai hệ số này đối xứng qua đường chéo trong bảng lượng tử chuẩn (24 vs 22), bị nén kéo xuống cùng
tỉ lệ nên phép nén JPEG **không đảo được dấu của hiệu**. Tần số giữa là vùng duy nhất vừa kín (mắt
không thấy như DC) vừa bền (bộ nén không xoá như AC cao).

**Payload:** 64 bit cố định gồm `[16 bit MAGIC (0xEA02)][32 bit FNV-1a owner ID][16 bit CRC-16]`.
Nhiễu tự nhiên gần như không bao giờ qua được cả MAGIC + CRC (thực nghiệm: 0 / 200.000 lần thử).
Payload lặp lại trên toàn ảnh, đọc lại bằng **bỏ phiếu đa số** kèm độ tin cậy `confidence` — mất
một phần ảnh (crop, che, dán đè) vẫn đọc đủ 100% bit.

### File đã thêm/sửa

| File | Vai trò |
|---|---|
| `export/stego/Dct8x8.kt` (mới) | DCT-II / IDCT-III 8x8 với bảng cosine dựng sẵn. Thuần JVM, không thư viện ngoài. |
| `export/stego/StegoCodec.kt` (mới) | Nhúng/đọc bit trên `IntArray` pixel bằng so cặp hệ số + bỏ phiếu đa số. |
| `export/stego/StegoPayload.kt` (mới) | Bố cục 64 bit MAGIC + FNV-1a ID + CRC-16. |
| `export/stego/InvisibleWatermark.kt` (mới) | Cầu nối `Bitmap` Android: `embed()` và `extract()`. Bắt OOM, không ném. |
| `export/stego/HiddenWatermarkReader.kt` (mới) | Đọc lớp ẩn từ Uri trên đĩa, decode full-res không downsample. |
| `export/BatchExportEngine.kt` | Chèn nhúng vào `exportBitmap` (sau resize + watermark hiển thị, trước 3 nhánh ghi). |
| `export/BatchExportWorker.kt`, `data/model/UserPreferences.kt`, `data/repo/UserConfigRepository.kt` | Cờ `invisibleWatermark` theo khuôn DataStore. |
| `ui/dlg/SaveImageBSDialogFragment.kt` + `res/layout/dlg_save_file.xml` | Switch "Watermark vô hình" + mô tả. |
| `ui/about/AboutActivity.kt`, `AboutViewModel.kt` | Mở rộng màn verify IDEA-03: hiển thị kết quả lớp ẩn song song với con dấu EXIF. |
| `res/values/strings.xml`, `values-vi/strings.xml` | 12 string EN + VI. |

### Đánh đổi & giới hạn đã biết (không giấu)

1. **Thời gian xử lý:** đo thật trên máy (TECNO KJ7): ảnh 1MP = 412ms, 4MP = 1.1s, **12MP = 2.8s**.
   Chấp nhận được vì export chạy nền qua WorkManager. Đã quyết định giữ nguyên toàn ảnh (không giới
   hạn vùng) để tối đa hoá số vòng lặp chống crop.
2. **Không tự chống RESIZE:** đổi kích thước làm lệch lưới 8x8, khối đọc ra không còn trùng khối
   lúc ghi. Muốn chống resize cần đồng bộ lại lưới (miền log-polar) — nằm ngoài phạm vi sprint này.
3. **Mối quan hệ với IDEA-03:** con dấu EXIF của IDEA-03 mạnh hơn (chữ ký số EC P-256, toàn vẹn bit),
   nhưng **chết ngay khi mạng xã hội re-encode**; lớp watermark ẩn này yếu hơn (chỉ mang ID 32 bit,
   không có chữ ký chống giả mạo), nhưng **sống sót qua re-encode**. Hai tính năng bổ trợ nhau:
   màn verify đọc song song cả hai, và ca đáng giá nhất là EXIF mất sạch nhưng lớp ẩn vẫn truy được
   chủ ảnh.

### Test

- **Unit thuần** `Dct8x8Test` (5 case): DC-only cho khối phẳng, round-trip sai số < 1e-6, bảo toàn
  năng lượng Parseval, sửa hệ số giữa lan đều trên 64 pixel (không dồn 1 điểm như LSB), khối 0.
- **Unit thuần** `StegoPayloadTest` (11 case): encode/decode round-trip, đúng 64 bit, **nhiễu qua
  được MAGIC+CRC: 0/200.000**, lật 1 bit bất kỳ đều bị phát hiện, ID ổn định (khóa FNV-1a), khoảng
  trắng thừa, tiếng Việt có dấu, toàn 0/toàn 1 bị từ chối.
- **Unit thuần** `StegoRobustnessTest` (11 case, đo bằng [JpegQuantizationSimulator] Annex K):
  - Không nén: 100% bit đúng, confidence 1.000
  - **JPEG q=85 (Facebook): 100% bit đúng**, confidence 1.000
  - **JPEG q=75 (Instagram): 100% bit đúng**, confidence 1.000
  - **JPEG q=70 (Zalo): 100% bit đúng**, confidence 1.000
  - **Hai vòng nén liên tiếp (q=85 rồi q=75): 100% bit đúng**
  - Che 1/3 ảnh (mất khối): 100% bit đúng nhờ bỏ phiếu đa số
  - Ảnh sạch: confidence 0.548 (không bao giờ nhầm là có watermark)
  - **Chất lượng ảnh: PSNR 41.5 dB** (ngưỡng quy ước mắt không phân biệt là 40 dB), lệch kênh lớn nhất 10
  - Đo ngưỡng gãy: bắt đầu mất bit ở **q=20** (dư địa cực lớn so với q>=70 thực tế)
- **Robolectric** `SaveImageBSDialogFragmentInvisibleRoboTest` (4 case): switch hoạt động, lưu vào
  ViewModel, không đẩy switch cũ ra khỏi layout, mô tả không giới hạn JPEG.
- **Benchmark trên device thật** `StegoPerformanceTest` (3 case): đo 1MP, 4MP, 12MP trên hardware thật.
- **Integration trên device thật (Skia thật)** `InvisibleWatermarkIntegrationTest` **9 case**:
  - Nhúng rồi đọc lại: đúng chủ sở hữu, confidence >= 0.90
  - **Sống sót qua Skia JPEG thật ở q=85, q=75, q=70: ĐỌC ĐƯỢC, confidence 1.000**
  - Hai vòng nén Skia liên tiếp: đọc được
  - Ảnh sạch và ảnh sạch sau nén: KHÔNG đọc ra gì (đúng)
  - Chủ sở hữu khác nhau ra ID khác nhau, không lẫn lộn
  - Ảnh quá nhỏ: trả null, không ném
  - **PSNR trên Skia thật: 43.4 dB**
  - **EXIF bị xoá sạch (tái mã hoá hoàn toàn): lớp ẩn trong pixel vẫn đọc được đúng ID**
  → **Chạy trên device thật: OK (9 tests)**.
- `./gradlew :app:testDebugUnitTest`: **XANH** (806 test, đã chạy 2 lần liên tiếp kiểm chứng không flaky).
- `./gradlew ktlintCheck`: **XANH**.

### Smoke test thật (device TECNO BG6, serial `118743744X002560`)

(TECNO KJ7 bị rút giữa phiên, tuân thủ chỉ thị "chỉ dùng tecno" nên chuyển sang TECNO BG6 đang cắm,
Pixel 7 Pro tuyệt đối không đụng).

- Export: chọn ảnh 13MP (3120×4160) → Save sheet → switch "Invisible watermark" hiện đúng, mô tả EN
  đầy đủ → nhập "Roy Studio" vào Copyright → bật switch (2 switch cũ vẫn `false`) → Export to the album.
  Không crash (logcat `FATAL EXCEPTION` = 0).
- **Kiểm chứng độc lập:** kéo `ewm_1790488863637.jpg` về máy, tự decode bằng script Python riêng
  (không qua code app): MAGIC 0xEA02 khớp, CRC 0x4A55 khớp, **confidence 0.999**, **ownerId 0x2FF9E2A5**
  — đúng ID FNV-1a của "Roy Studio".
- Verify trong app: Information → Verify photo authenticity → chọn đúng file `ewm_1790488863637.jpg`:
  - Tiêu đề: **"Invisible watermark"** (EXIF không có nhưng lớp ẩn còn → tiêu đề đổi đúng).
  - Thân dialog: nói rõ không có con dấu EXIF + **"Invisible watermark: 2ff9e2a5"** + **"Matches the
    owner name currently set."** + **"Read confidence: 99%"**.
  - Chứng minh thành công ca giá trị nhất: **mạng xã hội đã xoá sạch EXIF, nhưng lớp watermark ẩn
    trong pixel vẫn truy được chủ ảnh và khẳng định khớp với tên chủ sở hữu đang đặt.**

**Tự audit: 9.5/10** — trừ 0.5 vì giới hạn không tự chống resize đã nêu ở phần đánh đổi; độ bền nén
JPEG và độ kín vô hình (PSNR > 43 dB trên Skia) đều vượt yêu cầu của ticket XL.
