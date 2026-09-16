---
id: ENH-24
type: Enhancement
effort: XS
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/data/repo/UserConfigRepository.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
---

# `UserConfigRepository` ghi DataStore vô ích mỗi lần mở app cho tính năng đã gỡ (changelog)

## Mô tả
`KEY_CHANGE_LOG` dùng nhầm constant `WaterMarkRepository.SP_KEY_CHANGE_LOG` (thuộc DataStore file KHÁC) thay vì tự định nghĩa key riêng. Nơi đọc duy nhất (`AboutActivity` `tvChangeLog`) đã bị comment out từ trước — tính năng hiển thị changelog đã gỡ bỏ khỏi UI, nhưng `saveVersionCode()` (gọi từ `MainViewModel`) vẫn ghi DataStore mỗi lần app khởi chạy, hoàn toàn không ai đọc lại.

## Triển khai
Xoá lời gọi `saveVersionCode()`/`KEY_CHANGE_LOG` chết này nếu xác nhận tính năng changelog không có kế hoạch khôi phục; nếu có kế hoạch khôi phục thì định nghĩa key riêng đúng DataStore file của nó.

## Acceptance Criteria
- [ ] Sau khi xoá, mở app nhiều lần — không còn write DataStore thừa cho key này (kiểm tra qua log DataStore hoặc test).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-24`, file ticket = `todo/ENH-24-userconfigrepository-ghi-datastore-vo-ich-moi-lan-mo-app-cho.md`.
