---
id: ENH-08
type: Enhancement
effort: M
sources: Codex (1/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/data/repo/WaterMarkRepository.kt
---

# Model bất biến cho `ImageInfo`/`StateFlow` (`WaterMarkRepository`)

## Mô tả
`ImageInfo` (dòng ~72-220 trong `WaterMarkRepository`) vừa bị mutate trực tiếp (gán field) vừa được copy và phát qua `StateFlow` — trộn 2 phong cách khiến state khó dự đoán, và `DiffUtil`/`AsyncListDiffer` (dùng ở list ảnh) có thể không nhận ra thay đổi nếu object reference không đổi dù field bên trong đã mutate.

## Đề xuất
Chuyển `ImageInfo` sang model bất biến hoàn toàn (mọi thay đổi tạo instance mới qua `copy()`), kèm 1 reducer/update atomic tập trung tại repository thay vì mutate rải rác ở nhiều nơi gọi.

## Acceptance Criteria
- [ ] Không còn chỗ nào mutate field của `ImageInfo` trực tiếp (mọi update qua `copy()`).
- [ ] DiffUtil nhận diện đúng thay đổi sau khi refactor (kiểm tra UI list cập nhật đúng khi đổi selected/offset/jobState).
