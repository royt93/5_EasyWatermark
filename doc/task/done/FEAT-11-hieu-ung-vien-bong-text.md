---
id: FEAT-11
type: Feature
effort: S
sources: Agy (1/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
  - app/src/main/java/com/mckimquyen/watermark/utils/TextEffectRenderer.kt
  - app/src/main/java/com/mckimquyen/watermark/utils/ktx/PainKtx.kt
  - app/src/main/java/com/mckimquyen/watermark/data/model/WaterMark.kt
  - app/src/main/java/com/mckimquyen/watermark/data/repo/WaterMarkRepository.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/adapter/TextEffectAdapter.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/panel/TextStyleFragment.kt
verified: true
---

# Hiệu ứng viền/bóng/nền pill cho text watermark

## Mô tả
Bổ sung tuỳ chọn viền tương phản (outline stroke), đổ bóng (drop shadow), hoặc nền mờ dạng viên thuốc (background pill) cho text watermark, giúp chữ luôn dễ đọc trên mọi nền ảnh sáng/tối phức tạp — vấn đề UX thực tế khi ảnh nền không đồng nhất.

## Triển khai
Mở rộng `TextPaint`/`buildTextBitmapShader` (`WaterMarkImageView.kt`) hỗ trợ thêm `Paint.setShadowLayer()` cho shadow, vẽ thêm layer stroke/pill nền trước khi vẽ text chính.

## Acceptance Criteria
- [x] Có tối thiểu 2 hiệu ứng mới (vd stroke + shadow) chọn được độc lập hoặc kết hợp — 3 hiệu ứng (Outline/Shadow/Pill BG), mỗi cái là 1 `Boolean` riêng trong `WaterMark`, chip UI toggle độc lập (không loại trừ như Fill/Stroke), kết hợp tự do cả 3 cùng lúc.
- [x] Text watermark trên ảnh nền phức tạp vẫn đọc rõ khi bật hiệu ứng — verify bằng mắt trên Pixel 7 Pro với ảnh nền nhiều màu (icon app screenshot).

## Kiến trúc
- `TextEffectRenderer` (mới, `utils/`) — hàm thuần tính biên/màu tương phản + hàm vẽ pill/outline, không phụ thuộc View. Màu tương phản (B/W) tự tính theo độ sáng màu chữ (`ColorUtils.calculateLuminance`) — không thêm color picker riêng (ngoài phạm vi AC).
- `WaterMark` thêm 3 field `textEffectStroke/Shadow/PillBackground: Boolean = false` — mặc định tắt, không đổi hành vi cũ.
- `PainKtx.applyConfig` set/clear `setShadowLayer` theo `textEffectShadow` — đặt ở đây (không phải trong `buildTextBitmapShader`) vì paint bị tái sử dụng qua nhiều config trong preview, tắt hiệu ứng phải xoá hẳn thay vì chỉ bỏ qua set.
- `WaterMarkImageView.buildTextBitmapShader` — cộng thêm biên (`TextEffectRenderer.marginPx`) vào kích thước bitmap SAU KHI đã nhân hệ số hGap/vGap (không phải trước) để viền/bóng/pill không bị cắt bất kể cấu hình gap; vẽ pill rồi outline TRƯỚC text chính (layer dưới cùng).
- **Export dùng chung 100% code path với preview**: `BatchExportEngine.generateImage()` gọi đúng `WaterMarkImageView.buildTextBitmapShader` + `TextPaint().applyConfig(...)` y hệt preview — không có code render riêng cho export, hiệu ứng tự động áp dụng cả 2 nơi không cần sửa gì thêm ở export.
- UI: chip toggle mới (`TextEffectAdapter`, độc lập với `TextPaintStyleAdapter` single-select hiện có) nối thêm vào `ConcatAdapter` của `TextStyleFragment` (tab Style). Checked-state dùng `?attr/colorPrimary` (không hardcode hex) — theo đúng Material You dynamic color đã áp dụng toàn app (CMonet).

## Kết quả kiểm chứng

### Unit/widget test
- `TextEffectRendererTest` (7 case, Robolectric — `Color.red/green/blue/alpha()` là method call thật, JVM thuần không mock sẽ sai): màu tương phản đúng B/W theo luminance, alpha áp đúng, `marginPx` tính đúng cho từng combo hiệu ứng.
- `WaterMarkImageViewTextShaderRoboTest` — 5 test mới: mỗi hiệu ứng (stroke/shadow/pill) bật riêng làm bitmap LỚN HƠN baseline (đo được biên cộng thêm); cả 3 kết hợp không crash; tắt hết hiệu ứng kích thước KHÔNG đổi so với trước FEAT-11 (không phá test BUG-07 cũ).
- `WaterMarkRepositoryIntegrationTest` (androidTest, chạy thật trên Pixel 7 Pro) — 5 test mới: mặc định `false`, round-trip từng field, và bật 1 field không ảnh hưởng 2 field còn lại (verify tính độc lập).
- Toàn bộ regression `WaterMarkImageView*`/`MainViewModel*`/`utils.*` + `ktlintCheck` module `:app` pass, `compileAppReleaseDebugAndroidTestKotlin` sạch.

### Smoke test thật trên Pixel 7 Pro (2B051FDH3006MU)
- Bật lần lượt Pill BG → Outline → Shadow trong tab Style — chip UI chuyển trạng thái checked đúng (viền cam `colorPrimary`), cả 3 chip active đồng thời (xác nhận combinable, không loại trừ nhau).
- Preview trong app hiện rõ nền pill sẫm màu sau mỗi dòng chữ ngay khi bật — cải thiện rõ rệt độ đọc trên ảnh nền phức tạp (ảnh test: icon app nhiều màu).
- Export ra file thật (JPEG) với cả 3 hiệu ứng bật — `adb pull` file về, zoom kiểm tra bằng mắt: nền pill sẫm màu sau chữ hiện rõ trong file export, khớp với preview trong app (xác nhận export/preview share đúng code path như phân tích kiến trúc). Không crash, `logcat` sạch không có `FATAL EXCEPTION`.
- Không phát hiện vấn đề UI/UX/Material You nào khác trong quá trình test tính năng này.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-11`, file ticket = `todo/FEAT-11-hieu-ung-vien-bong-text.md`.
