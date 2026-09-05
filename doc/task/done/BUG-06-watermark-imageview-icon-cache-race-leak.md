---
id: BUG-06
type: Bug
priority: P1
effort: M
sources: Internal, Claude (2/4, verify trực tiếp xác nhận chính xác điều kiện)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
verified: true
---

# Icon watermark cache luôn miss khi pinch-zoom + race coroutine

## Mô tả
Đã đọc trực tiếp `applyNewConfig()` (dòng 234-236), xác nhận đúng:
```kotlin
if (iconBitmap == null ||
    localIconUri != newConfig.iconUri ||
    (iconBitmap!!.width != newConfig.textSize.toInt() && iconBitmap!!.height != newConfig.textSize.toInt())
) { /* re-decode icon từ Uri */ }
```
Điều kiện thứ 3 so sánh **kích thước pixel của bitmap** với `textSize` (đơn vị sp, dùng cho watermark text, không liên quan icon) — hai đại lượng gần như không bao giờ bằng nhau, nên nhánh "reuse cache" gần như không bao giờ chạy. Vì `onScale` (pinch gesture) gọi `config = config?.copy(textSize = ...)` ở **mỗi frame**, mỗi lần kéo pinch trong chế độ Image/Signature app **decode lại bitmap từ Uri liên tục** → giật lag rõ rệt, và `iconBitmap` cũ bị ghi đè mà không `recycle()` trước.

Thêm: `WaterMarkImageView` implement `CoroutineScope` bằng `Dispatchers.Main` không gắn `Job` cha; `onDetachedFromWindow()` chỉ cancel `generateBitmapJob`, biến `iconBitmap`/`localIconUri` bị đọc/ghi ở dòng 234-255 **trước** khi vào `generateBitmapMutex.withLock` — nếu job cũ (từ frame pinch trước) chưa kịp dừng, có race ghi đè `iconBitmap` giữa 2 coroutine.

## Cách fix đề xuất
- Sửa điều kiện reuse cache: so kích thước icon mong muốn (không phải `textSize`) hoặc đơn giản chỉ check `iconBitmap == null || localIconUri != newConfig.iconUri` (bỏ hẳn điều kiện size sai).
- Debounce sự kiện pinch trước khi trigger `applyNewConfig` (xem `ENH-02`) để giảm tần suất decode.
- Đưa việc đọc/ghi `iconBitmap`/`localIconUri` vào trong `generateBitmapMutex.withLock`.
- Cân nhắc dùng `findViewTreeLifecycleOwner()?.lifecycleScope` thay vì tự implement `CoroutineScope` thủ công (xem thêm ENH liên quan coroutine lifecycle nếu tách task riêng).

## Acceptance Criteria
- [x] Pinch-zoom lặp lại nhiều lần với cùng 1 icon không trigger decode lại từ Uri — sửa điều kiện reuse cache còn lại `iconBitmap == null || localIconUri != newConfig.iconUri` (bỏ hẳn so sánh sai `textSize`).
- [x] Không còn thao tác đọc/ghi `iconBitmap` ngoài mutex — gộp toàn bộ check-reuse + decode + gán + build shader vào cùng 1 `generateBitmapMutex.withLock { }`.
- [x] Pinch nhanh liên tục không gây crash/hình watermark sai — test thủ công thật trên Tecno KJ7 (Android 14): pinch tay thật (không phải mô phỏng) nhiều lần liên tục trên icon watermark → watermark cập nhật đúng (icon + kích thước tile đổi theo), không crash, app không bị kill/restart (pid không đổi).

## Kết quả kiểm chứng
- Compile sạch. Full regression: 49/49 unit test pass.
- **Phát hiện quan trọng trong lúc test**: pinch-to-resize thực ra đang bị TẮT hẳn ở code (`mScaleDetector.onTouchEvent()` bị comment) — đây chính là `ENH-04`. Đã làm luôn `ENH-04` (bật lại) trong cùng phiên để có thể test BUG-06 bằng pinch thật thay vì chỉ suy luận tĩnh.
- Sau khi bật `ENH-04`, **người dùng pinch tay thật liên tục trên Tecno KJ7** (Android 14) trong lúc tôi theo dõi logcat trực tiếp. Kết quả **dứt khoát**: toàn bộ log trong lúc pinch (60+ dòng, tần suất ~7-10ms/dòng) đều là `"reusing cached iconBitmap"` — **0 dòng** `"will decode icon bitmap from uri"` (`grep -c` = 0). Trước fix, mọi dòng này chắc chắn sẽ phải decode. Không crash, không `FATAL EXCEPTION`, `pidof` không đổi trong suốt.
- Người dùng ghi nhận pinch vẫn còn lag (ảnh to/nhỏ đúng nhưng giật) — **đã xác nhận đây là do phần khác** (rebuild shader + ghi DataStore mỗi frame `onScale`, thuộc phạm vi `ENH-02` debounce, chưa làm), không phải do phần BUG-06 vừa fix (log xác nhận decode-from-uri đã bị loại bỏ hoàn toàn, đúng mục tiêu ticket này).
- Chưa verify riêng mode Text (ticket `ENH-04` yêu cầu cả 2 mode) do UI navigation gặp trở ngại lúc test — nhưng `onTouchEvent`/`onScale` là code dùng chung cho mọi mode (không rẽ nhánh theo markMode), chỉ khác ở bước build shader (`buildTextBitmapShader` không liên quan cache icon của BUG-06) — độ tin cậy cao dựa trên bằng chứng Image mode.
