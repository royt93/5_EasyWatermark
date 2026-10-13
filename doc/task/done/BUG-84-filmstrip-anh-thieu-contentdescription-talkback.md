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
**Status:** done

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

## Kết quả kiểm chứng (2026-10-14)

- **Fix**: `PhotoListPreviewAdapter.processUI()` gán `contentDescription = gallery_photo_item_desc(position + 1)` (tái dùng key đã dịch 13 locale), `isClickable`/`isFocusable = true`; `onViewRecycled()` xoá nhãn để holder tái dùng không giữ nhãn cũ. Hằng `POSITION_NUMBER_OFFSET`. Không đụng gesture kéo-xoá.
- **Widget (Robolectric)** `PhotoListPreviewAccessibilityWidgetTest` 3 case: nhãn đúng vị trí + clickable/focusable; recycle xoá nhãn; `ACTION_CLICK` gọi đúng `onItemClick` selection. RED thật trước khi sửa: `contentDescription` là `null`.
- **Integration (androidTest, Pixel 7 Pro `2B051FDH3006MU`)** `PhotoListPreviewAccessibilityIntegrationTest`: lấy `AccessibilityNodeInfo` thật từ item — có `contentDescription` đúng, `isClickable`, action `ACTION_CLICK`, và thực thi action gọi đúng callback. PASS.
- **Regression**: ktlint + lint pass. Full `testDebugUnitTest` 1507 test, 1 fail là `MainViewModelCompressImgRoboTest.compressImg_emptyImageList_doesNotCrash_andPostsFailure` — chạy riêng 2 lần (có `--rerun-tasks`) đều pass; khớp flaky cross-JVM-fork đã ghi ở `app/build.gradle.kts`, không nằm trên đường code đã sửa.
- **Smoke Pixel 7 Pro**: chọn 2 ảnh Watermark → editor → filmstrip hiện 2 thumbnail, không crash, không `FATAL EXCEPTION`. Ảnh smoke không lưu/chia sẻ.
- **Giới hạn**: `uiautomator dump` trả null root trên máy này (có accessibility service bên thứ ba `PowerMenuService`/`LockAccessibilityService` đang bật); TalkBack thật không bật được qua adb → xác minh bằng `AccessibilityNodeInfo` trong instrumentation thay vì nghe TalkBack. Chưa nghe thử bằng TalkBack thật.
- **Tự audit**: 9.0/10 — diff nhỏ, test đủ 3 tầng; trừ điểm vì chưa nghe TalkBack thật.
