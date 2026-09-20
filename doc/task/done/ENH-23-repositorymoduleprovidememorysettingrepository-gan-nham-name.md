---
id: ENH-23
type: Enhancement
effort: XS
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/di/RepositoryModule.kt
  - app/src/main/java/com/mckimquyen/watermark/data/repo/MemorySettingRepo.kt
---

# `RepositoryModule.provideMemorySettingRepository()` gắn nhầm `@Named("WaterMarkPreferences")`

## Mô tả
`provideMemorySettingRepository()` gắn `@Named("WaterMarkPreferences")` dù `MemorySettingRepo` không nhận `DataStore` nào (constructor rỗng, không liên quan preferences) — copy-paste sai từ provider bên cạnh. Hiện KHÔNG gây lỗi vì không có injection site nào request `@Named("WaterMarkPreferences") MemorySettingRepo` (chỉ inject plain `MemorySettingRepo` qua `@Inject constructor` nếu có, hoặc qua provider này nếu không) — nhưng là bẫy DI, dễ gây confusion/lỗi thật nếu sau này ai vô tình thêm `@Named` này ở injection site khác, tạo 2 Singleton độc lập ngoài ý muốn.

## Triển khai
Xoá `@Named("WaterMarkPreferences")` khỏi `provideMemorySettingRepository()` (không cần qualifier vì không có ambiguity thật).

## Acceptance Criteria
- [ ] Build + toàn bộ test pass sau khi xoá qualifier — không injection site nào phụ thuộc qualifier này (đã grep xác nhận).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-23`, file ticket = `todo/ENH-23-repositorymoduleprovidememorysettingrepository-gan-nham-name.md`.
