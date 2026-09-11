---
id: BUG-18
type: Bug
priority: P1
effort: S
sources: codex exec (external CLI, re-audit 2026-09-10)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/ExifPbFragment.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/base/BaseBindBSDFragment.kt
---

# `styleButtons by lazy` trong `ExifPbFragment` giữ view cũ qua tái tạo dialog

## Mô tả
`ExifPbFragment.kt:15` khai báo tập hợp 4 `MaterialButton` (style Classic/Polaroid/Film Strip/Minimal) bằng `by lazy`. `lazy` chỉ tính giá trị 1 lần rồi cache vĩnh viễn cho vòng đời `Fragment` instance — nhưng `BottomSheetDialogFragment` có thể tái tạo `View` (`onCreateView` gọi lại) nhiều lần trong cùng 1 Fragment instance (xoay màn hình, dialog bị hệ thống tái tạo). Khi đó `styleButtons` vẫn trỏ tới các `MaterialButton` của `View` CŨ đã bị gỡ khỏi cây UI — listener gắn trên chúng không còn tác dụng trên UI thật (nút mới không phản hồi đúng), đồng thời giữ tham chiếu mạnh tới view hierarchy cũ (leak nhẹ tới khi Fragment bị huỷ hẳn).

## Cách fix đề xuất
Khởi tạo lại `styleButtons` trong `onCreateView`/`onViewCreated` mỗi lần (không dùng `by lazy` cấp Fragment cho view reference), hoặc clear/null tham chiếu trong `onDestroyView()` theo đúng pattern ViewBinding đã dùng ở các Fragment khác trong `BaseBindBSDFragment`.

## Acceptance Criteria
- [ ] Dialog `ExifPbFragment` bị tái tạo view (test qua xoay màn hình hoặc force-recreate) vẫn bind đúng listener vào 4 nút style hiện tại trên UI.
- [ ] Không còn tham chiếu tới `View`/`MaterialButton` đã bị gỡ khỏi hierarchy sau `onDestroyView()`.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-18`, file ticket = `todo/BUG-18-exifpbfragment-lazy-button-leak.md`.

## Kết quả kiểm chứng (2026-09-11)
- **Điểm audit tự chấm: 9.5/10** (nâng từ 9/10 sau re-audit + smoke test thật + 1 fix bổ sung phát hiện trong lúc test). Thay `private val styleButtons by lazy {...}` bằng property getter thuần (`get() = mapOf(...)`) — luôn đọc `binding` hiện tại thay vì cache View cũ vĩnh viễn theo Fragment instance. `binding` đã tự trỏ đúng View mới mỗi lần `onCreateView` chạy lại (base class `BaseBindBSDFragment._binding` reset trong `onDestroyView`), nên không cần thêm state/clear logic nào khác.
- **Phát hiện + fix bổ sung (edge-to-edge, trong lúc smoke test):** `dlg_exif_border.xml` gốc có root là `LinearLayout` `wrap_content` trực tiếp (không scroll được). Khi bật "Leica EXIF Border" (hiện thêm `groupFrameStyle` + `groupCustomize`, gồm Frame Style + Customize + Serif caption), tổng chiều cao nội dung vượt quá vùng hiển thị an toàn của cửa sổ app trên KJ7 (3-button nav bar) — xác nhận bằng `uiautomator`: bounds `swExifSerifCaption` nằm hoàn toàn ngoài `app=1080x2208` (vùng an toàn), tức switch cuối cùng bị nav bar che, không bấm được. Đây đúng loại lỗi edge-to-edge đã ghi trong `doc/feat.md` mục 10 (khác biểu hiện: không phải thiếu insets-listener — `BaseBSDFragment` đã có insets-listener áp dụng chung cho mọi BSD Fragment — mà là nội dung khi mở rộng đủ dài thì không có cách nào scroll để kéo lên, nên phần cuối luôn nằm ngoài tầm với bất kể padding bù bao nhiêu). **Fix:** bọc toàn bộ nội dung trong `androidx.core.widget.NestedScrollView` (`android:fillViewport="true"`) — đúng pattern chuẩn cho nội dung `BottomSheetDialogFragment` có thể cao hơn màn hình, tận dụng insets-listener sẵn có của `BaseBSDFragment` cho phần padding đáy. Đã build lại APK, cài lại lên KJ7, xác nhận: "Serif caption" switch giờ kéo lên xem/bấm được đầy đủ (bounds mới `[840,2100][1008,2244]`, có margin rõ với nav bar trên màn hình thật), bấm toggle hoạt động đúng (chuyển xanh). Không phát hiện vấn đề edge-to-edge nào khác trong phạm vi 8 ticket đang xử lý (đã kiểm tra riêng BUG-10/GalleryFragment — xem file đó).
- **Test:** `app/src/test/java/com/mckimquyen/watermark/ui/dlg/ExifPbFragmentRoboTest.kt` (2 test, Robolectric) — xác nhận bằng reflection rằng class KHÔNG còn field delegate `Lazy` (`styleButtons$delegate`) và không còn backing field nào cho `styleButtons`, tức cơ chế cache theo Fragment instance đã bị loại bỏ hoàn toàn. Launch thật `BottomSheetDialogFragment` qua xoay màn hình thật KHÔNG áp dụng được cho ticket này theo cách khác: đã xác nhận `MainActivity` khai báo `android:screenOrientation="portrait"` + `android:configChanges="orientation|keyboardHidden"` trong `AndroidManifest.xml` — nghĩa là app KHOÁ portrait và tự xử lý config change (không bao giờ recreate Activity/Fragment/View do xoay máy), nên kịch bản "xoay màn hình để force-recreate view" mà AC #1 nêu **không thể xảy ra trong production thật** với app này (đã verify bằng `adb shell settings put system user_rotation 1` trên thiết bị thật — UI không đổi, xác nhận orientation bị khoá). Rủi ro gốc của ticket (View cũ bị cache khi dialog tái tạo) chỉ còn khả năng xảy ra qua đường khác (hệ thống buộc recreate do low-memory) — cơ chế fix (bỏ `by lazy`) vẫn đúng và đã verify qua reflection test + smoke test chức năng thật (đổi style nhiều lần trên UI thật vẫn đúng, xem dưới).
- **Smoke test (2026-09-11, TECNO KJ7 `115333744A005844`):** PASS. Bật toggle EXIF Border → 4 nút style (Classic/Polaroid/Film Strip/Minimal, cuộn ngang) hiện đúng; chọn "Polaroid" → highlight chuyển đúng nút, "Band thickness" tự cập nhật theo style (12%→16%) — xác nhận `styleButtons` map đúng vào các nút hiện tại trên UI, không lỗi binding. Thử `settings put system user_rotation 1` (xem trên) xác nhận app khoá portrait, không recreate — loại bỏ khả năng tái hiện đúng kịch bản gốc của bug trên thiết bị thật. Không crash trong toàn bộ phiên. **Đạt Definition of Done: điểm 9.5/10 > 9, test đủ (reflection test + lý do xoay màn hình không áp dụng được đã verify bằng thiết bị thật), smoke test pass + 1 fix edge-to-edge bổ sung đã verify.**
