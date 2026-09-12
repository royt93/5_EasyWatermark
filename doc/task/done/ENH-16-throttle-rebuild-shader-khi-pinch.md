---
id: ENH-16
type: Enhancement
effort: M
sources: Phát hiện trong lúc test BUG-06/ENH-04 thật trên device (Tecno KJ7) — người dùng báo pinch "rất lag" dù BUG-06 (icon cache) đã fix
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
related: BUG-06, ENH-04, ENH-02
verified: true
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
- [x] Đo được mức giảm allocation/CPU rõ rệt khi pinch sau khi fix, so với trước — chọn Hướng 1 (throttle theo thời gian, 40ms ~25fps), chứng minh bằng test mô phỏng 120fps/1s: số lần rebuild thật giảm từ 120 xuống ≤30 (giảm >75%); không đo được bằng Android Studio Profiler qua CLI/ADB (cần UI), xem giới hạn bên dưới.
- [ ] Pinch mượt hơn rõ rệt theo cảm nhận thực tế trên device tầm trung/thấp — KHÔNG kiểm chứng được: `adb shell input` không hỗ trợ multi-touch/pinch thật (chỉ 1 pointer), cần người dùng thao tác tay thật trên device để xác nhận cảm nhận.
- [x] Không phá vỡ độ chính xác kích thước cuối cùng sau khi nhả tay — `onScaleEnd` force-apply `pendingTextSize` nếu khác `config.textSize` hiện tại (bù trường hợp frame cuối bị throttle bỏ qua), đảm bảo giá trị hiển thị lúc nhả tay luôn khớp giá trị pinch cuối cùng, không phụ thuộc throttle.

## Kết quả kiểm chứng
- Unit test `WaterMarkImageViewShaderThrottleTest`: 5/5 pass — biên throttle (`<40ms` không rebuild, `=40ms`/`>40ms` rebuild), frame đầu gesture (`lastRebuildAtMs=0`) luôn rebuild ngay, và mô phỏng pinch 120fps/1s cho `rebuildCount` trong khoảng [tối đa 30, < 120].
- Không giả lập được pinch 2 ngón thật qua `adb shell input` (chỉ có `tap`/`swipe`/`motionevent` 1 pointer, không có lệnh multi-touch) — đã thử và xác nhận giới hạn công cụ, không cố dùng `sendevent` thô (rủi ro cao, không đáng vì logic throttle là hàm thuần không phụ thuộc runtime Android thật).
- Smoke test thật trên Samsung Galaxy S24 Ultra (SM-S928B): mở editor, chuyển tab Text/Icon, chuyển ảnh trong batch nhiều lần trong lúc watermark Text đang active — không crash, không lỗi vẽ liên quan `WaterMarkImageView`; không bao phủ được cảm nhận "mượt khi pinch" (cần test tay thật, ghi lại trong AC còn mở phía trên).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-16`, file ticket = `todo/ENH-16-throttle-rebuild-shader-khi-pinch.md`.
