---
id: ENH-08
type: Enhancement
effort: M
sources: Codex (1/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/data/repo/WaterMarkRepository.kt
verified: true
---

# Model bất biến cho `ImageInfo`/`StateFlow` (`WaterMarkRepository`)

## Mô tả
`ImageInfo` (dòng ~72-220 trong `WaterMarkRepository`) vừa bị mutate trực tiếp (gán field) vừa được copy và phát qua `StateFlow` — trộn 2 phong cách khiến state khó dự đoán, và `DiffUtil`/`AsyncListDiffer` (dùng ở list ảnh) có thể không nhận ra thay đổi nếu object reference không đổi dù field bên trong đã mutate.

## Đề xuất
Chuyển `ImageInfo` sang model bất biến hoàn toàn (mọi thay đổi tạo instance mới qua `copy()`), kèm 1 reducer/update atomic tập trung tại repository thay vì mutate rải rác ở nhiều nơi gọi.

## Acceptance Criteria
- [x] Không còn chỗ nào mutate field của `ImageInfo` trực tiếp (mọi update qua `copy()`) — `ImageInfo` đổi toàn bộ field `var` sang `val` (compiler tự bắt lỗi mọi chỗ mutate còn sót — 2 test file cũ dùng `.apply { field = x }` bị lỗi biên dịch, đã sửa dùng constructor arg). 4 điểm mutate thật tìm được và sửa: `MainViewModel.generateImage()` (width/height/inSample/scaleX/scaleY/exifModel — chuyển sang biến local `var imageInfo` reassign qua `copy()`, không rò rỉ ra ngoài hàm vì các field này chỉ dùng cục bộ), `MainViewModel.generateList()` (jobState/result — list trả về giờ là list COPY MỚI, không phải list gốc), `MainViewModel.resetJobStatus()` (jobState — giờ đẩy qua `waterMarkRepo.updateImageList()` thay vì mutate im lặng), `WaterMarkImageView.applyNewConfig()` (inSample/width/height — phát hiện đang mutate object truyền vào từ ngoài, có thể là cùng tham chiếu với repository).
- [x] DiffUtil nhận diện đúng thay đổi sau khi refactor — phát hiện bug tiềm ẩn ĐÚNG như ticket cảnh báo: `SaveImageListAdapter.differCallback.areItemsTheSame` dùng `oldItem == newItem` (full equals) để xét "cùng item", chỉ "đúng" tình cờ khi object còn mutable (mutate tại chỗ nên old/new luôn trùng reference). Sau khi bất biến, mọi thay đổi jobState tạo instance mới → full equals luôn `false` → DiffUtil coi là item khác hẳn. Sửa: `areItemsTheSame` so theo `uri` (khoá ổn định, đúng chuẩn DiffUtil). Đồng thời `SaveImageListAdapter.updateJobState()` cũ dùng `data.indexOf(it)` (full equals) để tìm vị trí — cũng lỗi tương tự, sửa tìm theo `uri` + thực sự thay thế item trong `differ` qua `submitList()` (trước đây chỉ gọi `notifyItemChanged` suông, không cập nhật `data` thật — sau khi bất biến sẽ khiến `finishCount`/`failCount` (ENH-13) kẹt vĩnh viễn ở state cũ nếu không sửa).

## Kết quả kiểm chứng
- Unit test: `BitmapCacheTest` (data class equals/hashCode không đổi vì refCount/evictedFromCache nằm ngoài primary constructor, không liên quan `val` hoá). 3 test mới: `MainViewModelSaveImageImmutabilityRoboTest` (dùng URI không tồn tại để decode fail nhanh, verify object gốc do caller giữ KHÔNG bị mutate sau `saveImage()`, `saveProcess` nhận đủ chuỗi Ready→Ing→Failure qua các copy mới), `MainViewModelResetJobStatusRoboTest` (verify object gốc không đổi + repository/StateFlow đồng bộ đúng Ready cho mọi ảnh), `SaveImageListAdapterUpdateJobStateRoboTest` (4 case: tìm đúng theo uri dù object khác reference, uri lạ không làm gì, null không làm gì, và case race-condition — xem bug dưới). Toàn bộ 42 test class unit test hiện có (kể cả các test không liên quan trực tiếp ENH-08) chạy qua theo batch nhỏ (do máy dev bị nghẽn tài nguyên nặng trong phiên — xem ghi chú Sprint cuối file) — pass, ngoại trừ 1 test debounce QR không liên quan (`QrCodeBottomSheetFragmentRoboTest.rapidRetyping...`) fail 1 lần khi chạy chung batch nhưng pass ngay khi chạy riêng lẻ — flaky do tải máy, không phải regression (đã xác nhận không đụng file đó trong sprint này).
- **Bug thật phát hiện qua smoke test batch 2 ảnh trên TECNO BG6** (không phải giả định — quan sát trực tiếp): 1 ảnh (icon watermark tile trên ảnh camera full-res, CPU máy tầm trung mất >60s xử lý) export file THÀNH CÔNG và ĐÚNG trên đĩa (đã pull file về kiểm tra: kích thước 3120×5033, watermark tile phủ đều, không lỗi nội dung), nhưng card của ảnh đó trong danh sách kẹt VĨNH VIỄN ở icon "đang xử lý", không bao giờ chuyển sang icon thành công dù dữ liệu thật đã Success từ lâu.
  - **Root cause**: `SaveImageListAdapter.updateJobState()` (bản fix đầu, trước khi phát hiện bug này) build list mới bằng `data.toMutableList()` (`data` = `differ.currentList`) — list này CHỈ cập nhật SAU KHI `AsyncListDiffer` tính xong `DiffUtil.calculateDiff()` trên background executor (bất đồng bộ, không đồng bộ với thời điểm gọi `submitList()`). `MainViewModel.generateList()` gọi `updateJobState()` 4 lần liên tiếp rất nhanh (2 ảnh × 2 bước Ready→Ing→Success) qua `saveProcess` LiveData — khi lần gọi thứ N đọc `data` trước khi lần gọi N-1 kịp áp dụng vào `currentList`, nó vô tình submit 1 list dựa trên snapshot CŨ, xoá mất update của lần N-1 (vì `AsyncListDiffer` chỉ giữ lại kết quả của lần `submitList()` MỚI NHẤT, huỷ bỏ diff job cũ hơn theo generation counter nội bộ).
  - **Fix**: thêm `pendingList: MutableList<ImageInfo>` làm nguồn "sự thật" đồng bộ, mutate trực tiếp theo index NGAY tại lúc gọi (luôn trên main thread, đúng thứ tự FIFO) thay vì đọc lại `differ.currentList` mỗi lần — đảm bảo không bao giờ mất update dù `AsyncListDiffer` chưa kịp áp dụng lần trước.
  - **Regression test**: `SaveImageListAdapterUpdateJobStateRoboTest.updateJobState_rapidSuccessiveCallsForDifferentItems_noUpdateLost` — gọi 4 lần `updateJobState()` liên tiếp KHÔNG `idle()` xen giữa (tái hiện đúng race), chỉ pass với `pendingList`, fail với code dùng `data.toMutableList()`.
  - Verify lại trên chính TECNO BG6 sau fix: export lại đúng cấu hình (Icon watermark từ ảnh camera nặng + 1 ảnh nhẹ) — cả 2 card chuyển thành công đúng lúc, không còn kẹt.
- Rủi ro còn lại đã cân nhắc: không dựng lại `reducer/update atomic tập trung tại repository` như đề xuất "gợi ý" của ticket (chỉ là gợi ý, không phải AC bắt buộc) — thay vào đó sửa đúng tại từng điểm mutate cụ thể đã audit được, giữ diff nhỏ và đúng phạm vi rủi ro đã xác định, tránh rewrite kiến trúc lớn không cần thiết cho đúng 2 AC yêu cầu.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-08`, file ticket = `todo/ENH-08-immutable-state-watermark-repository.md`.
