---
id: ENH-09
type: Enhancement
effort: M
sources: Codex (1/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainActivity.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/GalleryFragment.kt
---

# Đưa hardcode string UI sang `resources` (i18n/accessibility)

## Mô tả
`MainActivity` (dòng ~98-175), `GalleryFragment` (dòng ~211-239) và rải rác nơi khác còn nhiều chuỗi UI hard-code trực tiếp trong code (vd "Signature", "Leica EXIF", "Select photo/selected") thay vì `strings.xml`. Ảnh hưởng: khó dịch đa ngôn ngữ, không hỗ trợ plural đúng chuẩn Android, thiếu nhất quán accessibility (TalkBack đọc string cố định).

## Đề xuất
Audit toàn bộ string hardcode trong `ui/`, chuyển vào `res/values/strings.xml` (dùng `<plurals>` cho các trường hợp đếm số lượng ảnh/item).

## Acceptance Criteria
- [ ] Không còn string UI hardcode trực tiếp trong code Kotlin (trừ log/debug).
- [ ] Các chuỗi đếm số lượng dùng `<plurals>` đúng chuẩn.
