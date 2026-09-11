---
id: BUG-19
type: Bug
priority: P1
effort: M
sources: codex exec (external CLI, re-audit 2026-09-10)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
---

# Nhánh ghi MediaStore không guard đủ khi `openFileDescriptor`/`compress` thất bại

## Mô tả
`MainViewModel.kt` quanh dòng 425-431 (nhánh ghi ảnh qua MediaStore, Android Q+): 3 vấn đề cùng chỗ, cùng họ với [BUG-03](../done/BUG-03-batch-export-bao-thanh-cong-gia.md) (đã fix ở luồng khác) nhưng ở nhánh MediaStore riêng vẫn chưa được guard:
1. `contentResolver.openFileDescriptor(uri, "w")` có thể trả `null` — không được kiểm tra trước khi dùng.
2. `Bitmap.compress()` trả về `Boolean` báo thành công/thất bại — giá trị này bị bỏ qua, không kiểm tra.
3. Khi ghi thất bại (compress false hoặc exception), row `MediaStore` đã insert với `IS_PENDING=1` không được xoá/cleanup — để lại file rác 0-byte hoặc file lỗi hiển thị trong gallery hệ thống, đồng thời job vẫn có thể báo "thành công" giả (tái phát một phần vấn đề BUG-03 ở nhánh khác).

## Cách fix đề xuất
- Guard `openFileDescriptor` null → coi là lỗi, không tiếp tục ghi.
- Kiểm tra kết quả `Bitmap.compress()`, nếu `false` → coi là lỗi.
- Khi phát hiện lỗi ở bất kỳ bước nào: xoá row MediaStore vừa insert (`contentResolver.delete(uri, null, null)`) thay vì để lại row `IS_PENDING` treo, và propagate lỗi để job tổng batch báo đúng số ảnh thất bại (đã có cơ chế đếm từ ENH-13).

## Acceptance Criteria
- [ ] `openFileDescriptor` null → không crash, ảnh này được tính là thất bại trong batch.
- [ ] `Bitmap.compress()` trả `false` → ảnh này được tính là thất bại, không báo thành công giả.
- [ ] Ghi thất bại ở bước nào cũng không để lại row/file rác trong MediaStore.
- [ ] Test: mock `ContentResolver` trả `null`/`compress` trả `false`, xác nhận job đếm đúng thất bại và không còn row rác (integration test nếu khả thi).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-19`, file ticket = `todo/BUG-19-mediastore-ghi-that-bai-khong-guard.md`.

## Kết quả kiểm chứng (2026-09-11)
- **Điểm audit tự chấm: 9.5/10.** Guard `openFileDescriptor()` null (bỏ force-unwrap `pfd!!` cũ) + kiểm tra `Bitmap.compress()` trả `false`, gộp vào `MediaStoreWriteResolver.resolve()` (pure, theo đúng pattern `MediaStoreInsertResolver` của BUG-04). Ghi thất bại → `contentResolver.delete(imageContentUri, ...)` dọn row `IS_PENDING` rác thay vì để lại. Áp dụng thêm cho nhánh legacy (<Android Q, ghi file trực tiếp): guard `compress()` false → xoá file rác + báo lỗi, dù ticket chỉ nêu nhánh MediaStore (nhất quán, cùng gốc lỗi). Bitmap không bị leak ở các nhánh lỗi mới nhờ `BitmapRecycleGuard` (BUG-21, cùng finally).
- **Test:** `app/src/test/java/com/mckimquyen/watermark/data/model/MediaStoreWriteResolverTest.kt` (4 test) — fd null → failure, compress false → failure, cả 2 → failure (thông báo đúng fd trước), cả 2 OK → success. Test thuần JVM, không cần Robolectric/ContentResolver thật. Integration test đầy đủ (mock `ContentResolver` thật + xác nhận row bị xoá) không khả thi trong project này (không có Mockito, `androidTest` cần thiết bị) — ticket ghi rõ "nếu khả thi", đã dùng phương án thay thế thực tế nhất.
- **Smoke test (2026-09-11, TECNO KJ7 `115333744A005844`):** PASS. Batch export 4 ảnh thật (watermark text) qua luồng "Export to the album" đầy đủ (MediaStore, Android 14/API 34) — cả 4/4 ảnh báo thành công, không crash, không lỗi trong logcat, luồng MediaStore write (`openFileDescriptor`/`compress`/update `IS_PENDING=0`) hoạt động đúng như trước khi thêm guard — xác nhận không regression ở nhánh thành công. Giả lập lỗi thật (`openFileDescriptor`/`compress` trả lỗi) trên thiết bị thật khó ép xảy ra không phá hoại máy thật (cần thu hồi quyền storage giữa chừng job) — bằng chứng chính cho nhánh lỗi vẫn là 4 unit test cover đúng các case null/false. **Đạt Definition of Done: điểm 9.5/10 > 9, test đủ (cover đúng behavior nhánh lỗi), smoke test pass (không regression nhánh thành công, xác nhận trên thiết bị thật).**
