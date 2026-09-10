---
id: ENH-15
type: Enhancement
effort: M
sources: Tách từ BUG-05 (phần "BitmapCache không recycle bitmap bị evict") — phát hiện rủi ro crash khi phân tích, cần thiết kế lại trước khi làm
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/BitmapCache.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
related: BUG-05
---

# BitmapCache: recycle bitmap khi bị evict, nhưng phải an toàn với tham chiếu đang dùng

## Mô tả
`BitmapCache` (`LruCache`) không override `entryRemoved()` nên bitmap bị evict khỏi cache không được `recycle()` chủ động — trên API < 26, pixel data Bitmap nằm ở native heap ngoài Java heap, chỉ giải phóng khi GC/finalizer chạy.

**Đã cân nhắc fix trực tiếp (override `entryRemoved` gọi `oldValue?.bitmap?.recycle()`) nhưng PHÁT HIỆN RỦI RO THẬT khi đọc code**: `WaterMarkImageView.applyNewConfig()` giữ tham chiếu trực tiếp (không copy) tới `BitmapValue.bitmap` lấy từ `BitmapCache` (biến `iconBitmap`), rồi tiếp tục vẽ (`buildIconBitmapShader`) bằng chính bitmap đó nhiều frame sau. Nếu cache evict entry này (LRU đầy vì batch nhiều ảnh) và `entryRemoved` tự động `recycle()`, `iconBitmap` trong `WaterMarkImageView` trở thành **con trỏ tới bitmap đã recycle** — lần vẽ tiếp theo (`Canvas.drawBitmap`) sẽ crash `IllegalStateException: Canvas: trying to use a recycled bitmap` ngay trong lúc người dùng đang xem preview, tệ hơn cả bug OOM gốc.

## Vì sao tách riêng khỏi BUG-05
Đây không phải fix 1 dòng — cần cơ chế đảm bảo bitmap chỉ bị recycle khi KHÔNG còn consumer nào giữ tham chiếu (reference counting, hoặc đổi hẳn sang trả bản sao thay vì tham chiếu gốc từ cache). Làm vội trong 1 loop iteration cùng lúc với phần recycle an toàn của BUG-05 sẽ lẫn lộn rủi ro.

## Triển khai (gợi ý sơ bộ, cần thiết kế kỹ trước khi code)
- Phương án A (an toàn, đơn giản hơn): không tự động recycle trong cache — chỉ giảm `cacheSize` hoặc thêm giới hạn số bitmap full-res được giữ, để GC dọn tự nhiên nhanh hơn (không rủi ro use-after-recycle).
- Phương án B (triệt để hơn, effort cao): thêm reference counting cho `BitmapValue` (tăng khi lấy ra dùng, giảm khi consumer xong việc), chỉ `recycle()` khi refcount về 0 VÀ đã bị evict.
- Bất kể phương án nào, cần audit toàn bộ nơi đang giữ tham chiếu trực tiếp bitmap từ `BitmapCache` (hiện biết ít nhất `WaterMarkImageView.iconBitmap`) trước khi bật recycle tự động.

## Acceptance Criteria
- [ ] Batch nhiều ảnh (đủ để cache evict entry cũ) không gây crash "trying to use a recycled bitmap" ở bất kỳ đâu đang hiển thị bitmap từ cache.
- [ ] Bitmap bị evict thực sự được giải phóng bộ nhớ nhanh hơn hiện tại (đo qua Memory Profiler).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-15`, file ticket = `todo/ENH-15-bitmapcache-recycle-an-toan-khi-evict.md`.
