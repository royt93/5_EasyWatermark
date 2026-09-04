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
- [x] Không còn `!!` trên `imageContentUri` — extract `MediaStoreInsertResolver.resolve()` (pure function, theo pattern `JobStateResolver`/`TextTokenResolver` đã có).
- [x] Trường hợp `insert()` trả null có unit test trực tiếp (`MediaStoreInsertResolverTest`, 2/2 pass: null → failure đúng code, non-null → success đúng data).

## Kết quả kiểm chứng
- Unit test: `MediaStoreInsertResolverTest` (2/2 pass), full suite 41/41 pass, không regression.
- Compile sạch, không lint violation mới.
- Smoke test thật trên Pixel 7 Pro (Android 16, chắc chắn đi qua nhánh Q+): export ảnh thật qua UI 2 lần (trước và sau khi extract resolver) → thành công cả 2 lần, checkmark xanh, logcat `generateList` xác nhận, không crash.
- Nhánh `insert()` trả null (thực tế hiếm, cần MediaStore từ chối/hết dung lượng) không mô phỏng được an toàn trên device thật — coverage dựa vào unit test resolver (đã tách pure logic, không phụ thuộc Android runtime thật cho phần quyết định).
