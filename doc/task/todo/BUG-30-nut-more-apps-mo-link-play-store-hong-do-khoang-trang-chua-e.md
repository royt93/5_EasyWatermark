---
id: BUG-30
priority: P2
type: Bug
effort: XS
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/about/AboutActivity.kt
---

# Nút "More Apps" mở link Play Store hỏng do khoảng trắng chưa encode + sai định dạng id

## Mô tả
`openLink("https://play.google.com/store/apps/developer?id=SAIGON PHANTOM LABS")` — khoảng trắng trong tên công ty chưa được URL-encode, và tham số `id=` của trang Play Store developer page cần ID số/định danh hệ thống (vd `id=1234567890...`) chứ không phải tên công ty dạng chuỗi thô. Bấm nút nhiều khả năng mở ra trang lỗi/không tìm thấy thay vì trang developer thật.

## Triển khai
Lấy đúng Developer ID số từ Play Console (hoặc dùng URL dạng `search?q=pub:"Saigon Phantom Labs"` nếu không có ID số), encode đúng nếu vẫn cần khoảng trắng.

## Acceptance Criteria
- [ ] Bấm nút "More Apps" trên thiết bị thật có Play Store — mở đúng trang liệt kê app của developer, không phải trang lỗi.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-30`, file ticket = `todo/BUG-30-nut-more-apps-mo-link-play-store-hong-do-khoang-trang-chua-e.md`.
