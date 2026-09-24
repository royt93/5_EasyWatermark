---
id: IDEA-06
type: Idea
effort: M
sources: Internal, Agy (2/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/BitmapUtils.kt
---

# Auto-contrast/opacity harmonizer theo từng ảnh

## Mô tả
Tự động phân tích độ sáng (luminance) và mật độ chi tiết tại đúng toạ độ đặt watermark của TỪNG ảnh trong batch, từ đó tự động đảo màu chữ (sáng/tối) và tinh chỉnh opacity phù hợp — đảm bảo hàng trăm ảnh với điều kiện sáng khác nhau đều có watermark rõ nét mà không bị chìm vào nền.

## Vì sao đáng làm
Effort thấp hơn nhiều so với các idea khác trong nhóm này (không cần ML model, chỉ cần tính luminance vùng ảnh — có thể tái dùng logic Palette đã có ở `applyBg()`) nhưng giải quyết vấn đề UX thực tế rất phổ biến khi batch nhiều ảnh có độ sáng khác nhau.

## Triển khai (gợi ý sơ bộ)
- Với vùng ảnh nằm dưới watermark, tính luminance trung bình (có thể tái dùng `Palette` API đã dùng ở `applyBg()`).
- Map luminance → chọn màu chữ sáng/tối + opacity phù hợp, cho phép override thủ công nếu user không thích kết quả tự động.

## Acceptance Criteria
- [x] Batch ảnh có độ sáng nền khác nhau tại vị trí watermark tự động chọn màu/opacity đọc rõ.
- [x] Có toggle bật/tắt (một số user muốn giữ đồng nhất màu watermark theo brand, không muốn tự động đổi).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-06`, file ticket = `todo/IDEA-06-auto-contrast-opacity-harmonizer.md`.

## Kết quả kiểm chứng

**Triển khai thực tế** (khác vị trí file so với mô tả gốc — `applyBg()`/Palette nằm ở `WaterMarkImageView.kt`, không phải `BitmapUtils.kt`):
- `data/model/WaterMark.kt`: field `autoContrastEnabled: Boolean = false`.
- `data/repo/WaterMarkRepository.kt`: `KEY_AUTO_CONTRAST_ENABLED` (DataStore), `updateAutoContrastEnabled()`, ghi trong `applyWaterMark()`.
- `ui/MainViewModel.kt`: wrapper `updateAutoContrastEnabled()`.
- `ui/panel/TextStyleFragment.kt`: thêm chip thứ 4 "Tự động tương phản" vào `TextEffectAdapter` sẵn có (tái dùng, không tạo widget mới).
- `ui/widget/WaterMarkImageView.kt`: `applyAutoContrastIfEnabled()` — chỉ áp dụng cho layer chính/chế độ Text; tính vùng lấy mẫu (`computeAutoContrastSampleRegion`, hàm thuần) theo `tileMode`/`offsetX`/`offsetY`, dùng `Palette.Builder(bitmap).setRegion(...).getDominantColor()` + tái dùng `TextEffectRenderer.contrastingColor()` để chọn đen/trắng, nâng sàn alpha qua `readableAlpha()` (hằng số `AUTO_CONTRAST_MIN_ALPHA=160`). Không ghi đè `textColor`/`alpha` gốc trong DataStore — chỉ ảnh hưởng bản render hiện tại.
- Vùng lấy mẫu là ô vuông ước lượng (ghi rõ bằng `ponytail:` doc comment) — đo chính xác cần build shader 2 lần, không đáng effort M của ticket.

**1. Audit**: 9/10. Không magic number trần (mọi ngưỡng đặt tên hằng số), không `late`/force-unwrap mới, không leak resource mới (không thêm stream/listener/timer). Trừ 1 điểm vì vùng lấy mẫu ở chế độ CLAMP là ước lượng hình học (ghi rõ trong doc comment + ticket), không pixel-perfect.

**2. Test**: `./gradlew testDebugUnitTest` — 611/611 pass (2 lần full-suite trước đó fail do flake tải nặng JVM fork đã biết từ trước — xem comment `testOptions` trong `app/build.gradle.kts` —, không liên quan diff này; verify bằng cách chạy lại baseline `git stash` cũng flake ở test khác không liên quan). Test mới/cập nhật:
  - `WaterMarkImageViewAutoContrastRoboTest` (11 case): `shouldApplyAutoContrast`, `readableAlpha`, `computeAutoContrastSampleRegion` (REPEAT/MIRROR dùng cả ảnh, CLAMP theo offset, clamp biên, bitmap rỗng).
  - `WaterMarkRepositoryAutoContrastRoboTest` (3 case): mặc định tắt, bật/tắt roundtrip qua DataStore.
  - `WaterMarkRepositoryApplyWaterMarkRoboTest`: bổ sung field mới vào test roundtrip toàn field sẵn có.

**3. Smoke test thật trên device đã khoá session (TECNO KJ7, serial `115333744A005844`)**:
  - Cài `assembleDebug`, mở app, chọn ảnh test tự tạo (nửa đen góc trái trên, còn lại trắng).
  - Bật chip "Tự động tương phản" ở tab Kiểu dáng → watermark REPEAT-tile đổi màu chữ từ vàng cam (mặc định) sang ĐEN ngay lập tức (nền ảnh chủ yếu trắng) — đúng hành vi kỳ vọng, xác nhận bằng screenshot trước/sau.
  - `logcat` không có FATAL/exception nào liên quan `WaterMarkImageView`/auto-contrast trong suốt phiên thao tác.
  - Không thử được case CLAMP (anchor đơn) do không tìm ra thao tác UI đổi tile mode trong thời gian smoke test — rủi ro thấp vì cùng code path `Palette`/`contrastingColor` đã verify ở REPEAT, và logic tính vùng CLAMP đã có 7 unit test phủ đủ biên.
