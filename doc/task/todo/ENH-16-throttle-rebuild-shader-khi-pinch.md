---
id: ENH-16
type: Enhancement
effort: M
sources: Phát hiện trong lúc test BUG-06/ENH-04 thật trên device (Tecno KJ7) — người dùng báo pinch "rất lag" dù BUG-06 (icon cache) đã fix
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
related: BUG-06, ENH-04, ENH-02
---

# Throttle rebuild shader khi pinch — nguyên nhân lag thật (không phải ghi DataStore)

## Mô tả
Sau khi fix `BUG-06` (icon không còn decode lại từ Uri mỗi frame pinch — xác nhận qua log `reusing cached iconBitmap` 100%) và `ENH-04` (bật lại pinch), người dùng test thật vẫn báo pinch "rất lag". Đọc code xác nhận nguyên nhân thật: `applyNewConfig()` gọi `buildIconBitmapShader()`/`buildTextBitmapShader()` lại từ đầu ở **mỗi lần gọi** (tức mỗi frame `onScale`, có thể 60-120 lần/giây khi pinch), và các hàm này cấp phát bitmap MỚI mỗi lần:

```kotlin
// buildIconBitmapShader() — WaterMarkImageView.kt
val scaleBitmap = Bitmap.createScaledBitmap(srcBitmap, scaledW, scaledH, true)!!  // cấp phát #1
val targetBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)  // cấp phát #2
```

2 lần cấp phát bitmap + thao tác scale/vẽ canvas mỗi frame — đây là chi phí CPU/GC thật gây giật, không liên quan gì đến việc ghi DataStore (đã đính chính lại trong `ENH-02` — pinch/kéo không hề ghi DataStore mỗi frame, chỉ ghi 1 lần lúc nhả tay).

## Đề xuất (cần cân nhắc kỹ trước khi chọn hướng, effort có thể lớn hơn M nếu làm kỹ)
Vài hướng khả thi, có thể kết hợp:
1. **Throttle theo thời gian**: chỉ thực sự rebuild shader nếu đã qua ≥N ms (vd 32-50ms, ~20-30fps) kể từ lần rebuild trước; giữa các lần, chỉ `invalidate()` với tham số scale tạm thời qua `Canvas.scale()` trên shader/bitmap ĐÃ CÓ (rẻ hơn nhiều so với build lại) để mắt vẫn thấy phản hồi mượt, rebuild "chuẩn" đầy đủ khi dừng tay (`onScaleEnd`) hoặc theo throttle.
2. **Cache bitmap trung gian theo (kích thước, uri) đã build**: nếu `scaledW/scaledH` mới rất gần giá trị lần trước (vd lệch <2%), tái dùng bitmap cũ thay vì build lại.
3. Đo đạc trước bằng Android Studio Profiler để xác nhận chính xác bitmap allocation là bottleneck chính (không giả định suông) trước khi chọn hướng fix.

## Acceptance Criteria
- [ ] Đo được (Profiler) mức giảm allocation/CPU rõ rệt khi pinch sau khi fix, so với trước.
- [ ] Pinch mượt hơn rõ rệt theo cảm nhận thực tế trên device tầm trung/thấp (không chỉ trên Tecno KJ7).
- [ ] Không phá vỡ độ chính xác kích thước cuối cùng sau khi nhả tay (rebuild đầy đủ ở `onScaleEnd` nếu dùng hướng throttle).
