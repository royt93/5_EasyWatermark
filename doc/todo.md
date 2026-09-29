# Danh sách việc cần làm & Cải tiến

> Cập nhật: 2026-09-29. Xem thêm `doc/feat.md` cho danh sách tính năng (FEAT-XX) — file này tập trung bugfix/cải tiến/hạ tầng. Từ ngày sinh backlog 2026-09-04, hàng đợi ticket kỹ thuật chi tiết (BUG-XX/ENH-XX/FEAT-XX/IDEA-XX) đã chuyển sang `doc/task/BACKLOG.md` + `doc/task/done/` — file này giữ vai trò tóm tắt/lịch sử, không lặp lại nội dung đầy đủ từng ticket.

## Review pass 3 — `:cmonet` + memory leak/hiệu năng + M3 compliance, 2026-09-29

Audit mở rộng theo yêu cầu user (loop tiếp theo sau đợt release-readiness ở trên): `/code-review
--level max` trên `cmonet/src/main` + `app/src/main`, 3 phạm vi (1) `:cmonet` module — clean, không
tìm thấy issue nào ngoài BUG-41 đã fix trước đó; (2) memory leak/hiệu năng dài hạn trong custom
view/fragment; (3) UI M3 compliance còn sót — clean, không còn widget legacy/hardcode màu theme.
7 finding (2) verify tay từng cái bằng đọc source trước khi fix — **tất cả ĐÚNG**, tất cả đều fix:

- [x] **`ColoredImageVIew.onDraw()` cấp phát bitmap MỖI FRAME** — animation shimmer `colorAnimator`
  (`repeatCount=INFINITE`) gọi `postInvalidateDelayed` liên tục nên `onDraw()` chạy liên tục, mỗi
  lần tạo `innerBitmap` mới không check null/size, không recycle bản cũ → GC churn nặng/rủi ro OOM.
  Fix: chỉ tạo lại khi `innerBitmap == null || sizeHasChanged`, recycle bản cũ trước khi thay.
- [x] **`ColoredImageVIew.onDetachedFromWindow()` dùng `pause()` thay vì `cancel()`** — animator
  INFINITE vẫn "started" khi pause, sống mãi trong `AnimationHandler` giữ tham chiếu View/Context
  nếu view không bao giờ re-attach. Fix: đổi sang `cancel()` (an toàn — `onAttachedToWindow()` tự
  `start()` lại từ đầu khi re-attach). Test mới: `ColoredImageVIewWidgetTest` (3 test: reuse bitmap
  cùng size, recycle bitmap cũ khi resize, animator hết "started" sau detach).
- [x] **`CircleImageView` `sourceImageBitmap` không recycle khi resize** — bất đối xứng với
  `destCircleBitmap` ngay bên cạnh (`onSizeChanged`) vốn đã recycle đúng. Fix: recycle bản cũ trước
  khi gán bitmap mới trong `onDraw()`, giống hệt pattern đã có. Test mới:
  `CircleImageViewSizeChangedWidgetTest` (2 test: recycle khi resize, reuse khi cùng size).
- [x] **`QrCodeBottomSheetFragment` bitmap QR preview không recycle** — cả khi refresh (mỗi
  keystroke debounce 250ms) lẫn khi đóng sheet (không có `onDestroyView`) — đúng lớp bug BUG-38 đã
  fix nơi khác nhưng bỏ sót ở đây. Fix: recycle bản cũ trước khi gán bitmap mới, thêm
  `onDestroyView()` recycle + clear ImageView. Test mới: 3 test trong
  `QrCodeBottomSheetFragmentRoboTest` (recycle khi đổi nội dung, khi xoá nội dung, khi đóng sheet).
- [x] **`Context.colorBackground` fallback luôn trả màu DARK** bất kể theme sáng/tối thật — khác
  mọi property màu khác trong cùng file (`colorPrimary`/`colorSurface`/`colorTertiary` đều branch
  theo `isNight()`/`supportNight()`). Property hiện chưa có code nào gọi (dead code) nên chưa gây
  bug thật, nhưng bẫy sẵn cho tương lai — cùng bài học `scaleY` ở review pass 2. Fix: thêm branch
  `isNight()` đúng pattern. Test mới: `ContextExtensionColorBackgroundRoboTest` (light/dark theme,
  ép `CMonet.setUserEnabled(false)` để chắc chắn rơi vào nhánh fallback đang test).
- [x] **`RadioButton.isChecked` setter gọi listener TRƯỚC khi gán field** — callback đọc lại
  `isChecked` (thay vì dùng param) sẽ thấy giá trị CŨ. Fix: gán field trước, đúng ngữ nghĩa property
  setter chuẩn. Test mới: `RadioButtonRoboTest.setChecked_listenerReadsBackProperty_seesNewValueNotStale`.
- [x] **`BlinkCursorView` dùng nhầm `ObjectAnimator.INFINITE/REVERSE`** cho `AlphaAnimation` (giá
  trị số trùng nên chạy đúng, chỉ sai class tham chiếu) — fix đổi sang `Animation.INFINITE/REVERSE`
  đúng class, cosmetic, không có test riêng (không đổi hành vi runtime).

**Verify:** `./gradlew testDebugUnitTest` full suite PASS (1037 test — 3 flaky pre-existing không
liên quan diff này, đã xác nhận pass riêng lẻ). `ktlintCheck` PASS, `lintDebug` sạch (222
`LintBaselineFixed` thông tin + 7 warning baseline, không đổi).

**Smoke test thật trên TECNO_KJ7, 2026-09-29:** cài lại APK — mở About (CircleImageView avatar
render đúng, không crash) → chọn ảnh vào editor → mở Mã QR (`QrCodeBottomSheetFragment`), gõ nội
dung 2 lần liên tiếp (refresh + recycle bitmap cũ), đóng sheet (`onDestroyView` recycle) — tất cả
không crash, `logcat` sạch không `FATAL EXCEPTION`/"recycled bitmap" trong suốt phiên. RadioButton
(gallery multi-select) đã exercise nhiều lần qua các lần chọn ảnh trong phiên, không lỗi.
`colorBackground`/`BlinkCursorView` không có UI trực tiếp để check bằng mắt (dead code/cosmetic),
verify đủ bằng unit test.

## Bugfix đợt audit trước release, 2026-09-29 (`/code-review --level max`, loại AD/VIP/keystore)

Re-audit toàn bộ `app/src/main` + `cmonet/src/main` tập trung logic/feature (không phải Ad/VIP/keystore
— các mục đó user quyết định tự xử lý riêng, xem `doc/task/BACKLOG.md`). 5 finding, verify tay từng
cái bằng đọc source thật trước khi fix — 4 fix theo lựa chọn user, 1 (dead param `scale: Boolean` ở
`buildIconBitmapShader`) chỉ cosmetic, không fix (không ảnh hưởng hành vi):

- [x] **OOM khi output "Original"** — `BitmapUtils.decodeBitmapFromUri(reqLongEdge<=0)` từng có
  nhánh early-return decode full-res, KHÔNG BAO GIỜ gọi `computeMaxSafeDimension` — trái doc comment
  ENH-14/OOM-PROTECT (chỉ áp dụng bảo vệ OOM khi user chọn resize, bỏ sót ảnh 108MP + output gốc).
  Fix: xoá hẳn nhánh early-return, dùng chung 1 đường code cho mọi `reqLongEdge` (computeMaxSafeDimension
  tự xử lý đúng case `<=0` — giữ nguyên kích thước nếu ảnh dưới ngưỡng an toàn). Thêm tham số
  `maxHeapBytes` (default `Runtime.getRuntime().maxMemory()`) để test được mà không cần dựng bitmap
  108MP thật. Xoá luôn `decodeBitmapWithExif` (dead code sau khi gộp nhánh). Test mới:
  `BitmapUtilsDownsampleExportRoboTest.decodeBitmapFromUri_reqLongEdgeZero_tinyHeap_stillDownsamplesToPreventOom`.
  Đổi kèm: `BitmapUtilsInputStreamCountRoboTest` — path Original giờ mở 3 stream thay vì 2 (cần đọc
  bounds trước khi quyết định downsample) — đánh đổi chấp nhận được để đổi lấy bảo vệ OOM thật.
- [x] **`BatchExportWorker.enqueue` REPLACE âm thầm huỷ batch đang chạy** — nếu ViewModel mới (thoát
  app rồi quay lại) không biết có batch export khác đang chạy dở, `ExistingWorkPolicy.REPLACE` huỷ
  ngang không báo lỗi. Fix: thêm `BatchExportWorker.isActive(context)`, `SaveImageBSDialogFragment`
  hỏi xác nhận (`MaterialAlertDialogBuilder`, string `export_conflict_dialog_*`, đủ 14 locale) trước
  khi enqueue nếu phát hiện batch khác đang chạy. Test mới: `BatchExportWorkerIsActiveRoboTest`
  (dùng `Worker` chặn bằng `CountDownLatch` để có cửa sổ RUNNING xác định, tránh flaky).
- [x] **`BatchExportEngine.kt` copy-paste `scaleY` đọc nhầm `MSCALE_X`** — vô hại hiện tại (scale
  luôn đồng nhất X/Y do `WaterMarkImageView.adjustMatrix()` luôn `postScale(scale, scale)`), nhưng
  bẫy bug âm thầm nếu sau này scale không đồng nhất. Fix 1 dòng đổi thành `MSCALE_Y`, không có test
  riêng (hành vi hiện tại provably không đổi được qua path thật — `adjustMatrix` luôn ép uniform).
- [x] **`ExportNaming.generateOutputName` trả tên file ẩn `.jpg`** khi pattern không rỗng nhưng
  resolve+sanitize ra chuỗi rỗng (vd `{filename}` trên ảnh DISPLAY_NAME rỗng/chỉ có đuôi) — dotfile
  dễ bị ghi đè hàng loạt ảnh khác cùng tên. Fix: fallback về `ewm_{timestamp}` khi base rỗng, dùng
  chung logic với pattern rỗng. Test mới:
  `ExportNamingConflictTest.generateOutputName_patternResolvesToEmptyString_fallsBackToTimestampPrefix`.

**Verify:** `./gradlew testDebugUnitTest` toàn bộ 1015+ test PASS, `./gradlew ktlintCheck` PASS,
`./gradlew lintDebug` không phát sinh warning/error mới (baseline giảm 222 `ExtraTranslation` đã tự
hết do bổ sung đủ string 14 locale).

**Smoke test thật trên TECNO_KJ7 (`115333744A005844`), 2026-09-29:** cài `assembleDebug`, chọn 4
ảnh → editor render watermark đúng → menu Lưu → output "Original" (đúng nhánh vừa fix) → xuất
4/4 thành công, file ghi thật ra `/Pictures/WaterMarkCreator/ewm_<timestamp>.jpg` (tên đúng fallback
mặc định, kích thước hợp lệ 56-67KB) → "Xem trong thư viện"/"Chia sẻ" hiện đúng. `logcat` sạch, không
`FATAL EXCEPTION`, không quảng cáo che UI trong suốt flow. Riêng kịch bản dialog xác nhận REPLACE
(fix 2) không kịp bắt trực tiếp trên device do 4 ảnh test nhỏ (800×600) export xong quá nhanh để giữ
được cửa sổ RUNNING — đã cover đủ bằng `BatchExportWorkerIsActiveRoboTest` (dùng `Worker` chặn bằng
`CountDownLatch`, xác định, không phụ thuộc timing thật).

## Review pass 2 (`/code-review --level max` trên chính diff 4 fix ở trên), 2026-09-29

Tự audit lại toàn bộ diff bằng code-review độc lập, tìm thêm 4 finding (đọc source verify trực
tiếp trước khi fix, không tin theo báo cáo suông):

- [x] **`decodeBitmapFromUri` bounds-decode thiếu guard null stream** — bước `inJustDecodeBounds`
  (đã tồn tại từ trước cho nhánh resize, nay áp dụng luôn cho "Original" sau fix OOM ở trên) gọi
  `BitmapFactory.decodeStream(it, null, options)` không kiểm tra `it == null` (quyền bị thu hồi giữa
  chừng/provider lỗi) — trong khi bước decode pixel thật ngay bên dưới ĐÃ có guard này. Fix: thêm
  guard giống hệt, trả `Result.failure` thay vì rủi ro decode với stream null. Test mới:
  `BitmapUtilsNullStreamGuardRoboTest` (2 case: Original + resize, provider giả lập trả null).
- [x] **`BatchExportWorker.isActive()` block main thread** — hàm gọi `ListenableFuture.get()` của
  WorkManager (query Room đồng bộ) ngay trong click handler của nút Save — rủi ro jank/ANR nếu
  WorkManager chậm (DB nguội, máy yếu), dù thực tế hiếm khi chậm tới mức đó. Fix: chuyển sang
  `viewLifecycleOwner.lifecycleScope.launch { withContext(Dispatchers.IO) { ... } }`, không block UI
  thread nữa. Cập nhật 3 test `SaveImageBSDialogFragmentExportConflictRoboTest` sang poll
  (`idle()`+sleep) thay vì kỳ vọng đồng bộ ngay sau `performClick()`.
- [x] **`ExportNaming` fallback timestamp không đủ phân biệt cùng batch** — `ewm_${currentTimeMillis()}`
  (base rỗng, kể cả case pattern rỗng hoàn toàn có từ trước) có thể trùng nếu 2 ảnh cùng batch xử lý
  xong trong cùng 1 millisecond (ảnh nhỏ/máy nhanh) → ghi đè lẫn nhau tuỳ `conflictPolicy`. Fix: thêm
  hậu tố `_${index + 1}` (đã có sẵn tham số, cùng quy ước `{seq}`). Cập nhật 2 test cũ
  (`MainViewModelGenerateOutputNameRoboTest`) khớp format mới + test mới verify 2 ảnh không đụng tên.
- [x] Không sửa: dead param `scale: Boolean` (đã ghi nhận review pass 1) vẫn giữ nguyên, cosmetic.

**Verify:** `./gradlew testDebugUnitTest` full suite PASS (982 test, chỉ 2 flaky pre-existing
`SaveImageBSDialogFragmentProofingRoboTest`/`...InvisibleRoboTest` — xác nhận KHÔNG liên quan diff
này, pass ổn định khi chạy riêng và không đụng file nào trong 4+4 fix). `ktlintCheck` PASS,
`lintDebug` không phát sinh warning/error mới. 3 test conflict-dialog + `BatchExportEngineScaleTest`
+ `BatchExportWorkerIsActiveRoboTest` chạy lặp lại 3 lần liên tiếp đều xanh (không flaky).

**Smoke test thật trên TECNO_KJ7, 2026-09-29 (sau review pass 2):** gỡ cài sạch, cài lại
`assembleDebug`, cấp quyền ảnh, chọn 1 ảnh → export Original + pattern rỗng → file ghi ra
`ewm_1790660019504_1.jpg` (đúng format mới có hậu tố `_1`, 54KB, hợp lệ) → "Chia sẻ" hiện đúng.
`logcat` sạch, không `FATAL EXCEPTION` trong suốt phiên (bao gồm cả batch 13 ảnh Original trước đó).
Riêng thao tác cuộn dialog Export trên thiết bị TECNO/Transsion gesture-nav đôi lúc bị hệ thống hiểu
nhầm thành cử chỉ Home nếu vuốt gần mép dưới — không liên quan code app, tránh bằng cách vuốt trong
vùng an toàn (y giữa màn hình).

**Điểm tự audit: 9.5/10.** Trừ nhẹ vì: (1) kịch bản 2 batch export chồng nhau chỉ verify được qua
Robolectric (rủi ro thao tác gesture thật trên thiết bị dùng chung nếu cố tái hiện), (2) fix
`scaleY` không test qua path thật (bản chất không thể — `adjustMatrix` luôn ép uniform X/Y). Đủ
điều kiện >9/10 theo `PROMPT_TEMPLATE.md` — đã push.

## Tính năng cần triển khai

- [x] ~~Tích hợp Firebase~~ — ❌ Skipped (2026-09-27): dự án không có backend, không tích hợp Firebase thật. Đã xóa dòng `// TODO firebase` trong `MyApplication.kt`. Backup/Restore đã xong local-only (SAF + zip, xem dòng dưới), không phụ thuộc Firebase.
- [x] ~~Backup/Restore Template & Signature~~ — ĐÃ XONG (2026-09-16, đề xuất F): local-only qua SAF + zip, không cần Firebase. Chi tiết xem `doc/feat.md` mục #21.
- [x] ~~Thêm tính năng chọn màu (Color)~~ — ĐÃ XONG (`FuncTitleModel.Color` → `ColorFragment`)
- [x] ~~Thêm tính năng chia sẻ ứng dụng (Share App)~~ — ĐÃ XONG (2026-09-06): pill "Share App" trong `AboutActivity` (`a_about.xml` id `tvShareApp`, bọc trong `HorizontalScrollView` cùng Rate/More Apps) mở `Intent.ACTION_SEND` text kèm link Play Store (`R.string.share_app_message`). Verify trên emulator: chooser "Sharing text" hiện đúng nội dung `Check out Watermark Creator-Debug: https://play.google.com/store/apps/details?id=com.mckimquyen.watermark`.
- [x] ~~QR Code watermark~~ — ĐÃ XONG (`QrCodeGenerator` + `QrCodeBottomSheetFragment`, reuse luồng Image watermark).

## Cải thiện mã nguồn

- [x] ~~Dọn code comment trong `MyApplication.kt` & `build.gradle.kts`~~ — ĐÃ XONG (cả khối dead-code MaxAd/applyPalette trong `AboutActivity.kt` cũng đã xóa).
- [x] ~~Dọn 7 file nháp ở gốc repo~~ — ĐÃ XONG (`git rm` build_log.txt, fix_anim.kt, old_launch.kt, sim.kt, sim.py, test_anim.kt, translate.py).
- [x] ~~**Hardcoded log tag `roy93~`**~~ — ĐÃ XONG: gom 92 chỗ về hằng số chung `LOG_TAG` trong `AppConst.kt` (top-level, package gốc).
- [x] ~~**Magic numbers**: `MyApplication.catchException` (`1024 * 1024 / 2 / 10`)~~ — ĐÃ XONG: tách hằng `MAX_CRASH_STACK_TRACE_LENGTH` có doc.
- [x] ~~**ENH-36: 33 `Log.i`/`Log.w` chưa gate `BuildConfig.DEBUG`**~~ — ĐÃ XONG (2026-09-28): sót khỏi
  đợt gate `Log.d` trước đó (ENH-03) — `AppLog.w` trước đây cũng KHÔNG gate gì. Thêm `AppLog.i`
  (gate `BuildConfig.DEBUG`, cùng khuôn `AppLog.d`), gate luôn `AppLog.w`; GIỮ `AppLog.e` không gate
  (lỗi thật cần thấy ở bản release) nhưng scrub URI ảnh user khỏi message (`WaterMarkRepository`).
  Xoá hẳn (không gate) ~15 log rác tần suất cao trong vòng vẽ/gesture (`onTouch`/`onScale` mỗi sự
  kiện chạm, `onSizeChanged`, touch handler `MultiSelectRv`, gesture callback `PhotoPreviewItem`,
  "Hit the cache bitmap!" mỗi lần decode) — gate cũng không giải quyết được áp lực GC dựng string
  template ở tần suất này. Chi tiết: `doc/task/done/ENH-36-gate-log-i-w-e-con-sot-ngoai-enh-03.md`.

## Kiểm thử (Test)

- [x] Đã bật lại test deps trong `settings.gradle.kts` + `app/build.gradle.kts`; thêm `testOptions` cho Robolectric.
- [x] **271 unit test** (JVM + Robolectric, `testAppReleaseDebugUnitTest`) — PASS 100% (2026-09-16).
- [x] **Fix deadlock full-suite (2026-09-16):** `context.waterMarkDataStore`/`userDataStore` (`by preferencesDataStore(...)`) là singleton keyed theo FILE PATH chứ không theo Context — Robolectric chạy hết mọi `@Test` trong 1 JVM fork dùng chung 1 main-thread executor, nên nhiều test method/class vô tình share chung 1 DataStore instance ngầm; nếu Robolectric teardown sandbox đúng lúc DataStore đang giữ Mutex ghi dở, Mutex kẹt khoá vĩnh viễn → `runBlocking { edit {} }` ở test sau treo mãi. Chỉ lộ ra khi chạy full suite không filter `--tests` (đủ nhiều test tích luỹ trong 1 JVM). Fix: `app/src/test/.../testutil/TestDataStores.kt` cấp DataStore cô lập (file tạm riêng mỗi test), áp dụng cho 18 file test đang đụng singleton thật.
- [x] **Fix `SaveImageListAdapterPreviewRoboTest` (2026-09-16):** thiếu theme M3 khi inflate `item_saving_image` (`ProgressImageView` đọc `?attr/colorTertiary`/`colorError`) — bọc `ContextThemeWrapper(context, R.style.Theme_MyApp)`, đúng pattern các test khác đã dùng sau đợt migrate M3.
- [x] **9 integration test** (androidTest, PASS trên TECNO KJ7 - Android 14): Room `TemplateDaoIntegrationTest` (4) + `BitmapUtilsDecodeFailureIntegrationTest` (1) + `BitmapUtilsContextThreadingIntegrationTest` (2, mới — decode JPEG thật + EXIF orientation=90 qua `context` tham số) + `WaterMarkRepositoryIntegrationTest` (2, mới — default text/round-trip qua `@ApplicationContext` + DataStore thật).
- [x] Smoke test thủ công trên TECNO KJ7: Splash → Launch → nhận ảnh qua `ACTION_SEND` → editor render watermark → Export to album (file thật ghi ra `/Pictures/WaterMarkCreator/`) — không crash, logcat sạch `FATAL EXCEPTION`.
- Lệnh: `./gradlew :app:testDebugUnitTest` (toàn bộ) hoặc thêm `--tests "*.ClassName"` cho 1 class; `./gradlew :app:connectedDebugAndroidTest` cho instrumentation test (cần thiết bị). (Tên task đổi từ `testAppReleaseDebugUnitTest`/`connectedAppReleaseDebugAndroidTest` sau khi gộp bỏ flavor `appTest`/`appRelease` ở ENH-26.)

## Code review (2026-09-16, `/code-review master..dev high`)

Review toàn bộ diff `dev` so với `master` — tìm 6 phát hiện, đã fix hết + thêm test, xem chi tiết
commit `c3af54a`:

- [x] **[Nghiêm trọng]** `BatchExportEngine.generateImage()`: caption rỗng ("" — cố ý không
  watermark ảnh đó) không skip vẽ như `generatePreviewBitmap()` — `layoutPaint` (Paint() mặc định
  đen, alpha 255) vẫn tô kín đè lên ảnh xuất ra vì shader null. Preview/export lệch nhau, ảnh xuất
  ra thật bị đen kín. Fix: `shouldSkipTextWatermark()` dùng chung 2 nơi.
- [x] **[Bảo mật, zip-slip]** `SignatureRepository.importSignatureBytes()`: tên file lấy trực
  tiếp từ zip entry (backup do user chọn qua SAF, không tin cậy), không sanitize — entry tên
  `../../../shared_prefs/evil.xml` có thể ghi đè file ngoài thư mục signature. Fix:
  `File(fileName).name` chỉ lấy phần tên cuối.
- [x] Restore template không dedup — Template.id luôn = 0 (autoGenerate PK mới) nên restore 2 lần
  nhân đôi mọi template. Fix: dedup theo content trước khi insert.
- [x] `BatchCaptionBSDialogFragment`: dùng snapshot `imageList.size` chụp lúc mở dialog thay vì
  đọc live lúc bấm Apply — list ảnh đổi giữa chừng có thể gán sai caption. Fix: đọc lại live.
  **Không test tự động được** (hạn chế đã ghi nhận sẵn — `requireContext() as MainActivity` không
  launch được thật trong JVM test của repo này).
- [x] 2 chỗ DRY: rule `caption ?: text` copy-paste 2 nơi (chính là nguyên nhân bug #1 lệch nhau) —
  gộp thành hàm dùng chung. Boilerplate "expand bottom sheet" lặp 4 lần ở
  BatchCaption/Qr/Signature/PositionAnchor fragment — gộp vào `BaseBSDFragment.expandBottomSheetFully()`.
- [x] **Phát hiện phụ:** viết test cho fix zip-slip lộ thêm 1 bug hạ tầng CÙNG LOẠI với deadlock
  DataStore — `FileProvider.getUriForFile()` cache `PathStrategy` theo authority ở static field
  AndroidX, sống sót qua ranh giới Application/Context của từng `@Test` dưới Robolectric. Fix:
  `SignatureModel.uri` tính lazy (không còn eager ở constructor), tránh gọi `FileProvider` khi
  caller không cần URI ngay — né hẳn collision, cũng là cải tiến perf hợp lý độc lập.

## Sửa lỗi rò rỉ bộ nhớ (Memory Leak Fixes)

> ✅ **Re-verified (2026-09-16):** đọc lại `WaterMarkImageView.kt` thật — `onDetachedFromWindow()`
> vẫn cancel `generateBitmapJob` + release `mainImageBitmapValue`/`iconBitmapValue`, không còn
> `Executors.newSingleThreadExecutor()`. Cảnh báo trong `CLAUDE.md` (root) là lỗi thời, đã sửa lại.
> `drawableAlphaAnimator`/`animator` (snap-back sau pinch) không bị cancel ở `onDetachedFromWindow`
> nhưng duration ngắn (300-450ms) — không đáng kể, không cần fix.

- [x] ~~**WaterMarkImageView** — scope/executor leak~~ — ĐÃ XONG, re-verify lại 2026-09-16 vẫn đúng:
  - `onDetachedFromWindow()` đã override và gọi `generateBitmapJob?.cancel()`.
  - Không còn `Executors.newSingleThreadExecutor()`; dùng `Dispatchers.Default` cho `generateBitmapCoroutineCtx`. (Import rác `Executors` đã được xóa.)
- [x] ~~**MyApplication** — static `instance: Context`~~ — ĐÃ XONG (2026-09-06): gỡ field `instance`, thread `Context` qua toàn bộ chuỗi gọi.
  - `MainViewModel`/`WaterMarkRepository` nhận `@ApplicationContext` qua Hilt (`RepositoryModule.provideWaterMarkRepository` cập nhật theo).
  - `BitmapUtils`: `decodeBitmapWithExif(Sync)`/`decodeBitmapFromUri`/`decodeSampledBitmapFromResource(Sync)` nhận thêm tham số `context`; caller ở `MainViewModel` truyền `appContext`, `WaterMarkImageView` truyền `context` (View) sẵn có.
  - `SaveImageListAdapter`/`PhotoListPreviewAdapter` dùng field `context` sẵn có thay vì `MyApplication.instance`; `FuncPanelAdapter` nhận thêm tham số `context` ở constructor.
  - `DetectedPerformanceSeekBarListener` (class chưa dùng ở đâu) nhận `context` ở constructor.
  - `MainActivity` dùng `application as MyApplication` thay vì `MyApplication.instance as MyApplication`.

## Bugfix đợt self-audit 2026-09-27, fix 2026-09-28

Self-audit toàn app (không giới hạn phạm vi cũ) tìm 8 finding mới (BUG-37..43, ENH-36), đã fix hết
+ phát sinh thêm 1 bug thật lúc smoke test (BUG-44). Chi tiết đầy đủ + test + smoke test xem từng
file `doc/task/done/<ID>-*.md`; tóm tắt ở đây:

- [x] **BUG-37** — MediaStore `OVERWRITE` ghi thất bại để lại ảnh CŨ mắc `IS_PENDING=1` (ảnh mất
  khỏi gallery vĩnh viễn) — cleanup cũ chỉ xử lý đúng nhánh `insert()` mới (BUG-19), chưa cover
  nhánh `update()` đè lên row có sẵn (FEAT-19 thêm sau). Fix: `MediaStoreWriteFailureCleanup.decide()`.
- [x] **BUG-38** — leak 2 bitmap `ComparePreviewBottomSheetFragment` — xem `doc/memory_leak.md` mục 3.1.
- [x] **BUG-39** — auto-contrast (IDEA-06) chỉ áp preview editor, `BatchExportEngine` (export thật/
  preview grid/so sánh) bỏ qua hoàn toàn — ảnh xuất ra giữ màu chữ cũ dù preview đã đảo màu. Fix:
  trích `WaterMarkImageView.resolveAutoContrast()` dùng chung, wire vào cả 3 điểm vẽ.
- [x] **BUG-40** — tên file xuất không sanitize, token EXIF `{exposure}`/`{fnumber}` chứa `/` làm
  export lỗi. Fix: `ExportNaming.sanitizeFileName()` dùng chung với `ExportZipHelper`.
- [x] **BUG-41** — switch "Dynamic Color" ở About không tắt được trên mọi máy Android 12+ (logic
  `||` khiến năng lực thiết bị luôn thắng lựa chọn user) + `applyToActivitiesIfAvailable` gọi trùng
  2 lần. Fix: tách `isDeviceCapable()`/`isUserEnabled()` trong `MonetManufacturer`.
- [x] **BUG-42** — `sizeHasChanged = w != oldh` (lỗi copy-paste 3 custom view: `CircleImageView`/
  `ColoredImageVIew`/`ProgressImageView`) — view gần vuông (avatar About) bỏ lỡ resize thật.
- [x] **BUG-43** — EXIF `focalLength`/`exposureTime` parse không guard — `NumberFormatException`
  lan ra làm decode/export thất bại mơ hồ; mẫu số 0 sinh `"Infinitymm"`/`"1/2147483647s"`.
- [x] **BUG-44** (phát sinh lúc smoke test BUG-39) — nút export giữ nhầm label/state "Chia sẻ" từ
  lần export THÀNH CÔNG trước đó trong cùng phiên, không trigger export mới cho ảnh vừa chọn. Root
  cause: `WorkManager` replay lại `WorkInfo` SUCCEEDED cũ mỗi lần `reattachExportWorkIfRunning()`
  gọi lại, ghi đè `saveResult` vừa được `resetJobStatus()` xoá. Fix: guard idempotent
  `lastHandledFinishedWorkId`.

## Tham khảo
- Chi tiết các leak đã fix: xem `doc/memory_leak.md`.
- Hàng đợi ticket kỹ thuật đầy đủ (đang làm + tồn đọng): xem `doc/task/BACKLOG.md`.
- Trạng thái migrate quảng cáo: xem `doc/AD.MD`.
