---
id: BUG-11
type: Bug
priority: P2
effort: XS
sources: Codex, Agy (2/4, verify trực tiếp xác nhận đúng dòng)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
verified: true
---

# `compressImg` không guard danh sách rỗng + leak file tạm

## Mô tả
`compressImg()` (dòng 726-769), đã đọc trực tiếp xác nhận:
```kotlin
appContext.contentResolver.openInputStream(waterMarkRepo.imageInfoList.first().uri)
```
`imageInfoList.first()` ném `NoSuchElementException` nếu danh sách rỗng (crash) khi thao tác nén trùng lúc danh sách ảnh vừa bị xoá hết. Ngoài ra, `File.createTempFile` tạo file tạm không nằm trong khối `try-finally` — nếu nén thất bại giữa chừng hoặc coroutine bị huỷ (user thoát màn hình), file tạm trong cache không được dọn, tích luỹ dung lượng theo thời gian.

## Cách fix đề xuất
- Guard: `imageInfoList.firstOrNull() ?: return@... ` (early return/emit lỗi phù hợp).
- Bọc phần tạo/dùng file tạm trong `try { ... } finally { tempFile.delete() }`.

## Acceptance Criteria
- [ ] Gọi `compressImg` khi danh sách ảnh rỗng không crash.
- [ ] Huỷ coroutine giữa lúc nén (thoát màn hình) không để lại file tạm trong cache.
