# BUG-79: WaterMarkImageView — `iconBitmap!!` NPE khi `BitmapValue.bitmap` null

**Priority:** P0 (crash)
**Effort:** XS (<2h)
**Status:** todo

## Vấn đề

`app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt:403` `srcBitmap = iconBitmap!!`. Guard dòng ~386 chỉ kiểm tra `isFailure() || data == null`, nhưng `BitmapCache.BitmapValue.bitmap` là `Bitmap?`. `iconBitmap = newIconBitmapValue.bitmap` có thể null → NPE trong coroutine render. Log dòng ~385 đã in `bitmapNull=...` nghĩa là case này được biết tới.

Cùng loại lỗi BUG-73 đã fix ở `BatchExportEngine`, nhưng chưa fix ở view.

## Fix

Lấy `val src = iconBitmap ?: return@launch` (release value mới nếu null) rồi truyền `src` vào `buildIconBitmapShader`.

## Test

Widget test Robolectric: decode trả `BitmapValue(bitmap = null, ...)` → không crash, watermark không vẽ.

## Prompt loop

Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).
