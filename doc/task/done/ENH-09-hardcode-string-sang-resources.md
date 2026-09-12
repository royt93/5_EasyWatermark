---
id: ENH-09
type: Enhancement
effort: M
sources: Codex (1/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainActivity.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/GalleryFragment.kt
verified: true
---

# Đưa hardcode string UI sang `resources` (i18n/accessibility)

## Mô tả
`MainActivity` (dòng ~98-175), `GalleryFragment` (dòng ~211-239) và rải rác nơi khác còn nhiều chuỗi UI hard-code trực tiếp trong code (vd "Signature", "Leica EXIF", "Select photo/selected") thay vì `strings.xml`. Ảnh hưởng: khó dịch đa ngôn ngữ, không hỗ trợ plural đúng chuẩn Android, thiếu nhất quán accessibility (TalkBack đọc string cố định).

## Đề xuất
Audit toàn bộ string hardcode trong `ui/`, chuyển vào `res/values/strings.xml` (dùng `<plurals>` cho các trường hợp đếm số lượng ảnh/item).

## Acceptance Criteria
- [x] Không còn string UI hardcode trực tiếp trong code Kotlin (trừ log/debug) — audit toàn bộ `ui/` (không chỉ 2 file nêu trong ticket, theo đúng mô tả "audit toàn bộ string hardcode trong ui/"), tìm và sửa 6 chỗ: `MainActivity.kt` ("Signature", "Leica EXIF" → `func_title_signature`/`func_title_leica_exif`), `GalleryFragment.kt` (label FAB + hint chọn ảnh), `SaveImageBSDialogFragment.kt` (Toast lỗi share "Share error with ${e.message}" → `share_error`), `AboutActivity.kt` ("v${BuildConfig.VERSION_NAME}" → `about_version_display`). Các chuỗi còn lại khớp `"[A-Za-z ]{3,}"` trong `ui/` đã rà soát đều là payload key nội bộ (`notifyItemChanged(pos, "Selected")`), bundle key, file path, hoặc tên định dạng kỹ thuật (`"JPEG"/"PNG"/"WEBP"`) — không phải văn bản hiển thị cần dịch, giữ nguyên.
- [x] Các chuỗi đếm số lượng dùng `<plurals>` đúng chuẩn — thêm `gallery_select_photo_count` và `gallery_selected_count` (2 quantity `one`/`other`), thay hẳn `if (count == 1) "..." else "..."` cũ trong `GalleryFragment`.

## Kết quả kiểm chứng
- String mới chỉ thêm vào `values/strings.xml` (English, base), không dịch sang 11 locale khác (de/es/fr/it/ja/nb/pt/pt-BR/ru/vi/zh-CN/zh-TW) — theo đúng tiền lệ đã thiết lập trong sprint trước (`dialog_save_export_count_with_failures` của ENH-13 cũng chỉ thêm ở base, chưa dịch); các locale khác sẽ fallback về tiếng Anh cho string mới, không phải regression.
- Unit test: `GalleryFragmentSelectionLabelRoboTest` (mới, 2 case) — đẩy trực tiếp `GalleryAdapter.selectedCount` (LiveData công khai, đúng nguồn observer thật lắng nghe) verify FAB hiện đúng "Select 1 photo" (singular) và "Select 2 photos" (plural), hint hiện đúng "1 selected"/"2 selected" — không dựng lại toàn bộ cơ chế chọn ảnh qua RecyclerView thật (click/drag qua `GalleryAdapter.select()`) vì đó không phải phần logic ENH-09 thay đổi, chỉ risk thêm độ phức tạp test không cần thiết.
- Smoke test thật trên thiết bị (xem chi tiết BACKLOG.md phần sprint): quan sát trực quan panel tab "Signature"/"Leica EXIF" hiển thị đúng chữ như trước, label chọn ảnh gallery đúng số lượng.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-09`, file ticket = `todo/ENH-09-hardcode-string-sang-resources.md`.
