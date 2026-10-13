---
id: ENH-48
type: Enhancement
priority: P2
effort: XS
sources: R5 business-value/test re-audit 2026-10-14
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/panel/AlphaPbFragment.kt
  - app/src/test/java/com/mckimquyen/watermark/ui/panel/AlphaPbFragmentRoboTest.kt
---

# ENH-48: Alpha slider round-trip mất 1% do làm tròn xuống hai lần

**Priority:** P2 (sai lệch UI nhỏ, tái hiện được)
**Effort:** XS (<2h)
**Status:** done

## Vấn đề

`AlphaPbFragment.doOnChange()` đổi phần trăm slider sang alpha 0..255 bằng `(value / 100 * 255).toInt()`, còn `rebuildAlpha()` đổi ngược bằng `(alpha / 255 * 100).toInt()`. Cả hai đều cắt phần thập phân xuống:

- User chọn 99% → alpha 252.
- Mở lại giá trị alpha 252 → slider/label thành 98%.

Đây không phải lệch do alpha được lưu thiếu chính xác: phép đổi hai chiều bằng floor làm mất 1 điểm phần trăm ở một phần dải. Alpha slider hiện không có test kiểm tra round-trip/boundary; test accessibility chỉ khoá label tại alpha=128.

## Đề xuất

Dùng quy tắc làm tròn nhất quán, ví dụ `roundToInt()` ở cả hai chiều (có thể tách thành helper thuần để test), giữ biên 0..255 và slider 0..100. Không đổi schema/data hiện có.

## Test

Unit/widget test cho 0%, 1%, 50%, 99%, 100%; đặc biệt 99% → native alpha → display percent phải trả lại 99% (hoặc sai số tối đa 1 điểm nếu giá trị native không biểu diễn chính xác). Xác nhận biên alpha luôn 0..255 và slider luôn 0..100.

## Prompt loop

Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).

## Kết quả kiểm chứng (2026-10-14)

- **Fix**: `AlphaPbFragment` dùng `alphaPercentToNative()`/`nativeAlphaToPercent()` với `coerceIn` rồi `roundToInt()` ở cả hai chiều; không đổi range slider, native alpha hay schema DataStore.
- **Unit**: `AlphaPbFragmentConversionTest` 3 case — mốc 0/1/50/99/100, native 0/1/128/252/255, clamp ngoài biên, round-trip 0..100 sai số tối đa 1. RED thật: compile fail vì hàm conversion chưa tồn tại.
- **Widget**: `AlphaPbFragmentConversionWidgetTest` 1 case — `AlphaPbFragment` trong `MainActivity`, callback slider 99% ghi alpha 252, format lại 99%, biên 100%/255.
- **Integration**: `AlphaPbFragmentIntegrationTest` trên Pixel 7 Pro (`2B051FDH3006MU`) dùng `MainActivity` + `MainViewModel` + DataStore thật; set 99%, đọc lại alpha 252, tạo lại panel và hiển thị 99. Test tự khôi phục alpha ban đầu trong `finally`.
- **Regression**: toàn bộ `testDebugUnitTest` 1503 test, 0 fail; `:app:ktlintCheck` pass; package instrumentation `ui` 5/5 pass.
- **Smoke Pixel 7 Pro**: mở app → chọn ảnh → editor → Kiểu dáng → Độ trong; slider hiển thị 100% rồi 99% sau kéo nhỏ, không `FATAL EXCEPTION`. Không lưu/chia sẻ ảnh smoke; thoát bằng huỷ thay đổi.
- **Giới hạn**: slider smoke tay chọn 99% bằng kéo thật, không xác minh native alpha 252 qua UI; native alpha được xác minh bằng integration test DataStore thật.
- **Tự audit**: 9.2/10 — diff nhỏ, test phủ conversion/widget/DataStore; trừ điểm vì tương tác slider thật khó đặt chính xác từng phần trăm.
