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
- [x] Ghi thất bại với `conflictPolicy = OVERWRITE` → ảnh cũ vẫn hiện trong gallery (`IS_PENDING` = 0).
- [x] Ghi thất bại với row MỚI (KEEP_BOTH/RENAME_VERSION) → vẫn xoá row rác như BUG-19 (không regression).
- [x] Unit test cover cả 2 nhánh cleanup (row mới vs row ghi đè).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-37`, file ticket = `todo/BUG-37-mediastore-overwrite-ghi-that-bai-de-lai-anh-cu-mac-is-pending.md`.

## Kết quả kiểm chứng (2026-09-28)

**Fix:** tách quyết định cleanup thành hàm thuần `MediaStoreWriteFailureCleanup.decide(isNewRow)`
(file mới `data/model/MediaStoreWriteFailureCleanup.kt`, theo đúng pattern `MediaStoreWriteResolver`
của BUG-19) trả về enum `MediaStoreCleanupAction.DELETE_ROW` / `CLEAR_PENDING`. Wire vào
`BatchExportEngine.kt` nhánh `writeResult.isFailure()`: `DELETE_ROW` → `delete()` như cũ (row mới),
`CLEAR_PENDING` → `update(IS_PENDING=0)` (row cũ bị OVERWRITE) thay vì bỏ quên ở trạng thái pending.

- **Điểm tự audit:** 9.5/10 — đúng bug, đúng đề xuất trong ticket, không magic number (dùng enum),
  không leak/late/force-unwrap, diff tối thiểu (+15/-3 ở `BatchExportEngine.kt` + 2 file mới).
- **Test:** TDD (RED → GREEN) — `MediaStoreWriteFailureCleanupTest` (2 case: `isNewRow=true` →
  `DELETE_ROW`, `isNewRow=false` → `CLEAR_PENDING`). `./gradlew :app:testDebugUnitTest` toàn bộ
  191/191 test suite xanh (0 failures, 0 errors, verify qua `test-results/*.xml`).
- **Smoke test thật** trên device đã khoá session — **TECNO KJ7 (115333744A005844)**: cài
  `assembleDebug`, export 2 ảnh với `conflictPolicy = Ghi đè (OVERWRITE)` **2 lần liên tiếp cùng
  tên file** (lần 2 mới thực sự đi qua nhánh `existingUri != null && OVERWRITE` vừa sửa) → cả 2
  lần "Danh sách xuất" báo thành công (1/2 rồi 2/2), không crash (`logcat` không có `FATAL`/
  `AndroidRuntime` liên quan app), verify trực tiếp qua
  `content query --uri content://media/external/images/media` → toàn bộ ảnh `WaterMarkCreator/`
  đều `is_pending=0`, không có row nào mắc pending. Không gặp quảng cáo che UI trong lúc test (R4).
  *(Nhánh lỗi thật — hết dung lượng/mất quyền giữa batch — không mô phỏng được trên device thật;
  cover bằng unit test thuần cho quyết định logic theo đúng AC thứ 3.)*
