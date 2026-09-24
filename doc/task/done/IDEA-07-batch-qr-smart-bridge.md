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
- [x] Mỗi ảnh trong batch có QR chứa hash riêng của chính ảnh đó (không trùng giữa các ảnh).
- [x] Quét QR (bằng app QR thường) ra được thông tin hash + timestamp + link đọc được.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-07`, file ticket = `todo/IDEA-07-batch-qr-smart-bridge.md`.

## Kết quả kiểm chứng (2026-09-24)

**Thiết kế:** không thêm `MarkMode` mới — thêm cờ `qrDynamicEnabled` + `qrContentTemplate` + `qrPortfolioLink` trên `WaterMark` (mặc định tắt, không đổi hành vi Image mode cũ). `BatchExportEngine.generateImage()` khi `qrDynamicEnabled=true` sinh QR bitmap RIÊNG trong RAM cho từng ảnh (hash SHA-256 đọc trực tiếp từ `imageInfo.uri` gốc qua `HashUtils`, template resolve qua `ExportNaming.resolveQrContent` tái dùng `TextTokenResolver`) rồi dùng thẳng làm `srcBitmap` cho `WaterMarkImageView.buildIconBitmapShader` — không ghi file tạm, không qua `BitmapCache`. `generatePreviewBitmap`/`generateCompareBitmaps`/`WaterMarkImageView` (canvas preview khi đang edit) CHỦ Ý không đổi — chỉ bottom sheet `QrCodeBottomSheetFragment` tự resolve preview cho ảnh đang chọn khi bật switch.

**File đã sửa:** `data/model/WaterMark.kt`, `data/repo/WaterMarkRepository.kt`, `export/ExportNaming.kt`, `export/BatchExportEngine.kt`, `utils/HashUtils.kt` (mới), `utils/QrCodeGenerator.kt` (+`saveToCache` dùng chung), `ui/dlg/QrCodeBottomSheetFragment.kt` + layout, `ui/MainViewModel.kt`.

**Audit:** 9.5/10 — 1 điểm trừ vì scope cố tình không mở rộng regenerate QR động cho `generatePreviewBitmap`/`generateCompareBitmaps` (xem lý do trong plan/thiết kế), có thể làm follow-up riêng nếu cần preview khớp 100% ảnh đang chọn ở mọi màn hình.

**Re-audit (2026-09-24, sau khi merge task phụ keep-screen-on/wake-lock):** phát hiện + fix thêm 1 leak thật — `qrBitmap` (512x512 ARGB_8888, ~1MB) sinh trong `BatchExportEngine.generateImage()` không được `recycle()` sau khi `buildIconBitmapShader()` đã copy pixel vào bitmap riêng của shader (cùng nguyên tắc BUG-05/ENH-15 đã áp dụng ở nhánh icon tĩnh) — tích luỹ qua nhiều ảnh trong batch lớn có thể góp phần OOM native trên máy cấu hình thấp/API 24-25. Đã bọc `try/finally` recycle ngay sau khi build shader xong, verify lại bằng `BatchExportEngineQrDynamicIntegrationTest` chạy PASS trên device thật (QR vẫn render đúng, không bị corrupt do recycle sớm).

**Test:**
- Unit (mới): `HashUtilsTest` (4 case, vector SHA-256 chuẩn NIST), `ExportNamingQrContentTest` (5 case: hash khác nhau giữa ảnh, hash ổn định theo cùng ảnh, token `{seq}`/`{portfolio_link}`, template không token, uri lỗi không crash), `WaterMarkRepositoryQrDynamicRoboTest` (3 case), mở rộng `QrCodeBottomSheetFragmentRoboTest` (+5 case switch/restore/save, sửa `TestHostActivity` + `@After` cleanup fix flaky thật đã phát hiện khi thêm test).
- `./gradlew testDebugUnitTest` (toàn bộ ~150 file): XANH. `./gradlew ktlintCheck`: XANH.
- Integration (mới, androidTest): `BatchExportEngineQrDynamicIntegrationTest` — export batch 2 ảnh khác nhau, đọc ngược QR trong file output bằng ZXing thật (`MultiFormatReader`), assert 2 QR content khác nhau + mỗi content là hash 64-hex hợp lệ. Chạy PASS trên device thật (xem Smoke test).

**Smoke test thật (device đã khoá session — TECNO BG6, serial `118743744X002560`):**
- Cài bản debug + androidTest, chạy `BatchExportEngineQrDynamicIntegrationTest` qua `am instrument` trực tiếp: **PASS**. Kéo ảnh output về kiểm tra bằng mắt — QR vẽ trọn vẹn, rõ nét (phát hiện + sửa 1 bug thật lúc build test: offset mặc định 0.5/0.5 làm QR bị cắt cụt rìa phải/dưới nếu icon lớn — không phải bug production, chỉ là tham số test cần chỉnh).
- Thao tác tay qua ACTION_SEND + `adb shell input tap` (không dùng MCP tool vì phiên có >1 device cắm, tránh gõ nhầm máy khác theo R3): mở app → nhận ảnh → mở QR Code sheet → bật switch "Dynamic per-image QR" → portfolio link field hiện ra, template tự điền `{hash}|{date}|{portfolio_link}`, preview QR tự sinh đúng hash ảnh đang chọn → bấm "Use QR Code" → canvas cập nhật đúng markMode Image. Toàn bộ luồng **không crash** (logcat `FATAL EXCEPTION` = 0 lần suốt phiên).
- Lưu ý môi trường: phiên phát hiện & xử lý 1 sự cố ngoài ý muốn — `./gradlew connectedDebugAndroidTest` (không truyền serial) tự chạy trên MỌI device đang cắm, vi phạm R3 (đã cài nhầm APK debug+test lên 1 device thứ 2 ngoài ý muốn) — đã uninstall dọn sạch máy đó, chuyển hẳn sang `adb -s <serial> shell am instrument` cho các lần chạy sau để đảm bảo chỉ chạm đúng device đã khoá.
