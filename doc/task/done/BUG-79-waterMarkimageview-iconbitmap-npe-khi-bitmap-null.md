# BUG-79: WaterMarkImageView — `iconBitmap!!` NPE khi `BitmapValue.bitmap` null

**Priority:** P0 (crash)
**Effort:** XS (<2h)
**Status:** done

## Vấn đề

`app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt:403` `srcBitmap = iconBitmap!!`. Guard dòng ~386 chỉ kiểm tra `isFailure() || data == null`, nhưng `BitmapCache.BitmapValue.bitmap` là `Bitmap?`. `iconBitmap = newIconBitmapValue.bitmap` có thể null → NPE trong coroutine render. Log dòng ~385 đã in `bitmapNull=...` nghĩa là case này được biết tới.

Cùng loại lỗi BUG-73 đã fix ở `BatchExportEngine`, nhưng chưa fix ở view.

## Fix

Lấy `val src = iconBitmap ?: return@launch` (release value mới nếu null) rồi truyền `src` vào `buildIconBitmapShader`.

## Test

Widget test Robolectric: decode trả `BitmapValue(bitmap = null, ...)` → không crash, watermark không vẽ.

## Prompt loop

Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).

## Kết quả kiểm chứng (2026-10-13)

- **Fix** (`WaterMarkImageView.kt`, nhánh Image của `applyNewConfig`): nếu `BitmapValue.bitmap == null` thì `release()` ref đã retain (hợp đồng BUG-73) rồi return; thay `iconBitmap!!` bằng `iconBitmap ?: return@launch`.
- **Test**: `WaterMarkImageViewNullIconBitmapRoboTest` 3 case — bitmap null (RED thật: NPE bị exceptionHandler nuốt, refCount kẹt 1 → GREEN refCount 0, không giữ value), icon hợp lệ vẫn được giữ (refCount 1, chống over-fix), decode failure không crash. 42 test `WaterMarkImageView*` pass, ktlint pass.
- **Smoke TECNO KJ7**: chọn ảnh → tab Hình → chọn icon từ photo picker → tile icon render đúng trên ảnh; logcat không có `AndroidRuntime:E`, không có NullPointer/"Throw Exception in WaterMarkImageView".
- **Giới hạn**: NPE gốc cần decode trả bitmap null, không tái hiện được bằng thao tác tay → bằng chứng chính là Robolectric RED→GREEN; smoke chỉ xác nhận không regression.
- **Tự audit**: 9.5/10 (diff 2 điểm, đúng R5, không leak thêm).
