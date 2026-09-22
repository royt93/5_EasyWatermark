---
id: ENH-34
type: Enhancement
effort: S
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SaveImageBSDialogFragment.kt
  - app/src/main/java/com/mckimquyen/watermark/data/repo/UserConfigRepository.kt
---

# Chuẩn hoá lựa chọn WEBP theo API mới (`WEBP_LOSSY`/`WEBP_LOSSLESS`)

## Mô tả
UI đang dùng `Bitmap.CompressFormat.WEBP` — deprecated từ API 30+ (thay bằng `WEBP_LOSSY`/`WEBP_LOSSLESS` tách biệt rõ chất lượng/dung lượng). Trên thiết bị API 30+, dùng constant cũ vẫn hoạt động (ánh xạ ngầm) nhưng không cho user chọn LOSSLESS thật sự khi cần giữ nguyên chất lượng tuyệt đối (vd export cho in ấn).

## Triển khai
Trên API 30+: phân biệt rõ WEBP_LOSSY (mặc định, giữ hành vi cũ) và WEBP_LOSSLESS (option mới) trong dropdown format; API <30: giữ nguyên `WEBP` như cũ (fallback).

## Acceptance Criteria
- [x] Chọn WEBP_LOSSLESS trên thiết bị API 30+ — file xuất ra không mất chất lượng dù nén (so sánh byte-for-byte hoặc PSNR với ảnh gốc).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-34`, file ticket = `todo/ENH-34-chuan-hoa-lua-chon-webp-theo-api-moi-webplossywebplossless.md`.

## Kết quả kiểm chứng
- `UserConfigRepository.resolveOutputFormat(ordinal, sdkInt)`: hàm thuần mới, đọc ordinal từ DataStore → `Bitmap.CompressFormat`. Ordinal 3/4 (WEBP_LOSSY/WEBP_LOSSLESS) chỉ resolve ra field enum thật khi `sdkInt >= Build.VERSION_CODES.R`, ngược lại fallback `WEBP` cũ — tránh `NoSuchFieldError` khi tham chiếu field không tồn tại trên framework thiết bị API <30 (field đó không có trong `android.graphics.Bitmap$CompressFormat.class` thật trên OS cũ, khác compileSdk dùng lúc build). Dùng hằng số Int `WEBP_LOSSY_ORDINAL`/`WEBP_LOSSLESS_ORDINAL` thay vì `.ordinal` trực tiếp trong nhãn `when` để bản thân việc so khớp không đụng field đó.
- `OutputImageUtils.resolveIsLossless(format, sdkInt)`: cùng nguyên tắc short-circuit (`sdkInt >= R &&`) — dùng lại ở cả `estimateOutputBytes()` (ước tính dung lượng, giờ coi WEBP_LOSSLESS như PNG — trước đây tính sai theo đường cong quality JPEG/WEBP thường) và `SaveImageBSDialogFragment.supportsQuality()` (ẩn slider chất lượng cho lossless, tái dùng thay vì lặp logic).
- `SaveImageBSDialogFragment`: `popArray`/`formatByIndex` build có điều kiện theo `Build.VERSION.SDK_INT >= R` — API 30+ hiện 4 lựa chọn (JPEG/PNG/WEBP Lossy/WEBP Lossless), API <30 giữ nguyên 3 lựa chọn cũ (JPEG/PNG/WEBP). `popArray` dùng `by lazy` (không phải field khởi tạo ngay) vì `getString()` cần Context — Fragment chưa attach lúc constructor chạy.
- Test: `UserConfigRepositoryFormatResolverTest` (JUnit thuần, không cần Robolectric — hàm nhận `sdkInt` tham số hoá) verify mọi ordinal × cả 2 nhánh SDK. `OutputImageUtilsTest` bổ sung case `resolveIsLossless`/`estimateOutputBytes` cho WEBP_LOSSY/WEBP_LOSSLESS. Không launch được `SaveImageBSDialogFragment` thật trong JVM test (hạn chế đã ghi nhận từ trước ở `DlgSaveFileLayoutRoboTest` — Fragment ép kiểu `requireContext() as MainActivity`) nên phần build danh sách format trong Fragment verify bằng smoke test thật thay vì widget test.
- Smoke test thật trên device khoá `115333744A005844` (TECNO SPARK 20 Pro+, Android 14/API 34 — đúng nhánh API 30+): dropdown Export hiện đúng 4 lựa chọn định dạng (screenshot xác nhận: JPEG/PNG/WEBP (Nén mất chất lượng)/WEBP (Không mất chất lượng)); chọn WEBP Lossless → slider CHẤT LƯỢNG biến mất đúng thiết kế; Export → file thật ghi ra `/sdcard/Pictures/WaterMarkCreator/ewm_*.webp` (911736 bytes, lớn hơn hẳn JPEG cùng ảnh ~250KB — đặc trưng lossless không nén mất dữ liệu), header hex xác nhận `RIFF....WEBPVP8X` (WebP extended format hợp lệ, không phải file hỏng).
- `./gradlew testDebugUnitTest` toàn bộ PASS (sau `--stop` daemon). `ktlintCheck` sạch.
- Audit: 9/10 — đúng scope + AC, xử lý đúng rủi ro NoSuchFieldError xuyên suốt (không chỉ chỗ ticket liệt kê mà cả `estimateOutputBytes` không nằm trong file list gốc của ticket nhưng cùng rủi ro); trừ điểm nhẹ vì không launch được Fragment thật để widget-test trực tiếp UI dropdown (hạn chế môi trường có sẵn, không phải thiếu sót code).
