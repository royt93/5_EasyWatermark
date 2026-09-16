---
id: BUG-26
priority: P1
type: Bug
effort: S
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/di/AppModule.kt
  - app/src/main/java/com/mckimquyen/watermark/data/repo/TemplateRepository.kt
---

# Room asset DB lỗi lúc query đầu (không phải lúc `build()`) không được try/catch bảo vệ

## Mô tả
`AppModule.provideYourDatabase()` try/catch chỉ bọc `builder.build()` — Room mở kết nối thật/copy asset DB (`createFromAsset`) LAZY tại QUERY ĐẦU TIÊN, không phải lúc `build()`. `TemplateRepository.getAllTemplate()`/`insertTemplate()`/`deleteTemplate()`/`updateTemplate()` gọi thẳng `templateDao?.xxx()` không có try/catch nào — nếu asset DB hỏng/thiếu, crash ngay tại query đầu tiên thay vì được null-safe như thiết kế `checkIfIsDaoNull()` đang cố làm.

## Triển khai
Bọc try/catch ở `TemplateRepository` quanh các lời gọi `templateDao?.xxx()`, trả kết quả rỗng/thất bại an toàn thay vì để exception văng ra UI layer.

## Acceptance Criteria
- [ ] Giả lập asset DB hỏng (xoá/đổi tên file asset trong test) — `TemplateRepository.getAllTemplate()` không crash, trả Flow rỗng thay vì exception.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-26`, file ticket = `todo/BUG-26-room-asset-db-loi-luc-query-dau-khong-phai-luc-build-khong-d.md`.
