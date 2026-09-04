---
id: ENH-07
type: Enhancement
effort: XS
sources: Internal, Agy (2/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/data/repo/SignatureRepository.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/SignatureActivity.kt
---

# `SignatureRepository` dùng raw Context thay vì Hilt `@ApplicationContext`

## Mô tả
`SignatureRepository` (dòng ~19) nhận `Context` thô, được khởi tạo thủ công tại `SignatureActivity.kt:73` thay vì qua Hilt `@Singleton @Inject` như mọi repo khác trong `data/repo/`. Không nhất quán kiến trúc, và phụ thuộc thủ công vào Activity context là nguồn leak tiềm ẩn nếu repo giữ tham chiếu lâu dài.

## Đề xuất
Đưa `SignatureRepository` vào DI module (`RepositoryModule.kt`) như các repo khác, inject `@ApplicationContext Context` qua constructor Hilt.

## Acceptance Criteria
- [ ] `SignatureRepository` được cung cấp qua Hilt, không khởi tạo thủ công trong Activity.
- [ ] Không còn phụ thuộc trực tiếp vào Activity context trong repo.
