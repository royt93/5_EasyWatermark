---
id: BUG-04
type: Bug
priority: P0
effort: S
sources: Codex, Internal (2/4, verify trực tiếp xác nhận đúng dòng)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
verified: true
---

# `contentResolver.insert()!!` crash khi MediaStore trả null

## Mô tả
Đã đọc trực tiếp, xác nhận dòng 387-388:
```kotlin
val imageContentUri = contentResolver.insert(imageCollection, imageDetail)
contentResolver.openFileDescriptor(imageContentUri!!, "w", null).use { pfd -> ... }
```
`ContentResolver.insert()` **có thể trả `null`** theo tài liệu chính thức (provider từ chối, hết dung lượng, lỗi nội bộ MediaStore). Code force-unwrap `!!` ngay sau đó → `NullPointerException` crash thay vì rơi vào nhánh lỗi như các chỗ khác trong cùng hàm (vốn đã trả `Result.failure` đàng hoàng cho các lỗi khác).

## Cách fix đề xuất
```kotlin
val imageContentUri = contentResolver.insert(imageCollection, imageDetail)
    ?: return@withContext Result.failure(null, code = TYPE_ERROR_SAVE_...)
```
Thêm mã lỗi mới (vd `TYPE_ERROR_MEDIASTORE_INSERT_FAILED`) nhất quán với các `Result.failure` khác trong hàm.

## Acceptance Criteria
- [ ] Không còn `!!` trên `imageContentUri`.
- [ ] Trường hợp `insert()` trả null có test mô phỏng (hoặc ít nhất review code path) trả `Result.failure` thay vì crash.
