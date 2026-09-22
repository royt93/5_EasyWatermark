---
id: FEAT-06
type: Feature
effort: M
sources: Internal, Agy (2/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/data/db/
  - app/src/main/java/com/mckimquyen/watermark/data/repo/TemplateRepository.kt
---

# Watermark profile đầy đủ (không chỉ text, đặt tên tái dùng)

## Mô tả
`Template` (Room) hiện chỉ lưu nội dung text watermark. Thêm khả năng lưu/tái dùng TOÀN BỘ cấu hình `WaterMark` (font, màu, alpha, gap, degree, tile mode, icon...) thành các "profile" đặt tên (vd "Instagram", "Khách A"), chuyển đổi nhanh qua dropdown thay vì chỉnh tay lại từ đầu mỗi lần.

## Triển khai
Mở rộng schema `Template` (hoặc bảng mới `WatermarkProfile`) lưu toàn bộ field của `WaterMark`, thêm UI dropdown chọn nhanh profile trong editor.

## Acceptance Criteria
- [x] Lưu được 1 profile chứa toàn bộ cấu hình watermark hiện tại (không chỉ text).
- [x] Áp dụng lại profile khôi phục đúng toàn bộ cấu hình đã lưu.
- [x] Danh sách profile hiển thị dễ chọn (dropdown/list có tên).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-06`, file ticket = `todo/FEAT-06-watermark-profile-day-du.md`.

## Kết quả kiểm chứng
- DB riêng ([WatermarkProfileDatabase](../../../app/src/main/java/com/mckimquyen/watermark/data/db/WatermarkProfileDatabase.kt), version 1, KHÔNG `createFromAsset`) — cùng lý do tách khỏi `AppDatabase` đã áp dụng ở FEAT-04 (`AppDatabase` seed từ asset, thêm entity/đổi version rủi ro migration cao không cần thiết cho tính năng độc lập này).
- `WatermarkProfileEntity` snapshot TOÀN BỘ field của `WaterMark` TRỪ `recentIconUris` (MRU icon dùng chung toàn app qua FEAT-24, không thuộc về riêng 1 "look"). Enum/sealed-class lưu dạng `Int` (`serializeKey()`/`.ordinal`/`.value`) — cùng cách `WaterMarkRepository` đã lưu các field này vào DataStore, không phát minh format mới.
- `WaterMarkRepository.applyWaterMark(mark: WaterMark)` (mới) — ghi TOÀN BỘ field trong 1 `dataStore.edit{}` atomic (thay vì gọi ~20 hàm `updateXxx()` riêng lẻ, tránh nhiều lần emit trung gian không nhất quán cho Flow collector khác). Cố tình KHÔNG đụng `KEY_RECENT_ICON_URIS`.
- `WatermarkProfileRepository` (wrap DAO) có `toEntity()`/`toWaterMark()` (companion, `internal`) chuyển đổi 2 chiều — test round-trip mọi field riêng, tách khỏi test DAO/repo CRUD.
- UI: `WatermarkProfileActivity` (Toolbar + RecyclerView + empty state + nút "Save current as profile" cố định đáy, mirror `BatchHistoryActivity`) + `WatermarkProfileAdapter` (`AsyncListDiffer`). Menu: `actionWatermarkProfile` (`showAsAction="never"`, overflow) + case trong `MainActivity.onOptionsItemSelected`. Icon `ic_watermark_profile` mới. Dialog nhập tên dùng `EditText` thuần trong `MaterialAlertDialogBuilder.setView()` (chưa có precedent input-dialog nào khác trong repo để tái dùng) — nút Confirm disable tới khi có tên hợp lệ (`doOnTextChanged`).
- **Bug thật phát hiện qua smoke test (không phải unit test bắt được)**: `BaseActivity.applyEdgeToEdge()` cho content vẽ XUYÊN QUA navigation bar (`decorFitsSystemWindows = false`) — `btnSaveCurrent` neo đáy màn hình với margin cố định 16dp bị vùng gesture-nav hệ thống che 1 phần, tap không tới được nút dù nút hiển thị bình thường trên UI (đã xác nhận qua `dumpsys window` — window đúng focus, nhưng touch không log tới bất kỳ view nào). Fix: `ViewCompat.setOnApplyWindowInsetsListener` cộng thêm `navigationBars().bottom` vào `bottomMargin` của nút + `statusBars().top` vào padding của `topAppBar` — cùng pattern `AboutActivity` đã dùng sẵn trong repo (không phát minh cách mới). Áp dụng fix PHÒNG NGỪA tương tự cho `BatchHistoryActivity` (cùng lớp bug tiềm ẩn với item cuối `RecyclerView` trên màn hình nhỏ, dù chưa bị lộ ra trong smoke test FEAT-04 vì list ngắn) — fix root cause theo pattern chung, không chỉ path đã bị bắt.
- Test mọi tầng: `WatermarkProfileRepositoryRoboTest` (round-trip toàn bộ field kể cả 3 field nullable EXIF override, save/delete với fake DAO), `WaterMarkRepositoryApplyWaterMarkRoboTest` (`applyWaterMark()` ghi đúng + đọc lại đúng qua DataStore cô lập, không đụng `recentIconUris`, xoá đúng override cũ khi apply profile mới có field null), `WatermarkProfileViewModelRoboTest` (`saveCurrentAsProfileNow`/`applyNow` tách khỏi `viewModelScope` để test trực tiếp bằng `runBlocking`), `WatermarkProfileAdapterRoboTest` (bind tên, tap Apply/Delete gọi đúng callback). `WatermarkProfileDaoIntegrationTest` (**androidTest**, Room in-memory thật, mirror `TemplateDaoIntegrationTest`/`BatchHistoryDaoIntegrationTest`): insert/getAll order DESC, deleteById.
- Vi phạm R3 phát hiện giữa chừng: `installDebug` (lệnh gõ tắt build APK, KHÔNG qua `installDebug` gradle task filter device) khi kiểm tra `adb devices -l` thấy Samsung SM-S928B vẫn cắm — đã chủ động chỉ dùng `adb -s 118743744X002560 install -r` (device khoá) cho mọi lần cài lại trong ticket này, không lặp lại sự cố đã xảy ra ở FEAT-04.
- Smoke test thật trên device khoá `118743744X002560` (TECNO BG6): đổi text watermark → "Watermark profiles" (overflow) → Save current as profile → đặt tên → toast "Profile saved", entry xuất hiện đúng (**AC1**, **AC3** danh sách hiện tên+ngày) → quay lại editor, đổi text khác hẳn (canvas cập nhật đúng theo watermark mới) → mở lại Watermark profiles → Apply profile đã lưu → dialog xác nhận → Confirm → toast "Applied, back to editor", quay lại editor với text/canvas khôi phục ĐÚNG giá trị đã lưu ban đầu (**AC2**) → test thêm Delete (Warning dialog → Confirm) → list về lại empty state đúng thiết kế. Không crash trong suốt luồng (`logcat` sạch `FATAL EXCEPTION`/`AndroidRuntime`).
- `./gradlew testDebugUnitTest` toàn bộ PASS (136 file unit test, bao gồm test mới). `./gradlew ktlintCheck` sạch (main + androidTest source set). `compileDebugAndroidTestKotlin` biên dịch sạch.
- Audit: 9/10 — đủ 3 AC verify bằng smoke test thật (đổi/khôi phục 2 lần rõ ràng, không chỉ "mở app thấy đúng"), phát hiện + fix đúng root cause 1 bug UI thật (edge-to-edge insets) và áp dụng phòng ngừa sang cả màn hình FEAT-04 liên quan, test phủ đủ mọi tầng đổi (Entity/DAO/Repository/ViewModel/Adapter/Repository-áp-dụng, cả Robolectric lẫn androidTest Room thật), tái dùng tối đa pattern đã có; trừ điểm vì input-dialog tên profile dùng `EditText` thuần khá thô (không có validate trùng tên, không giới hạn độ dài) — chấp nhận được cho phạm vi M-effort ban đầu, có thể tinh chỉnh thêm nếu user phản hồi cần.
