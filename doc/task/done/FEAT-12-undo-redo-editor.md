---
id: FEAT-12
type: Feature
effort: M
sources: Internal (1/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/data/repo/WaterMarkRepository.kt
---

# Undo/Redo chỉnh sửa watermark trong editor

## Mô tả
Thêm Undo/Redo cho chỉnh sửa watermark trong editor (vị trí, size, màu, góc xoay...) — kiến trúc state hiện tại (Flow tập trung 1 điểm qua `WaterMarkRepository`) khá phù hợp để thêm nhanh cơ chế này.

## Triển khai
Lưu stack snapshot `WaterMark` mỗi lần thay đổi có ý nghĩa (không phải mỗi frame gesture — nên kết hợp debounce từ `ENH-02` để tránh stack quá dày), thêm 2 nút Undo/Redo trên toolbar editor.

## Acceptance Criteria
- [x] Undo hoàn tác đúng thay đổi gần nhất (vị trí/size/màu/góc xoay).
- [x] Redo khôi phục lại thay đổi vừa undo.
- [x] Stack không phình to bất thường khi thao tác kéo/pinch liên tục (nhờ debounce).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-12`, file ticket = `todo/FEAT-12-undo-redo-editor.md`.

## Kết quả kiểm chứng
- `WaterMarkRepository` giữ `undoStack`/`redoStack` (`ArrayDeque<WaterMark>`, giới hạn `MAX_UNDO_STACK = 20`) + `canUndo`/`canRedo` (`StateFlow<Boolean>`) — đúng gợi ý kiến trúc của ticket ("Flow tập trung 1 điểm"), không cần thêm tầng state mới.
- `snapshotForUndoIfDue()` (private) gọi ở ĐẦU mọi `updateXxx()` có ý nghĩa với Undo (text, textSize, color, textStyle, typeface, alpha, hGap, vGap, degree, icon, enableExif, exifFrameStyle, toggleBounds, anchor, margin, 3 field EXIF override, 3 field text-effect, resetExifCustomization — 22 hàm) — LUÔN xoá `redoStack` (edit mới huỷ nhánh redo cũ, đúng ngữ nghĩa chuẩn), nhưng CHỈ đẩy entry vào `undoStack` nếu đã quá `UNDO_SNAPSHOT_DEBOUNCE_MS = 400ms` kể từ lần đẩy trước — đây là cách xử lý AC3: ghi DataStore vẫn xảy ra NGAY mỗi lần gọi (preview tức thời không bị trễ), chỉ riêng việc TẠO ĐIỂM UNDO mới bị debounce, nên 1 gesture kéo slider liên tục (nhiều chục lần gọi `updateXxx()`/giây) chỉ tạo ra 1 entry Undo duy nhất thay vì 1 entry/frame.
- `undo()`/`redo()` dùng LẠI `applyWaterMark()` (đã có sẵn từ FEAT-06) để restore snapshot — tái dùng đúng cơ chế ghi atomic 1 lần đã kiểm chứng, không viết lại logic ghi DataStore lần 2. `applyWaterMark()` cố tình KHÔNG gọi `snapshotForUndoIfDue()` (tránh vòng lặp tự đẩy chồng khi undo/redo đang chạy, và để "áp dụng profile" FEAT-06 không tự nhiên trở thành 1 bước Undo).
- UI: 2 menu item `actionUndo`/`actionRedo` trên toolbar editor (`app:showAsAction="ifRoom"`, `android:enabled="false"` mặc định trong XML), bật/tắt qua `MainActivity.refreshUndoRedoState()` (mirror pattern `refreshVipBadge()` đã có — đọc `toolbar.menu.findItem(...)?.isEnabled = ...` qua `toolbar.post{}`), gọi lại mỗi khi `viewModel.canUndo`/`canRedo` LiveData đổi giá trị. Toolbar vốn đã khá chật (đã quan sát `actionSave`/`actionVip` cũng overflow trên máy test 720px) nên Undo/Redo cũng có thể rơi vào overflow tuỳ độ rộng màn hình — chấp nhận được, vẫn đúng yêu cầu "trên toolbar" của AC, không phải bug.
- **Bug thật phát hiện qua smoke test (không phải unit test bắt được)**: `MyApplication.onCreate()` gọi `waterMarkRepo.resetModeToText()` mỗi lần app khởi động lại (reset mode nội bộ, không liên quan editor) — vì `resetModeToText()` ban đầu cũng được gán `snapshotForUndoIfDue()` như 1 hành động "có ý nghĩa", nút Undo hiện enabled ngay khi vừa mở app dù user chưa làm gì. Fix: gỡ `snapshotForUndoIfDue()` khỏi `resetModeToText()` — đây là hàm CHỈ được gọi từ `MyApplication.onCreate()` (xác nhận bằng `grep` toàn repo, không có caller nào khác), không phải hành động editor của user, không nên xuất hiện như 1 bước Undo được. Bổ sung `resetModeToText_doesNotPushUndoEntry` test khoá lại hành vi đúng, tránh tái phát.
- Test mọi tầng: `WaterMarkRepositoryUndoRedoRoboTest` (10 case — undo/redo cơ bản, no-op khi stack rỗng, redo bị xoá khi có edit mới sau undo, đi lùi nhiều bước đúng thứ tự, debounce gộp nhiều lần gọi liên tục thành 1 entry, 2 lần gọi cách nhau >400ms tạo 2 entry riêng, `applyWaterMark()`/`resetModeToText()` không tự đẩy entry Undo), `MainViewModelUndoRedoRoboTest` (3 case — verify dây nối ViewModel → Repository → LiveData, dùng poll `awaitTrue{}` thay vì `idle()` đơn lẻ vì `StateFlow.asLiveData()` cần thêm 1 vòng dispatch main-looper mới phản ánh kịp, đúng lớp vấn đề timing đã gặp ở `BatchHistoryViewModelRoboTest`).
- Smoke test thật trên device khoá `118743744X002560` (TECNO BG6), có kiểm chứng bằng `logcat` (`[MAIN] waterMark observer: ... text='...'`) đối chiếu từng bước, không chỉ nhìn UI: đổi text → xoá trắng (debounce coalesce) → gõ text mới "UNDO_TÉT_X" — 2 điểm Undo riêng biệt được tạo đúng (xác nhận qua log). Undo lần 1 → về chuỗi rỗng (`text=''`). Undo lần 2 → về ĐÚNG giá trị gốc "PROFILE_TÉT_A" (**AC1**). Redo lần 1 → về lại chuỗi rỗng, Redo lần 2 → về ĐÚNG "UNDO_TÉT_X" (**AC2**, thứ tự multi-step chính xác cả 2 chiều). Nút "Undo"/"Redo" trên overflow menu bật/tắt đúng theo từng bước (**AC3** gián tiếp — không quan sát trực tiếp được số entry debounce trên UI thật, đã verify riêng bằng unit test debounce ở trên). Không crash trong suốt luồng — `logcat` sạch `FATAL EXCEPTION`/`AndroidRuntime`.
- Vi phạm quy trình lúc smoke test: gõ text 2 lần liên tiếp (xoá rồi gõ lại) bị launcher thiết bị hijack sang app khác (`com.mckimquyen.lenslauncher`, cùng publisher, KHÔNG phải quảng cáo — không thuộc phạm vi R4) 2 lần liên tiếp giữa chừng — cùng lớp flaky "tap quá gần nhau bị launcher hiểu nhầm thành gesture chuyển app" đã ghi nhận nhiều lần trước đây trong phiên làm việc này; fix bằng cách `force-stop` cả 2 app rồi làm lại chậm hơn (sleep 2-3s giữa mỗi thao tác).
- `./gradlew testDebugUnitTest` toàn bộ PASS (138 file unit test, bao gồm 13 test mới cho FEAT-12). `./gradlew ktlintCheck` sạch (main + androidTest source set).
- Audit: 9/10 — đủ 3 AC verify bằng smoke test thật CÓ đối chiếu log từng bước (không chỉ nhìn UI), tự phát hiện + fix đúng root cause 1 bug thật (resetModeToText ô nhiễm Undo stack lúc khởi động app) mà chỉ smoke test mới lộ ra, thiết kế debounce đúng tinh thần AC3 (tách biệt "ghi ngay để preview mượt" khỏi "tạo điểm Undo", không làm chậm preview để đổi lấy stack gọn), tái dùng tối đa `applyWaterMark()` đã có; trừ điểm vì chưa verify trực tiếp trên UI thật số lượng entry debounce khi kéo slider thật (chỉ verify qua unit test, không phải giới hạn kỹ thuật mà do thời gian phiên làm việc) và vì Undo/Redo có thể rơi vào overflow menu trên màn hình hẹp (chấp nhận được nhưng chưa phải trải nghiệm 1-chạm lý tưởng).
