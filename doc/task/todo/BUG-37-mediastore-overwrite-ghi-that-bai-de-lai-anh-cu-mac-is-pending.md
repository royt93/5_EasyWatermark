---
id: BUG-37
type: Bug
priority: P1
effort: S
sources: Claude self-audit 2026-09-27 (đọc trực tiếp, verify dòng)
files:
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt
---

# MediaStore OVERWRITE: ghi thất bại để lại ảnh CŨ mắc `IS_PENDING=1` (mất ảnh khỏi gallery)

## Mô tả
`BatchExportEngine.generateImage()` nhánh Android Q+ (`BatchExportEngine.kt:603-650`):

```kotlin
val (targetUri, isNewRow) = if (existingUri != null && conflictPolicy == ConflictPolicy.OVERWRITE) {
    contentResolver.update(existingUri, ContentValues().apply { put(IS_PENDING, 1) }, null, null)
    existingUri to false            // isNewRow = false
} else { ... insert ... }
...
if (writeResult.isFailure()) {
    if (isNewRow) { contentResolver.delete(imageContentUri, null, null) }  // dòng 646
    return@withContext Result.extendMsg(writeResult)
}
```

Khi conflict policy = OVERWRITE và `openFileDescriptor`/`compress` thất bại (hết dung lượng, mất
quyền giữa batch, provider từ chối): row bị đánh `IS_PENDING=1` ở dòng 607 **không bao giờ được
reset về 0** — nhánh cleanup chỉ chạy khi `isNewRow == true`. Ảnh CŨ của user (file có sẵn từ lần
export trước, không phải file rác app vừa tạo) bị hệ thống ẩn khỏi gallery/Photos vĩnh viễn cho
tới khi có app khác ghi lại cùng uri. Không crash, không báo lỗi gì ngoài "1 ảnh thất bại" —
user chỉ thấy ảnh cũ tự nhiên mất.

BUG-19 đã xử lý đúng nhánh insert (`isNewRow`), nhưng thời điểm đó chưa có OVERWRITE
(FEAT-19 thêm sau) nên nhánh này chưa từng được cover.

## Cách fix đề xuất
Trong nhánh `writeResult.isFailure()`: nếu `!isNewRow` → `contentResolver.update(imageContentUri,
ContentValues().apply { put(IS_PENDING, 0) }, null, null)` để trả ảnh cũ về trạng thái hiển thị,
thay vì chỉ `delete` khi là row mới. Tách thành hàm thuần/nhỏ (pattern `MediaStoreWriteResolver`)
để test được quyết định "delete row mới vs clear pending row cũ".

## Acceptance Criteria
- [ ] Ghi thất bại với `conflictPolicy = OVERWRITE` → ảnh cũ vẫn hiện trong gallery (`IS_PENDING` = 0).
- [ ] Ghi thất bại với row MỚI (KEEP_BOTH/RENAME_VERSION) → vẫn xoá row rác như BUG-19 (không regression).
- [ ] Unit test cover cả 2 nhánh cleanup (row mới vs row ghi đè).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-37`, file ticket = `todo/BUG-37-mediastore-overwrite-ghi-that-bai-de-lai-anh-cu-mac-is-pending.md`.
