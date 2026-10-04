---
id: BUG-73
type: Bug
priority: P1
effort: M
sources: re-audit 2026-10-04 (2 agent đọc code) + verify tay hình dạng code; chưa tái hiện trên máy trừ khi ghi khác
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/BitmapCache.kt
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt
---

# Bitmapcache tra value chua retain co the bi recycle truoc khi caller dung

## Mô tả
`getFromCache()` trả `BitmapValue` khi `refCount` còn 0; caller (`BatchExportEngine.kt` ~987-999) chỉ `retain()` SAU khi hàm suspend trả về. Giữa 2 thao tác, thread khác có thể `evictAll()`/evict → `markEvictedAndRecycleIfUnused()` thấy refCount 0 và recycle bitmap; caller retain một value đã chứa bitmap recycled. (Đã verify hình dạng code; **race hẹp, chưa tái hiện**.)

**Kịch bản:** Preview/export lấy cache hit đúng lúc batch khác kết thúc và gọi `clearCache()` → `copy()`/`drawBitmap()` kế tiếp ném `IllegalStateException`/"recycled bitmap".

## Đề xuất
Thay `getFromCache()` bằng thao tác acquire nguyên tử: retain thành công mới trả; trạng thái evicted chặn retain để caller decode lại (synchronized/CAS).

## Acceptance Criteria
- [ ] Có test đồng thời (nhiều thread get/evict) không bao giờ trả bitmap recycled.
- [ ] Không đổi hành vi cache hit bình thường.
- [ ] Hiệu năng cache không giảm đáng kể.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-73`, file ticket = `todo/BUG-73-bitmapcache-tra-value-chua-retain-co-the-bi-recycle-truoc-khi-caller-dung.md`.
