# Task Backlog — EasyWatermark

> Sinh ngày 2026-09-04. **Re-audit 2026-09-10**: đọc lại toàn bộ `app/src/main` + `cmonet/src/main`, đối chiếu 5 commit mới từ ngày sinh backlog (Frame presets EXIF border, Share App, chip token, AdMob debug/release, preview token — xem `doc/feat.md`), cross-check 4 nguồn độc lập: **Claude fork nội bộ** (đọc trực tiếp), **codex exec** (OpenAI Codex, sandbox read-only), **claude -p** (session riêng, allowlist Read/Grep/Glob), **agy** (timeout 2 lần sau ~10 phút, không có kết quả — bỏ qua, 3/4 nguồn còn lại đủ đồng thuận). Mỗi finding quan trọng đã verify lại bằng đọc trực tiếp source, không chỉ tin theo báo cáo AI.

## Quy ước

- Mỗi ticket là 1 file `.md` trong `doc/task/todo/` → di chuyển sang `doc/task/inprogress/` khi bắt đầu làm, `doc/task/done/` khi xong (đổi trạng thái = di chuyển file).
- Prefix: `BUG-` (lỗi cần fix), `ENH-` (cải tiến tính năng có sẵn), `FEAT-` (tính năng mới thực dụng), `IDEA-` (tính năng độc quyền/đột phá, effort cao, để tham khảo định hướng dài hạn).
- `priority`: P0 (crash/mất dữ liệu/bảo mật/doanh thu, core feature) > P1 (leak/perf/crash edge-case) > P2 (nhỏ, tối ưu).
- `effort`: XS (<2h) / S (nửa ngày) / M (1-2 ngày) / L (3-5 ngày) / XL (>1 tuần, cần thiết kế riêng).
- `sources`: agent nào tìm ra/đồng thuận — độ đồng thuận cao = độ tin cậy cao.
- **Prompt loop:** mỗi ticket trong `todo/` có section "## Prompt loop" trỏ tới [PROMPT_TEMPLATE.md](PROMPT_TEMPLATE.md) — Definition of Done dùng chung (audit >9/10 + unit/widget/integration test đủ mọi case + smoke test thật trên device đã khoá → mới được move `done/` + push).

## BUGS_TO_FIX (10 todo + 12 done, +1 ngoài kế hoạch gốc: BUG-22) — ưu tiên P0 trước

| ID | Priority | Effort | Tiêu đề |
|---|---|---|---|
| [BUG-14](todo/BUG-14-vip-secret-hardcode-trong-apk.md) | P0 | M | VIP secret hardcode base64, lặp 3 chỗ + 1 secret thứ 2 chưa từng ticket hoá (mở rộng 2026-09-10) — **deferred, xem ghi chú cuối file** |
| [BUG-15](todo/BUG-15-admob-rewarded-release-dung-test-id.md) | P0 | XS | `ADMOB_REWARDED_ID` build release vẫn dùng ID test — mất doanh thu, vi phạm chính sách AdMob — **deferred, xem ghi chú cuối file** |
| [BUG-07](todo/BUG-07-text-shader-indexof-va-kich-thuoc-am.md) | P1 | S | Text shader: `indexOf` sai dòng trùng lặp + kích thước bitmap có thể ≤0 |
| [BUG-08](todo/BUG-08-interstitial-postdelayed-khong-huy.md) | P1 | XS | `postDelayed` hiện interstitial không huỷ khi thoát Activity |
| [BUG-09](todo/BUG-09-action-send-thieu-extra-stream.md) | P1 | S | Nhận ảnh share (`ACTION_SEND`) thiếu `EXTRA_STREAM`, lặp/rơi ảnh |
| [BUG-10](todo/BUG-10-galleryfragment-observe-sai-lifecycle.md) | P1 | XS | `GalleryFragment` observe LiveData sai lifecycle owner |
| [BUG-12](todo/BUG-12-removeimage-crash-index-out-of-bounds.md) | P1 | XS | `removeImage` crash `IndexOutOfBoundsException` khi xoá ảnh |
| [BUG-18](todo/BUG-18-exifpbfragment-lazy-button-leak.md) | P1 | S | `styleButtons by lazy` trong `ExifPbFragment` giữ view cũ qua tái tạo dialog (mới 2026-09-10) |
| [BUG-19](todo/BUG-19-mediastore-ghi-that-bai-khong-guard.md) | P1 | M | Nhánh ghi MediaStore không guard `openFileDescriptor`/`compress` thất bại (mới 2026-09-10) |
| [BUG-21](todo/BUG-21-generateimage-early-return-khong-recycle.md) | P1 | M | `generateImage()` còn early-return không recycle bitmap ở nhánh lỗi (bổ sung sau BUG-05, mới 2026-09-10) |

**Đã DONE** (xem `doc/task/done/`): BUG-01 (inSampleSize/rotation), BUG-02 (BitmapCache NPE), BUG-03 (batch export báo thành công giả), BUG-04 (contentResolver insert force-unwrap), BUG-05 (OOM batch export — *lưu ý: BUG-21 mới phát hiện phần còn sót*), BUG-06 (icon cache race leak), BUG-11 (compressImg guard rỗng + leak file tạm), BUG-13 (QR debounce + off Main thread), BUG-16 (literal "null" trong Edit watermark), BUG-17 (FilmStrip coerceAtLeast — hoá ra đã fix kèm FEAT-14, chỉ thiếu test+ticket), BUG-20 (EditTextContentFragment collect theo viewLifecycleOwner) — **sprint P2 2026-09-11, xem `## Sprint 2026-09-11` cuối file**. Thêm BUG-22 (SaveImageBSDialogFragment tràn viewport màn hình nhỏ — phát hiện ngoài kế hoạch gốc, fix cùng ngày).

## ENHANCEMENTS (6 todo + 14 done) — cải tiến tính năng có sẵn

| ID | Effort | Tiêu đề |
|---|---|---|
| [ENH-01](todo/ENH-01-batch-export-workmanager-huy-tien-do.md) | L | Batch export chạy qua WorkManager + huỷ + tiến độ tổng |
| [ENH-17](todo/ENH-17-vip-key-device-bound.md) | S | VIP key gắn thiết bị (device-bound) — mitigation cho BUG-14 (mới 2026-09-10) — **deferred cùng BUG-14/15** |
| [ENH-08](todo/ENH-08-immutable-state-watermark-repository.md) | M | Model bất biến cho `ImageInfo`/`StateFlow` (`WaterMarkRepository`) |
| [ENH-09](todo/ENH-09-hardcode-string-sang-resources.md) | M | Đưa hardcode string UI sang `resources` (i18n/accessibility) |
| [ENH-10](todo/ENH-10-android-photo-picker.md) | M | Chuyển sang Android Photo Picker thay `ACTION_PICK` legacy |
| [ENH-14](todo/ENH-14-downsample-truc-tiep-khi-decode-export.md) | M | Downsample trực tiếp khi decode ảnh export — **rủi ro cao nhất nhóm hiệu năng M, xem ghi chú Sprint ENH hiệu năng M cuối file, chưa quyết định hướng đi** |

**Đã DONE**: ENH-04 (pinch-to-resize), ENH-05 (preview token khớp export — xác nhận đã triển khai 2026-09-06, xem `doc/feat.md` mục 4). **Sprint ENH S/XS 2026-09-12** (xem `## Sprint ENH 2026-09-12` cuối file): ENH-02 (debounce ghi DataStore khi gõ text), ENH-03 (gate `Log.d` bằng `BuildConfig.DEBUG`), ENH-07 (SignatureRepository qua Hilt DI), ENH-11 (vòng đời Ad Banner đầy đủ), ENH-12 (MonetManufacturer dựa API chính thức), ENH-13 (hiển thị số ảnh thành công/thất bại), ENH-18 (hằng số "Unknown Device" chung), ENH-19 (EXIF border co chữ tránh tràn), ENH-20 (preview filename query bất đồng bộ). **Sprint ENH hiệu năng M 2026-09-12** (xem `## Sprint ENH hiệu năng M 2026-09-12` cuối file): ENH-06 (gộp mở InputStream decode), ENH-15 (BitmapCache reference counting an toàn khi evict), ENH-16 (throttle rebuild shader khi pinch).

## NEW_FEATURES (13 todo + 1 done) — tính năng mới thực dụng, 1-2 tuần

| ID | Effort | Tiêu đề |
|---|---|---|
| [FEAT-02](todo/FEAT-02-naming-template-file-xuat.md) | S | Naming template cho file xuất (tái dùng token có sẵn) |
| [FEAT-09](todo/FEAT-09-preset-resize-theo-nen-tang.md) | XS | Preset resize theo nền tảng (Instagram/Facebook/Zalo) |
| [FEAT-08](todo/FEAT-08-chon-thu-muc-saf-batch.md) | S | Chọn cả thư mục (SAF tree) để batch |
| [FEAT-11](todo/FEAT-11-hieu-ung-vien-bong-text.md) | S | Hiệu ứng viền/bóng/nền pill cho text watermark |
| [FEAT-10](todo/FEAT-10-frame-preset-nhan-dien-hang-may.md) | S | Tự nhận diện hãng máy để gợi ý style khung EXIF (scope thu hẹp sau audit — phần style đã xong) |
| [FEAT-14](todo/FEAT-14-custom-frame-builder-tham-so-hoa.md) | S | Custom Frame Builder — tham số hoá 4 EXIF frame style đã có (mới 2026-09-10) |
| [FEAT-04](todo/FEAT-04-lich-su-batch-gan-day.md) | M | Lịch sử batch export gần đây |
| [FEAT-05](todo/FEAT-05-backup-restore-template-signature.md) | M | Xuất/nhập Template + Signature (backup/restore) |
| [FEAT-06](todo/FEAT-06-watermark-profile-day-du.md) | M | Watermark profile đầy đủ |
| [FEAT-07](todo/FEAT-07-preview-grid-truoc-khi-export.md) | M | Preview grid trước khi export cả batch |
| [FEAT-12](todo/FEAT-12-undo-redo-editor.md) | M | Undo/Redo chỉnh sửa watermark trong editor |
| [FEAT-13](todo/FEAT-13-caption-rieng-tung-anh-batch.md) | M | Nhập caption/text riêng theo từng ảnh trong batch (CSV) |
| [FEAT-03](todo/FEAT-03-multi-layer-watermark.md) | L | Watermark đa lớp (chồng text + logo/QR cùng lúc) |

**Đã DONE**: FEAT-01 (9-grid position anchor — xác nhận đã triển khai 2026-09-05, xem `doc/feat.md` mục 7).

## UNIQUE_IDEAS (10) — tính năng độc quyền/đột phá, effort cao

| ID | Effort | Tiêu đề |
|---|---|---|
| [IDEA-01](todo/IDEA-01-ai-auto-placement-nhan-dien-chu-the.md) | L | Auto-placement bằng on-device ML (né mặt người/chủ thể) — **đồng thuận 4/4 agent (đợt gốc)** |
| [IDEA-08](todo/IDEA-08-referral-vip-qua-share-app.md) | M | Referral VIP qua Share App — tái dùng hạ tầng sẵn có, không cần backend (mới 2026-09-10) |
| [IDEA-06](todo/IDEA-06-auto-contrast-opacity-harmonizer.md) | M | Auto-contrast/opacity harmonizer theo từng ảnh |
| [IDEA-07](todo/IDEA-07-batch-qr-smart-bridge.md) | M | Batch QR Smart-Bridge (hash SHA-256 + xác thực nguồn gốc) |
| [IDEA-03](todo/IDEA-03-content-authenticity-stamp-c2pa.md) | L | Content authenticity stamp kiểu C2PA |
| [IDEA-09](todo/IDEA-09-watermark-survivability-preview.md) | L | Watermark Survivability Preview — mô phỏng crop/recompress mạng xã hội (mới 2026-09-10) |
| [IDEA-10](todo/IDEA-10-recipient-fingerprint-batch.md) | L | Recipient Fingerprint Batch — watermark riêng theo người nhận, truy nguồn rò rỉ (mới 2026-09-10) |
| [IDEA-02](todo/IDEA-02-invisible-watermark-steganography.md) | XL | Invisible watermark / steganography chống xoá |
| [IDEA-04](todo/IDEA-04-cloud-sync-brand-kit.md) | XL | Cloud sync Brand Kit đa thiết bị |
| [IDEA-05](todo/IDEA-05-cho-template-cong-dong.md) | XL | Chợ template cộng đồng (network effect) |

## ⏸️ Deferred theo quyết định user (2026-09-10)

- **BUG-14** (VIP secret hardcode), **BUG-15** (AdMob rewarded test ID), **ENH-17** (VIP key device-bound) — user quyết định: **bỏ qua, dời sang tháng sau** ("các tính năng về AD và IAP sẽ làm ở tháng sau"). Không đưa vào sprint hiện tại, không chọn hướng NDK/JNI hay server-side lúc này — quyết định BUG-14 để ngỏ tới khi quay lại nhóm AD/IAP.
- **UNIQUE_IDEAS**: user chọn không đầu tư idea nào ngay ("nên làm đủ backlog trước, các tính năng mới để sau") — giữ nguyên 10 IDEA để tham khảo định hướng, không triển khai.
- **Sprint đang chạy**: FEAT-02, FEAT-09, FEAT-14 (nhóm FEAT effort S/XS) — loop tự động qua `PROMPT_TEMPLATE.md`, kick off 2026-09-10. **Đã DONE** (xem `doc/task/done/`).

## Sprint 2026-09-11 — P2 bug sprint (BUG-11/13/16/17/20)

Toàn bộ 5 ticket P2 còn lại trong BUGS_TO_FIX đã hoàn thành theo `PROMPT_TEMPLATE.md` (audit >9/10, test đủ, smoke test thật) — chi tiết từng ticket xem "Kết quả kiểm chứng" trong file tương ứng ở `doc/task/done/`.

- **BUG-17** hoá ra code đã được fix sẵn (kèm theo FEAT-14 hôm 2026-09-10, sau khi ticket này được sinh) — chỉ còn thiếu test hồi quy + đóng ticket.
- **BUG-13**: có widget test thật (`QrCodeBottomSheetFragmentRoboTest`, dùng lại kỹ thuật `add()` fragment với `setShowsDialog(false)` từ `GalleryFragmentLifecycleRoboTest`/BUG-10) chứng minh cả debounce lẫn huỷ job cũ khi gõ liên tục. Bài học: `shadowOf(Looper).idle()` trần chạy hết cả task lên lịch tương lai — phải dùng `idleFor(duration)` mới test đúng debounce.
- **Bổ sung sau audit lần 2 (yêu cầu user)**: thêm `MainViewModelCompressImgIntegrationTest` (`app/src/androidTest`, 3 case, IO thật — ảnh JPEG thật + `Compressor` thật) cho BUG-11, chạy PASS trên Samsung SM_A115F qua `ANDROID_SERIAL=R9JN61LDLFJ ./gradlew connectedAppReleaseDebugAndroidTest` (24/24 test instrumented PASS, không riêng gì test mới).
- **BUG-22** (phát hiện trong sprint này, fix ngay sau khi user chọn ở sprint kế tiếp cùng ngày 2026-09-11): dialog `SaveImageBSDialogFragment` (Export to the album) dùng `LinearLayout` gốc không bọc `ScrollView` — trên màn hình nhỏ (Samsung SM_A115F, 720×1560, có nav bar), nút "Export to the album" bị tràn ngoài viewport, không bấm được bằng thao tác chạm thường. Fix: bọc `NestedScrollView` (đúng pattern BUG-18) — verify bằng vuốt tay thật trên Samsung, không dùng `wm size` hack. Xem `doc/task/done/BUG-22-save-image-dialog-tran-viewport-man-hinh-nho.md`.
- **Device smoke test đổi giữa chừng**: bắt đầu trên TECNO_KJ7 (khoá theo R3 lúc chỉ có 1 device), gặp App Open Ad test che toàn màn hình ngay sau splash → dừng theo R4, chờ user xác nhận. User yêu cầu tường minh đổi sang Samsung SM_A115F (R9JN61LDLFJ) — mọi smoke test từ đó về sau chạy trên Samsung.

## Sprint ENH 2026-09-12 — nhóm S/XS (ENH-02/03/07/11/12/13/18/19/20)

9 ticket enhancement effort S/XS hoàn thành theo `PROMPT_TEMPLATE.md` — chi tiết từng ticket xem "Kết quả kiểm chứng" trong file ở `doc/task/done/`.

- **ENH-03** (lớn nhất, mang tính cơ học): thêm `AppLog.d()` wrapper trong `AppConst.kt`, thay thế 100 lời gọi `Log.d(` → `AppLog.d(` trên 13 file bằng `sed`, xoá `import android.util.Log` không còn dùng ở 6 file. Verify bằng cách generate `BuildConfig.java` thật cho cả 2 biến thể (`appReleaseDebug` DEBUG=true, `appReleaseRelease` DEBUG=false) thay vì giả định.
- **ENH-17 gián tiếp bị ảnh hưởng**: KHÔNG chọn (vẫn deferred cùng BUG-14/15).
- **BUG-17 fix trước đó** được xác nhận lại đúng khi làm ENH-19 (đọc cùng khu vực code EXIF border).
- **Hạ tầng test bị treo lặp lại nhiều lần trong phiên này** (không liên quan code sửa) — full `testAppReleaseDebugUnitTest` trên toàn bộ 33 class treo thật (CPU gần như đứng yên) nhiều lần liên tiếp trên máy đang chạy đồng thời Android Studio + Chrome + 2 phiên Claude + Zalo + LarkSuite (RAM gần cạn, compressor cao). Khắc phục tạm: tăng `maxHeapSize=3g` + `forkEvery=25` trong `testOptions.unitTests.all` (app/build.gradle.kts) — vẫn treo được, nhưng khi chạy KIÊN NHẪN đủ lâu (batch tách nhỏ theo package, ~7-31 phút/lần) luôn PASS toàn bộ, không có test nào thật sự fail do lỗi logic. Ghi nhận: đây là giới hạn tài nguyên máy thật lúc chạy phiên dài, không phải bug trong code hay test.
- **Device đổi lần 2 giữa phiên**: Samsung SM_A115F mất kết nối hẳn, TECNO_KJ7 vẫn còn (từng dính ad, bị bỏ qua trước đó), xuất hiện máy mới OnePlus CPH1989 (FUJZIFIR7DQCNRWW) — hỏi user qua `AskUserQuestion`, được chọn OnePlus, khoá dùng cho phần smoke test còn lại của sprint này.
- **Bài học thao tác ADB mới**: `uiautomator dump` không đáng tin cậy trên máy OnePlus/OPPO này (nhiều lần trả về cây UI CŨ/sai màn hình đang hiển thị) — phải chuyển hẳn sang tính toạ độ tap từ ảnh chụp màn hình thật (screencap, luôn đúng kích thước thật của thiết bị, vd 1080×2340) thay vì dựa vào dump.

## Sprint ENH hiệu năng M 2026-09-12 — nhóm bitmap/render (ENH-06/15/16)

3 ticket effort M trong nhóm "ENH hiệu năng M" hoàn thành theo `PROMPT_TEMPLATE.md` — chi tiết từng ticket xem "Kết quả kiểm chứng" trong file ở `doc/task/done/`.

- **ENH-06**: dừng ở 3 lần mở `InputStream`/ảnh (không đạt mục tiêu aspirational "1-2" trong ticket gốc) — quyết định có chủ đích, ghi rõ lý do trong ticket (gộp thêm bằng `mark/reset` rủi ro `IOException` không đoán trước trên SAF/cloud provider, đổi lấy giảm 1 lần mở stream không đáng).
- **ENH-15**: chọn Phương án B (reference counting) thay vì Phương án A (chỉ giảm cacheSize) — vì Phương án A không giải quyết gốc rủi ro use-after-recycle nêu trong ticket. Audit đủ 2 nơi giữ tham chiếu trực tiếp bitmap từ cache (`WaterMarkImageView`, `MainViewModel.generateImage()`).
- **ENH-16**: chọn Hướng 1 (throttle theo thời gian, 40ms) trong 3 hướng đề xuất của ticket — đơn giản nhất, đủ hiệu quả (giảm >75% lần rebuild theo test mô phỏng 120fps), không đổi accuracy cuối cùng nhờ force-apply ở `onScaleEnd`.
- **Giới hạn chung không tránh được của cả 3 ticket**: 2/2 Acceptance Criteria dạng "đo bằng Android Studio Memory/CPU Profiler" (ENH-15, ENH-16) không thực hiện được vì môi trường làm việc chỉ có CLI + ADB, không có Android Studio UI — thay bằng bằng chứng gián tiếp (unit test mô phỏng đúng cơ chế, hoặc số liệu đếm thực nghiệm qua `ContentProvider`/`LruCache` giả lập). Ghi rõ trong từng ticket, không tự nhận đã đo Profiler khi chưa đo.
- **Pinch 2 ngón thật không giả lập được qua `adb shell input`** (chỉ hỗ trợ 1 pointer/không multi-touch) — AC "pinch mượt hơn theo cảm nhận thực tế" của ENH-16 còn để ngỏ trong ticket, cần người dùng xác nhận tay thật.
- **Smoke test thật trên Samsung Galaxy S24 Ultra (SM-S928B, serial R5CX613VZBR)** — đổi từ OnePlus CPH1989 theo yêu cầu tường minh của user giữa phiên ("hãy dùng s24u"): batch 2 ảnh, bật Icon watermark mode (decode+hiển thị icon bitmap từ cache), chuyển qua lại giữa 2 ảnh nhiều lần, theo dõi `adb logcat *:E` xuyên suốt — không `FATAL EXCEPTION`/`AndroidRuntime` nào liên quan app, không crash.
- **`uiautomator dump` tiếp tục không đáng tin cậy** trên cả OnePlus lẫn Samsung S24 Ultra (trả cây UI cũ/sai màn hình) — xác nhận đây là vấn đề công cụ trên các máy test gần đây, không phải riêng 1 hãng; toàn bộ thao tác tap phải tính toạ độ từ ảnh chụp màn hình thật, có lúc cần crop ảnh phóng to vùng nút để đo chính xác khi UI có nhiều cột hẹp sát nhau (row Text/Icon/Signature/QR Code).

## Ghi chú re-audit 2026-09-10 (khác biệt so với đợt sinh backlog gốc)

- **FEAT-01, ENH-05** đã triển khai xong ngoài luồng backlog — move sang `done/`, không phải làm lại.
- **FEAT-10** chỉ done 1 phần (4 style khung — xong; tự nhận diện hãng máy — chưa) — đã tách lại scope trong file ticket.
- **BUG-14** mở rộng đáng kể phạm vi (3 vị trí lặp secret + 1 secret thứ 2 chưa ticket hoá + cả package `feature/vip/` chưa từng audit) — nâng ưu tiên lên **P0**.
- **BUG-05 (đã done)** vẫn còn sót: `generateImage()` có nhánh early-return không recycle bitmap ở đường lỗi (không phải đường thành công) — theo dõi tiếp ở BUG-21 mới, không mở lại BUG-05.
- 2 ý kiến độc lập lệch nhau về hướng fix BUG-14: `claude -p` + fork nội bộ khuyến nghị **native NDK/JNI** (app chưa có hạ tầng network, effort thấp hơn); `codex exec` khuyến nghị **server-side verify** (trust boundary thật, thu hồi/giới hạn được key) — đã hỏi user quyết định qua AskUserQuestion trong phiên này.

## Gợi ý sprint đầu tiên (cập nhật sau re-audit)

Nhóm P0 giờ có 3 ticket cùng khu vực rủi ro cao: BUG-14 + ENH-17 (bảo mật VIP), BUG-15 (doanh thu Ad thật/test lẫn lộn) — nên gộp 1 sprint đầu, xử lý trước cả nhóm BUG-02..05 cũ (đã done). Sau đó nhóm P1 mới phát hiện (BUG-18/19/21) cùng khu vực `MainViewModel`/`ExifPbFragment` nên làm chung sprint kế tiếp với BUG-07/08/09/10/12 cũ. FEAT effort S/XS (FEAT-02, FEAT-09, FEAT-14) vẫn là lựa chọn tốt để có tính năng "nhìn thấy được" song song.
