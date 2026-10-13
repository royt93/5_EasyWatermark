---
id: BUG-84
type: Bug
priority: P2
effort: S
sources: R5 a11y re-audit 2026-10-14 (verify tay: grep contentDescription + ItemClickSupport)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/adapter/PhotoListPreviewAdapter.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/PhotoPreviewItem.kt
---

# BUG-84: Filmstrip ảnh (`rvPhotoList`) không có `contentDescription`, TalkBack không đọc được

**Priority:** P2 (accessibility, vi phạm R5 "không bỏ accessibility")
**Effort:** S
**Status:** todo

## Vấn đề

`PhotoPreviewItem` + `PhotoListPreviewAdapter` không gán `contentDescription`, `isClickable`, `isFocusable` ở bất kỳ đâu (grep 0 kết quả). Thumbnail trong batch thành "ảnh không tên" với TalkBack; không biết đang ở ảnh thứ mấy. `GalleryAdapter` (cùng khái niệm) đã gán `gallery_photo_item_desc` ("Photo %1$d") — filmstrip bị bỏ sót.

Click đi qua `ItemClickSupport` (touch theo toạ độ), không qua `View.OnClickListener`, nên chỉ thêm label chưa chắc kích hoạt được bằng double-tap.

## Đề xuất

1. Trong `PhotoListPreviewAdapter.processUI()` gán `holder.itemView.contentDescription = context.getString(R.string.gallery_photo_item_desc, position + 1)` (tái dùng key đã dịch đủ 13 locale — không thêm string mới).
2. Kiểm tra `ItemClickSupport` có phát `ACTION_CLICK` cho accessibility không; nếu không, thêm `AccessibilityDelegate`/`performClick()` để double-tap chọn được ảnh. Nhớ gỡ listener/delegate khi recycle (xem `resetForRecycle()` BUG-81).
3. Giữ nguyên gesture kéo-xoá; thêm accessibility action "Xoá ảnh" nếu làm bước 2.

KHÔNG đụng chuỗi/UI của khung quảng cáo.

## Test

- Widget (Robolectric): bind adapter 3 item, assert `contentDescription` = "Photo 1/2/3" đúng vị trí và cập nhật khi list đổi thứ tự.
- Widget: `performAccessibilityAction(ACTION_CLICK)` trên item gọi đúng callback chọn ảnh.
- Smoke: bật TalkBack trên thiết bị đã khoá, duyệt filmstrip, nghe đủ vị trí. Nếu không bật được TalkBack qua adb thì ghi rõ giới hạn.

## Prompt loop

Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).
