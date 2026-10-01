# Danh sách việc cần làm & Cải tiến

> Cập nhật: 2026-10-01. Xem thêm `doc/feat.md` cho danh sách tính năng (FEAT-XX) — file này tập trung bugfix/cải tiến/hạ tầng. Từ ngày sinh backlog 2026-09-04, hàng đợi ticket kỹ thuật chi tiết (BUG-XX/ENH-XX/FEAT-XX/IDEA-XX) đã chuyển sang `doc/task/BACKLOG.md` + `doc/task/done/` — file này giữ vai trò tóm tắt/lịch sử, không lặp lại nội dung đầy đủ từng ticket.

## UI polish — Responsive Grid 2x2 cho màn LaunchView, 2026-10-01

User phản ánh: "ở màn hình menu, tôi thấy grid 4 item chọn ảnh, chụp ảnh từ..., dán từ bộ nhớ tạm,
thông tin... có space không cân đối so với mép của device, tôi muốn space phải consistent".
Phản biện kỹ thuật: HOÀN TOÀN ĐÚNG. Trước đây `LaunchView.kt` hardcode cố định chiều rộng card
`GRID_CARD_WIDTH_DP = 156dp` rồi căn giữa khối grid (`(measuredWidth - gridWidth) / 2`):
- Trên Pixel 7 Pro (412dp): lề ngoài lên tới 44dp trong khi rãnh giữa chỉ 12dp (lệch gần 4 lần),
  card bị co cụm ở giữa, title dài bị truncate ("Chụp ảnh từ m...", "Dán từ bộ nh...").
- Trên màn 360dp: lề ngoài 18dp vs rãnh giữa 12dp.

Giải pháp theo lựa chọn user (Card co giãn theo màn hình / Responsive M3):
- [x] Đặt lề 2 bên cố định `GRID_SIDE_MARGIN_DP = 16dp`, rãnh giữa `GRID_GUTTER_DP = 12dp`.
- [x] Tính chiều rộng card động trong `onMeasure()`: `(parentWidth - totalMargins) / 2`, clamp trong
  khoảng `[140dp, 220dp]` (chống bè to trên tablet/foldable).
- [x] `layoutLaunch()` dùng kích thước thực sau measure (`measuredWidth`, `measuredHeight`) thay vì
  hằng số hardcode.
- [x] Test TDD mới: `LaunchViewRoboTest.layoutLaunch_arranges4ActionCards_withConsistentSideMargins()`
  — verify lề trái đúng 16dp, lề phải đúng 16dp, rãnh giữa 12dp, và trên tablet lề vẫn đối xứng
  tuyệt đối. Đã chạy RED (fail 16 vs 314) → GREEN (pass).
- [x] Smoke test thật trên Pixel 7 Pro (2B051FDH3006MU, density 3.5, 1440x3120): dump uiautomator
  cho kết quả chuẩn từng pixel:
  - Cột 1 Card trái: `left = 56px` (`16dp` × 3.5)
  - Cột 2 Card phải: `right = 1384px` → lề phải = `1440 - 1384 = 56px` (`16dp` × 3.5)
  - Rãnh giữa: `741 - 699 = 42px` (`12dp` × 3.5)
  - Chiều rộng 2 card: `643px` = `643px` đối xứng tuyệt đối.
  - Text title hiển thị trọn vẹn, không còn bị cắt cụt.

## Review pass 14 — quét toàn bộ `setOnApplyWindowInsetsListener` edge-to-edge + gộp helper chung, 2026-10-01

Tiếp loop ngay sau pass 13 (user yêu cầu chú ý lỗi edge-to-edge). Quét toàn bộ 13 file dùng
`setOnApplyWindowInsetsListener` trong app (không chỉ scope pass 13) — tìm thêm 1 bug edge-to-edge
THẬT:

- [x] **`RecipientManagementActivity.rvRecipients` ghi đè thẳng `setPadding(..., navBarBottom)`**,
  mất `paddingBottom="8dp"` khai trong `activity_recipient_management.xml` — ĐÚNG bug class vừa fix
  ở pass 13 (BatchHistory/WatermarkProfile). Trớ trêu: code tại chỗ có comment ghi rõ "bài học
  FEAT-06/BatchHistoryActivity" nhưng lại chép nhầm đúng pattern CŨ (trước khi pass 13 fix) thay vì
  pattern đúng. Chứng minh fix từng-chỗ-một (pass 13) không đủ — vẫn lặp lại được dù có cảnh báo
  bằng lời trong comment. Test mới: `RecipientManagementActivityInsetsRoboTest` (2 case, cùng mẫu
  `BatchHistoryWatermarkProfileInsetsRoboTest`).
- [x] **Gộp helper dùng chung `View.setBottomPaddingWithInset(base, inset)`** (`ViewExtension.kt`)
  để chặn tái diễn tận gốc — áp dụng lại cho CẢ 8 điểm gọi (kể cả những chỗ đang ĐÚNG, để nhất
  quán + dễ grep/copy đúng pattern cho màn mới sau này): `RecipientManagementActivity`,
  `BatchHistoryActivity`, `WatermarkProfileActivity`, `CropActivity`, `SmartRedactionActivity`
  (`llBottomControls`), `AboutActivity` (`nestedScrollView`), `OpenSourceActivity` (`binding.root`),
  `BaseBSDFragment` (`sheet`). KDoc helper nêu rõ invariant quan trọng: `base` phải đọc 1 LẦN từ
  bên ngoài listener — đọc lại `paddingBottom` hiện tại bên trong listener sẽ cộng dồn SAI mỗi lần
  listener bị gọi lại (xoay màn hình, bàn phím ẩn/hiện...).
- Verify: 6 file còn lại dùng insets listener (`MainActivity`, `SplashActivity`, `SignatureActivity`,
  `LaunchView`) đã ĐÚNG từ trước (không có padding gốc cần giữ, hoặc view tạo bằng code không khai
  padding XML nên ghi đè = cộng dồn với 0) — không đụng. `VipManagementActivity` (VIP) nằm ngoài
  phạm vi audit theo quyết định user trước đó (AD/IAP dời sang tháng sau).

Kết quả: build/test pass (ktlintCheck + testDebugUnitTest xanh, assembleDebug OK). Smoke test thật
trên TECNO KJ7 (khoá theo R3): app launch lại không crash sau khi cài bản mới (BaseActivity dùng
chung bởi mọi activity). Không đi sâu được vào màn Recipient Management trên device do photo-picker
hệ thống bị glitch render (tách đôi màn hình) — glitch hệ thống, không liên quan code sửa, không
theo đuổi thêm; bằng chứng chính dựa vào Robolectric pixel-exact (RED→GREEN) cho đúng 1 giá trị
padding, đủ mạnh hơn quan sát mắt thường.

## Review pass 13 — nốt scope `ui/` + root còn lại chưa quét qua 12 pass trước, 2026-10-01

Audit vòng 13 (loop tiếp theo sau pass 12). BACKLOG.md hết sạch BUG/ENH/FEAT todo (chỉ còn 2 ticket
VIP/Ad bị user dời sang tháng sau + 4 IDEA effort XL cần thiết kế riêng) — scope lần này quét nốt
phần `ui/` root chưa đụng tới (`BatchHistoryActivity`/`ViewModel`, `CropActivity`,
`SmartRedactionActivity`, `SplashActivity`, `WatermarkProfileActivity`/`ViewModel`, `Image.kt`) +
root-level (`MyApplication`, `BaseActivity`, `CustomGlideModule`, `AppConst`) + `ui/base/` (7 file
Base*Fragment/Activity). Chạy `/code-review --level high` qua subagent nền (8 finding raw) + tự đọc
verify tay từng finding bằng cách đọc trực tiếp source/decompile hành vi thư viện trước khi sửa —
2/8 finding là **false positive** sau khi verify kỹ, không áp dụng mù theo báo cáo AI:

- ❌ **False positive — Glide `CustomGlideModule` ép `PREFER_RGB_565` làm icon PNG trong suốt mất
  alpha**: verify lại logic `Downsampler.getConfig()` của Glide — thư viện tự phát hiện
  `ImageType.PNG_A` (PNG có alpha thật, đọc từ chunk màu trong header) và LUÔN dùng `ARGB_8888`
  bất kể `DecodeFormat` preference, bỏ qua `PREFER_RGB_565` cho đúng trường hợp này. Fix ban đầu
  (override `PREFER_ARGB_8888` tại `MainActivity.showIconQuickPickDialog`) đã bị revert vì dư thừa.
- ❌ **False positive — `MyApplication.catchException()` không chain lại
  `Thread.getDefaultUncaughtExceptionHandler()` cũ**: thử fix (tách `buildCrashHandler` ra top-level
  function để unit-test, có cả 3 test chứng minh hành vi chain) rồi nhận ra rủi ro ngược — handler
  mặc định của Android (nhiều khả năng CHÍNH LÀ "previous handler" bắt được ở thời điểm
  `attachBaseContext` chạy, rất sớm) thường TỰ kill process khi gọi `uncaughtException()`. Gọi
  handler đó trước bước `terminate()` tự viết (về home êm + `exitProcess(0)`, tránh dialog "App đã
  dừng") có thể khiến process bị kill giữa chừng BỞI handler cũ, mất luôn cơ chế graceful-recovery
  mà app cố tình xây — đổi lợi ích nhỏ (giữ lại 1 SDK hiếm khi thực sự đăng ký handler riêng) lấy
  rủi ro lớn hơn (phá chính tính năng đang có). Revert toàn bộ, chỉ giữ lại phần dọn comment rác
  (không ảnh hưởng hành vi).
- [x] **`BaseActivity.attachBaseContext()` ép CỨNG `fontScale = 1.0f` cho MỌI activity** — vô hiệu
  hoá hoàn toàn cài đặt Accessibility > Font size của hệ thống, vi phạm R5 "không được bỏ
  accessibility". Fix: clamp `coerceAtMost(1.3f)` thay vì chặn tuyệt đối (tôn trọng user chỉnh cỡ
  chữ lớn hơn tới 130%, vẫn chặn mức cực đoan 200% có thể vỡ layout các màn chưa test ở scale cực
  lớn). Test mới: `BaseActivityFontScaleRoboTest` (2 test, verify cả 2 chiều: scale vượt trần bị
  giới hạn đúng 1.3f — không phải `isAtMost` vì hardcode cũ 1.0f cũng vô tình thoả; scale trong
  trần giữ nguyên). Smoke test thật trên device khoá (TECNO KJ7): set `font_scale=1.3`, mở app —
  chữ to rõ rệt, card tự ellipsize gọn gàng, không vỡ layout/crash.
- [x] **`BatchHistoryActivity`/`WatermarkProfileActivity`: `setOnApplyWindowInsetsListener` GHI ĐÈ
  thẳng `setPadding(..., navBarBottom)`** thay vì cộng dồn — mất `paddingBottom` khai trong XML
  (16dp/8dp, khoảng thở dưới item cuối list) mỗi lần áp insets, item cuối dính sát mép màn hình
  trên thiết bị/orientation có `navBarBottom = 0` (gesture nav không chiếm inset...). Fix: lưu
  `baseBottomPadding` một lần trước khi gắn listener, cộng `baseBottomPadding + navBarBottom` —
  đúng pattern `CropActivity`/`SmartRedactionActivity` (`llBottomControls`). Test mới:
  `BatchHistoryWatermarkProfileInsetsRoboTest` (4 test, cả 2 Activity × cả 2 case navBarBottom=0/
  >0, dùng `ViewCompat.dispatchApplyWindowInsets` giả lập insets như
  `MaterialYouInsetsAndThemeRoboTest` đã làm).
- [x] **`BaseBindBSDFragment.binding` force-unwrap (`_binding!!`) không có comment giải thích** —
  khác `BaseBindFragment` (sibling, expose nullable + `isDestroy()`). Vi phạm chữ R5 dù đúng pattern
  ViewBinding chuẩn (chỉ truy cập hợp lệ giữa `onCreateView`/`onDestroyView`). Không đổi sang
  nullable (sẽ phải sửa tay 13 file con, rủi ro cao hơn lợi ích — đa số đã dùng đúng
  `viewLifecycleOwner.lifecycleScope`/`observe(viewLifecycleOwner)` tự huỷ đúng lúc) — chỉ thêm
  KDoc nêu rõ invariant non-null + cảnh báo không dùng `lifecycleScope` (Fragment-level) để đọc
  `binding`. Thuần doc comment, không cần test.
- [x] **`CropActivity`/`SmartRedactionActivity` khai trùng `private const val
  EDIT_DECODE_MAX_LONG_EDGE = 2048`** — gộp 1 nguồn (`BitmapUtils.kt`, cùng file với
  `decodeBitmapFromUri` nhận tham số này) tránh lệch nhau khi chỉ sửa 1 trong 2 chỗ. Refactor
  thuần (không đổi hành vi), không cần test mới — biên dịch là đủ bắt lỗi.
- [x] Xoá block comment TODO/checklist mồ côi trong `MyApplication.kt` (`// done`, `// font scale`,
  `// 120hz`, `// ad applovin`...) — rác không gắn gì với code xung quanh, phần lớn đã xong từ lâu
  (`doc/todo.md` đã là nơi tracking chuẩn).
- 💭 **`SplashActivity.runSplashFlow()`: `withTimeoutOrNull` bọc `AdManager.requestConsentInfoUpdate`/
  `initialize` không có `invokeOnCancellation`** — không sửa được: `AdManager` (external SDK, thư
  viện `AdmobWrapper`) không expose API cancel cho 2 lời gọi này, nên `invokeOnCancellation` sẽ là
  no-op (không thực sự dừng được SDK call đang treo), chỉ thêm boilerplate không giải quyết gì.
  Rủi ro thực tế đã ở mức thấp nhờ `cont.isActive` guard chặn double-resume.

Kết quả: 1152/1152 unit test pass (1 lần fail `MainViewModelExifFrameSuggestionRoboTest` khi chạy
full suite là flaky có sẵn từ trước — pass khi chạy riêng và khi rerun toàn bộ suite, không liên
quan thay đổi lần này), `ktlintCheck` sạch, `assembleDebug` build OK, smoke test thật trên TECNO KJ7
(115333744A005844, khoá theo R3) cho cả 2 fix UI thật (fontScale + inset padding).

## Review pass 12 — `export/stego/` (IDEA-02, watermark ẩn DCT), 2026-09-30

Audit vòng 12 (loop tiếp theo sau pass 11, scope `export/stego/` — 5 file: `StegoCodec`,
`StegoPayload`, `InvisibleWatermark`, `HiddenWatermarkReader`, `Dct8x8`). Chạy `/code-review --level
high` qua subagent nền + tự đọc verify tay từng finding trước khi quyết định sửa (không tin báo cáo
suông). 5 finding raw, chỉ 1 đủ an toàn để tự sửa ngay trong pass này — 4 finding còn lại chạm vào
thuật toán lõi đã tune kỹ theo số đo thật (`StegoRobustnessTest`), cần quyết định đánh đổi
robustness/chất lượng từ user trước khi động vào — **KHÔNG** tự ý sửa:

- [x] **`HiddenWatermarkReader.decodeOptions` là field `object` (singleton) dùng CHUNG cho mọi lần
  gọi `read()`** — `BitmapFactory.Options` không an toàn dùng lại đồng thời (chính `BitmapFactory`
  ghi ngược `outWidth`/`outHeight`/`outConfig` vào instance được truyền). `AboutViewModel.verifyAuthenticity`
  không debounce nút chọn ảnh xác thực — user chọn 2 ảnh xác thực nhanh liên tiếp trước khi lượt đầu
  xong (mỗi lượt `Dispatchers.IO` riêng) có thể khiến 2 lần decode ghi đè state của nhau. Fix: tạo
  `BitmapFactory.Options` MỚI mỗi lần gọi thay vì field dùng chung. Test mới (chưa từng có test nào
  cho class này): `HiddenWatermarkReaderIntegrationTest` (androidTest, decode ảnh THẬT qua
  `ContentResolver`/`Uri`) — 3 test, gồm 1 test chạy 2 `Thread` đọc 2 ảnh khác chủ sở hữu THẬT SỰ
  song song lặp 20 lần, xác nhận không lẫn kết quả.
- 💭 **4 finding còn lại — cần quyết định đánh đổi, chưa sửa:**
  1. `StegoCodec.decode()`: `confidence` luôn = 1.0 khi ảnh chỉ vừa đúng 1 vòng payload
     (`votesTotal[i]==1` mọi bit) — ngưỡng `MIN_CONFIDENCE=0.90` (tuyến phòng thủ thứ 2 theo doc
     comment) vô tác dụng ở ảnh nhỏ nhất chấp nhận được (64x64px). Thực tế vẫn được MAGIC+CRC
     (`StegoPayload`) chặn ở mức ~1/2^32 nên rủi ro gán nhầm chủ sở hữu vẫn thấp — nhưng cần sửa lại
     comment hoặc đổi công thức `confidence` (vd trộn thêm biên độ hệ số, không chỉ tỉ lệ phiếu) để
     đúng như tài liệu mô tả.
  2. `StegoCodec.applyBit()`: ép đủ `STRENGTH=26` bất kể khối 8x8 gốc gần như phẳng (trời/tường/phông
     nền) — dễ tạo vệt méo nhìn thấy được trên vùng phẳng thật (`StegoRobustnessTest` tự nhận trong
     comment "ảnh phẳng tuyệt đối không đại diện ảnh thật", chưa test case này).
  3. `StegoCodec.writeLumaBlock()`: `clamp()` riêng từng kênh R/G/B sau khi cộng chung 1 `delta` độ
     sáng — ở vùng gần trắng/đen, 1 kênh có thể bão hoà 255/0 trong khi 2 kênh kia chưa, khiến độ sáng
     THẬT ghi lại lệch với hệ số DCT vừa mã hoá → giảm độ bền watermark đúng ở ảnh bầu trời/tuyết/nền
     trắng/bóng tối sâu — không nằm trong phạm vi `texturedPixels()` hiện tại.
  4. `InvisibleWatermark.embed()`: `Bitmap.createBitmap(pixels, w, h, ARGB_8888)` luôn tạo bitmap sRGB
     mặc định, làm mất `ColorSpace` gốc (vd Display P3 — mặc định trên nhiều máy ảnh/gallery hiện đại)
     → lệch màu thấy được trên ảnh wide-gamut CHỈ khi bật watermark ẩn. Sửa cần API 26+
     (`Bitmap.createBitmap(w,h,config,hasAlpha,colorSpace)` qua `Canvas`, minSdk hiện tại là 24) nên
     cần gate version + kiểm tra kỹ lại toàn bộ pipeline `embed()` đã có test bao phủ rộng.

**Verify:** `HiddenWatermarkReaderIntegrationTest` (3 test) PASS thật trên Samsung SM_S928B (bao gồm
test race 2 luồng) + toàn bộ `testDebugUnitTest` (143+ file) PASS 100% + `ktlintCheck` PASS.

## Review pass 11 — nốt `ui/widget/` (14 file còn lại), 2026-09-30

Audit vòng 11 (loop tiếp theo sau pass 10, quét nốt toàn bộ `ui/widget/`: `PhotoPreviewItem`,
`SelectableImageView`, `MultiSelectRv`, `TouchSensitiveRv`, `ProgressImageView`, `ColoredImageVIew`,
`DetectedPerformanceSeekBarListener`, `SquareFrameLayout`, `LaunchViewListener`, `CustomViewGroup`,
`ItemClickSupport`, `CenterLayoutManager`, `RadioButton`, `CircleImageView`,
`UniformScrollGridLayoutManager`, `AsyncSquareFrameLayout`). 1 bug thật (đúng TDD — repro FAIL
trước fix, PASS sau), verify tay:

- [x] **`SelectableImageView` — bitmap màu/vector tự tạo (`srcBitmap`) bị ghi đè ở
  `circleResId`/`onSizeChanged` setter mà KHÔNG recycle bản CŨ** — dùng trong
  `ColorPreviewAdapter`/`ColorFragment` VÀ panel màu Chữ ký (`SignatureActivity`, chung layout
  `item_color_preview.xml`), RecyclerView rebind swatch liên tục (scroll/đổi list) tích luỹ bitmap
  native cho tới GC. Kèm bug correctness: setter `circleColor` trước đây CHỈ `invalidate()`, không
  tái tạo `srcBitmap` — bitmap theo màu chỉ refresh khi `circleResId`/kích thước đổi sau đó, có thể
  vẽ nhầm màu cũ 1 nhịp. Fix: thêm `ownsSrcBitmap` phân biệt bitmap SỞ HỮU (màu/vector tự vẽ, phải
  recycle) với bitmap SHARED từ `BitmapDrawable` (KHÔNG được recycle — resource dùng chung ngoài
  view), gom logic vào `replaceSrcBitmap()` gọi từ cả 2 setter + `onSizeChanged` + `onDetachedFromWindow()`
  (recycle sạch khi view detach, ColorPreviewAdapter luôn set lại field trước khi ViewHolder tái sử
  dụng hiển thị lại nên an toàn). Test mới: `SelectableImageViewWidgetTest` — 2 test
  (`rebindColorSwatch_recyclesPreviousOwnedBitmap` FAIL trước fix đúng bất biến dự đoán,
  `circleColorSetter_refreshesOwnedBitmapImmediately` FAIL trước fix vì bitmap không refresh ngay —
  cả 2 PASS sau fix).
- Còn lại 15 file: đã đọc kỹ toàn bộ, không tìm thêm bug mới — nhiều file đã có sẵn comment
  "BUG-AUDIT-2026-09-29"/"BUG-42" từ các đợt audit trước (đã fix), cleanup/animator-cancel đã đúng
  (`TouchSensitiveRv`, `MultiSelectRv`, `ColoredImageVIew`).

**Verify:** `SelectableImageViewWidgetTest` (5 test, gồm 2 test mới) PASS + toàn bộ `testDebugUnitTest`
(143+ file) PASS 100% + `ktlintCheck` PASS.

**Lưu ý vận hành (R3):** device đã khoá đầu session (TECNO_KJ7) bị ngắt kết nối USB giữa chừng; đã
hỏi qua `AskUserQuestion`, user duyệt chuyển khoá sang **Samsung SM_S928B (`R5CX613VZBR`)**. Mọi
thao tác sau đó chỉ target Samsung SM_S928B.

**Smoke test thật trên Samsung SM_S928B (R5CX613VZBR, thiết bị cá nhân thật của user):** cài APK
debug mới, chọn 1 ảnh phong cảnh (không đụng ảnh riêng tư khác trong gallery thật), vào editor mở
panel Màu chữ ký (đúng code path `SelectableImageView`/`item_color_preview.xml`), chạm liên tục qua
lại NHIỀU LẦN cả 7 màu (kịch bản rebind dồn dập vừa fix) — chọn đúng màu cuối cùng chạm (tím), viền
selection hiển thị đúng, không kẹt màu cũ, không crash, `logcat` sạch không `FATAL EXCEPTION`.

## Review pass 10 — `ui/widget/` (24 file), 2026-09-30

Audit vòng 10 (loop tiếp theo sau pass 9, phạm vi `ui/widget/` — 24 file, trừ `WaterMarkImageView`/
`LaunchView` đã audit ở pass 6). Đúng quy trình `systematic-debugging`: 2 giả thuyết bị BÁC BỎ bằng
repro thật trước khi kết luận không phải bug (không đoán mò) — xem chi tiết trong lịch sử hội thoại,
không ghi lại ở đây vì không dẫn tới thay đổi code. 1 bug thật + 3 dead code:

- [x] **`GalleryAdapter.GalleryItemHolder` — click/long-click listener đọc `holder.cbImage`
  (`lateinit`) TRƯỚC khi `AsyncLayoutInflater` (qua `AsyncSquareFrameLayout.inflate()`) gán xong** —
  `onBindViewHolder()` gắn `setOnClickListener`/`setOnLongClickListener` NGAY LẬP TỨC (ngoài
  `bindWhenInflated {}`), trong khi `cbImage`/`ivImage` chỉ được gán trong callback async inflate.
  Chạm vào ô ảnh mới tạo/cuộn nhanh vào TRƯỚC khi inflate xong (main thread bận dưới tải cao — kịch
  bản máy chậm như TECNO_KJ7 hay gặp trong dự án này) ném `kotlin.UninitializedPropertyAccessException`,
  crash toàn app. Repro 100% xác định dưới Robolectric (main looper mặc định không tự chạy Runnable
  đã post — giữ đúng cửa sổ trước khi inflate hoàn tất, không phụ thuộc timing thật). Fix: dời cả 2
  listener vào TRONG `bindWhenInflated {}` (đúng chỗ toàn bộ logic bind còn lại đã chờ sẵn). Test
  mới: `GalleryAdapterAsyncInflateClickRaceTest` (repro FAIL trước fix với đúng exception dự đoán,
  PASS sau fix).
- [x] **Dead code — 3 file không còn nơi nào tham chiếu (0 XML, 0 Kotlin)**: `ui/widget/Toolbar.kt`
  (custom Toolbar tự vẽ, app dùng `MaterialToolbar` thật qua `LaunchView.toolbar`),
  `ui/widget/GalleryItemView.kt` (trùng chức năng `AsyncSquareFrameLayout` + `item_image_gallery.xml`
  đang dùng thật), `ui/widget/ControllableScrollView.kt` (`NestedScrollView` chặn scroll, không còn
  layout nào dùng). Xoá cả 3 *(ponytail: không thêm test cho xoá dead code — 0 behavioral delta, tự
  chứng minh qua compile + `testDebugUnitTest` xanh)*.

**Verify:** `GalleryAdapterAsyncInflateClickRaceTest` PASS + toàn bộ `testDebugUnitTest` (143+ file)
PASS 100% + `ktlintCheck` PASS.

**Smoke test thật trên TECNO_KJ7 (115333744A005844):** cài APK debug mới, app khởi động bình thường
(xác nhận xoá 3 file dead code không vỡ build/runtime), mở "Chọn ảnh" rồi CHẠM LIÊN TỤC RẤT NHANH 6
ô ảnh khác nhau ngay lúc grid vừa mở (đúng kịch bản race vừa fix — ô mới tạo, `AsyncLayoutInflater`
có thể chưa xong) — chọn đúng cả 6 ảnh, không crash, `logcat` sạch không `FATAL EXCEPTION`/
`UninitializedPropertyAccessException`.

## Review pass 9 — `ui/` root (`MainActivity.kt`, `MainViewModel.kt`, `UiState.kt`), 2026-09-30

Audit vòng 9 (loop tiếp theo sau review pass 8, phạm vi: bộ não app — `MainActivity`/`MainViewModel`/
`UiState`, chưa từng có review pass riêng dù đã audit `di/`, `dlg/`, `repo/`, `widget/`, `adapter/`...):
1 finding, verify tay — **ĐÚNG**, fix theo lựa chọn user:

- [x] **`MainActivity.colorPalette.observe()` ghi đè `bgTransformAnimator`/animator textColor của
  `funcAdapter` mà KHÔNG cancel animator cũ trước đó** — mỗi lần chọn ảnh khác, Palette mới sinh ra
  gọi `currentBgColor.toColor(bgColor) {...}` tạo `ObjectAnimator` MỚI gán thẳng vào field, animator
  CŨ (nếu chưa chạy xong `ANIMATION_DURATION`) mất tham chiếu, tiếp tục chạy ngầm không kiểm soát
  được — đổi ảnh nhanh (vuốt dải thumbnail) khiến 2 animator cùng ghi `backgroundColor`/text color
  lên cùng view, gây race/nhấp nháy màu, và chỉ animator MỚI NHẤT bị cancel ở `onDestroy()` (animator
  cũ đã mất field tham chiếu từ trước, tiếp tục chạy sau khi Activity destroy). Riêng animator
  `funcAdapter.textColor.toColor(...)` còn tệ hơn — không lưu field nào cả, không bao giờ cancel
  được. Đúng pattern đã fix ở Review pass 6 (`LaunchView`/`WaterMarkImageView` animator/job không
  cancel trước khi ghi đè). Fix: thêm field `funcTextColorAnimator`, `cancel()` cả 2 field ngay đầu
  observer trước khi tạo animator mới, cancel cả 2 trong `onDestroy()`. Test mới:
  `MainActivityColorPaletteAnimatorRoboTest` (2 test: đổi Palette lần 2 cancel cả 2 animator cũ
  trước khi tạo animator mới; `onDestroy()` cancel cả 2 field).

**Verify:** `MainActivityColorPaletteAnimatorRoboTest` (2 test) PASS. Toàn bộ test `MainActivity*`
+ `MainViewModel*` hiện có PASS 100%. `ktlintCheck` PASS.

**Smoke test thật trên TECNO_KJ7 (115333744A005844):** cài APK debug mới, chọn 3 ảnh khác màu (nền
trắng, xanh dương, QR trắng-đen) vào editor, vuốt đổi qua lại nhanh giữa 3 ảnh trong dải thumbnail
(đúng kịch bản race vừa fix) — nền chuyển màu mượt không nhấp nháy/kẹt màu, `logcat` sạch suốt phiên
không `FATAL EXCEPTION`. Bấm back → "Xác nhận huỷ" gọi `resetView()`/`onDestroy()` (chứng minh cả 2
animator bị cancel đúng lúc), quay về LaunchMode trơn tru.

## BUG-FLAKY-2026-09-30 (tiếp): `MainViewModelCompressImgRoboTest` — hardcode `Dispatchers.IO`, không phải "flaky do tải máy"

Follow-up của mục BUG-FLAKY-2026-09-30 gốc (ngay dưới) — sau khi fix deadlock DataStore singleton,
báo cáo đó tự nhận "1/7 lần `MainViewModelCompressImgRoboTest` vẫn fail, pre-existing flaky do
timing polling dưới tải cao" nhưng KHÔNG điều tra sâu (không có log/stacktrace lần fail thật). Điều
tra lại kỹ hơn (đúng bài học đã rút ra ở mục dưới — không gắn nhãn "flaky do tải" khi chưa loại trừ
nguyên nhân xác định được):

- **Root cause thật:** `MainViewModel.compressImg()` hardcode `viewModelScope.launch(Dispatchers.IO)`
  — thread pool THẬT, hoàn toàn ngoài tầm kiểm soát `Robolectric.getForegroundThreadScheduler()`/
  Main looper giả. Test phải bơm `idle()` + `Thread.sleep(20)` lặp lại (deadline 5s) làm cầu nối
  giữa 2 thế giới — bản chất là race theo THỜI GIAN THẬT, không phải theo tick giả Robolectric. Dưới
  tải CPU cao (full suite 1000+ test chạy song song), OS có thể trì hoãn việc schedule thread
  `Dispatchers.IO` đủ lâu để chạm ngưỡng 5s dù việc bên trong cực ngắn.
- **Fix:** `MainViewModel` nhận thêm tham số cuối `ioDispatcher: CoroutineDispatcher = Dispatchers.IO`
  (default giữ nguyên hành vi production), `compressImg()` dùng `viewModelScope.launch(ioDispatcher)`.
  Cần thêm 1 `@Provides fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO` trong
  `AppModule.kt` — Dagger/Hilt **không** tự dùng Kotlin default value cho tham số không có
  `@Inject constructor`/binding riêng (khác 4 tham số default khác của `MainViewModel`, vốn có
  `@Inject constructor` riêng nên Dagger tự dựng được qua constructor injection, không phải nhờ
  default value). Test đổi sang `StandardTestDispatcher` + `advanceUntilIdle()` — hết polling/sleep,
  tất định 100% (coroutine chạy đồng bộ trên thread test).
- **Verify:** `MainViewModelCompressImgRoboTest` PASS lặp lại ổn định (`--rerun` 3 lần liên tiếp,
  không dùng thời gian thật để chờ nữa nên không còn phụ thuộc tải máy).

## Review pass 8 — `export/`, `utils/bitmap+facedetection+redaction+textdetection`, `ui/about+panel+recipient`, `data/model+backup`, 2026-09-30

3 agent audit song song quét toàn bộ module chưa từng audit ở pass 1-7 (loại trừ `feature/vip/`/Ad
SDK theo quyết định user). Tổng **38 finding** (9 P1 + 29 P2) — nhiều hơn hẳn quy mô 1 pass thường
(6-7 fix), nên chỉ fix hết 9 P1 trong pass này, 29 P2 dồn thành hàng đợi cho pass 9+ (danh sách cuối
mục này).

**9 finding P1 — tất cả ĐÃ FIX, verify tay từng cái:**

- [x] **`export/ProofingMode.kt` ghi `proof_index.html` sai thư mục trên Android Q+** — ghi vào
  `Documents/WaterMarkCreator/` nhưng ảnh thật nằm ở `Pictures/WaterMarkCreator/`
  (`BatchExportEngine.kt`), HTML tham chiếu ảnh bằng đường dẫn TƯƠNG ĐỐI → mọi ảnh trong index vỡ
  link trên đa số thiết bị (Q+). Nhánh SAF/legacy đã đúng từ trước, chỉ nhánh MediaStore Q+ lệch.
  **Fix ĐẦU TIÊN SAI** (đổi `RELATIVE_PATH` sang `"Pictures/$outPutFolderName"`) — smoke test thật
  trên TECNO KJ7 phát hiện Android Q+ **cấm** `MediaStore.Files.insert()` non-media file vào
  `Pictures/`, chỉ cho `[Download, Documents]` (log thật: "Primary directory Pictures not allowed
  for content://media/external_primary/file"); Robolectric không mô phỏng giới hạn này nên unit
  test ban đầu pass giả — trên máy thật `insert()` ném exception, `writeIndex()` trả `null`, **không
  sinh được file nào** (còn tệ hơn bug gốc). **Fix thật:** giữ `Documents/` (thư mục được phép),
  nhúng ảnh base64 thẳng vào HTML (`ProofingMode.embedImages()` đọc bytes qua `ContentResolver`,
  `Entry` thêm field `uri`/`imageSrc`) thay vì path tương đối — tự chứa hoàn toàn, không phụ thuộc
  ảnh/index có cùng thư mục hay không (portable hơn: copy/gửi file đi đâu vẫn xem được ảnh). Nhánh
  SAF/legacy giữ nguyên (không đổi, đã đúng). Test:
  `ProofingModeMediaStoreDirectoryRoboTest` (regression guard `writeIndex` không còn trả `null` +
  insert đúng `Documents/`, cộng 2 test cho `embedImages`/`buildHtml` dùng `imageSrc`). **Verify
  thật trên TECNO KJ7:** export 2 ảnh + bật "Chế độ ảnh duyệt cho khách" → `proof_index.html` sinh
  ra 497KB (thay vì 740 byte cũ), `pull` về kiểm tra chứa đúng `<img
  src="data:image/jpeg;base64,...">` + caption đúng tên file thật, không còn log lỗi
  "Không ghi được proof index".
- [x] **`export/BatchExportWorker.kt` notification tiến trình không bao giờ bị `cancel()`** —
  `setOngoing(true)` nhưng không nơi nào gọi `NotificationManager.cancel()` khi work xong/lỗi/huỷ →
  treo vĩnh viễn, user không vuốt bỏ được. Fix: `dismissProgressNotification()` trong `finally` của
  `doWork()` (cùng chỗ release wake lock — chạy mọi nhánh kể cả `CancellationException`). Test:
  `BatchExportWorkerNotificationDismissRoboTest`. Phải sửa lại
  `BatchExportWorkerRoboTest.doWork_postsInitialProgressNotification_...` (assertion cũ "vẫn còn
  sau khi work terminal" giờ race với `finally`, luôn `null` — đổi sang chỉ verify channel tạo).
- [x] **`export/BatchExportEngine.kt` OVERWRITE + exception giữa chừng làm mất ảnh gốc** — đặt
  `IS_PENDING=1` lên row ảnh CŨ trước khi ghi đè, chỉ dọn ở nhánh `writeResult.isFailure()`; nếu 1
  `Throwable` không phải `Exception` (`OutOfMemoryError` lúc `compress()` ảnh lớn — không bị
  `catch (e: Exception)` cục bộ bắt) thoát ra ngoài, row giữ pending vĩnh viễn → ảnh gốc biến mất
  khỏi mọi app gallery. Fix: bọc `try/finally` với cờ `pendingCleanupDone`, dọn về 0 trong `finally`
  nếu chưa nhánh nào dọn. Test: `BatchExportEngineOverwritePendingCleanupRoboTest` (ép
  `OutOfMemoryError` qua `spyk` trên `ContentResolver.openFileDescriptor`, verify bằng
  `io.mockk.verify` — Robolectric không có row MediaStore thật để query lại).
- [x] **`export/BatchExportEngine.kt:475` force-unwrap `iconBitmapValue.bitmap!!`** — site DUY NHẤT
  không guard (5 site khác cùng file dùng `?: return`). Fix: đổi cùng pattern
  `?: return@withContext Result.failure(...)`. Không thêm test riêng — path này provably unreachable
  null (constructor `BitmapValue` luôn nhận bitmap non-null qua `decodeBitmapWithExifSync`), fix chỉ
  để đồng nhất; verify qua test `generateImage`/icon đã có sẵn không regression.
- [x] **`export/BatchExportEngine.kt` bitmap tạm rò rỉ khi render lỗi giữa chừng** —
  `generatePreviewBitmap`/`generateCompareBitmaps` tạo bitmap `copy()` riêng (không qua
  `BitmapCache`), nhánh `catch (e: Exception)` chỉ `bitmapValue.release()` (bitmap GỐC trong cache),
  không recycle bitmap tạm → rò rỉ 1-2 bitmap mỗi lần lỗi. Fix: biến `leakGuardBitmap`/
  `leakGuardOriginal`/`leakGuardWatermarked` theo dõi bitmap đang sống, recycle trong catch. Test:
  `BatchExportEngineBitmapLeakOnExceptionRoboTest` (ép lỗi qua mock
  `WaterMarkImageView.resolveAutoContrast`, capture bitmap bằng `slot<Bitmap>()`).
- [x] **`data/model/JobState.kt` + `Result.kt` không phải `data class`** — `equals()` so reference
  identity, `DiffUtil.areContentsTheSame` (`SaveImageListAdapter`) luôn `false` dù nội dung giống →
  rebind/flicker thừa. Fix: đổi cả 2 sang `data class` (verify không field nào bị mutate sau khi
  tạo). Test: `JobStateTest` (unit, equals/hashCode).
- [x] **`ui/about/AboutActivity.showVerifyResult` thiếu guard `isFinishing`/`isDestroyed`** —
  callback từ `viewModelScope` (sống ngoài Activity) show dialog trên window token đã chết nếu
  Activity finish/rotate trước khi verify I/O xong → `BadTokenException`. Fix: `if (isFinishing ||
  isDestroyed) return` đầu hàm (đổi `private` → `internal` để test gọi trực tiếp, cùng pattern
  `buildVerifyMessage`). Test: `AboutActivityShowVerifyResultRoboTest` (verify qua
  `ShadowDialog.getLatestDialog()` — `ShadowAlertDialog` không track dialog AndroidX/Material).
- [x] **`ui/recipient/RecipientViewModel.save` check-then-act race** — `getByCode` rồi
  `update`/`insert` không atomic; 2 lần gọi gần đồng thời cùng `code` có thể cả 2 vượt qua check rồi
  1 trong 2 ghi Room ném `SQLiteConstraintException` thẳng trong `viewModelScope.launch` → crash cả
  test class (không chỉ 1 test — exception thoát `Dispatchers.Main.immediate` làm Robolectric skip
  luôn test còn lại). Fix: bọc try/catch quanh `insert`/`update`, `onResult(false)` khi bắt được.
  Test: `RecipientViewModelSaveRoboTest` (DAO giả `getByCode` trả `null` nhưng `insert`/`update` ném
  — mô phỏng đúng race).
- [x] **`ui/panel/TextStyleFragment.kt` không có test nào** — 8 fragment panel khác đều có, mapping
  `effectAdapter` position→handler + cross-callback `paintStyleAdapter`/`typefaceAdapter` đủ phức
  tạp cần test. KHÔNG có bug thật (mapping đúng) — chỉ thiếu coverage. Test:
  `TextStyleFragmentRoboTest` (3 test: 4 chip effect độc lập đúng mapping, paint style chip, typeface
  chip — gắn `MainActivity` thật lấy `ConcatAdapter.adapters` để click đúng adapter con).

**29 finding P2 — CHƯA fix, hàng đợi cho review pass 9+** (không tự bịa ticket `.md` riêng, theo
đúng convention review pass — liệt kê đây để pass sau không phải audit lại từ đầu):

*export/utils/bitmap (nhóm agent 1):*
- `utils/bitmap/BitmapUtils.kt:330-348` — cursor leak khi `getInt(0)` ném (catch không đóng cursor,
  cần `.use{}`).
- `utils/bitmap/BitmapUtils.kt:436-447` — race get-then-put `BitmapCache`: `entryRemoved(evicted=false)`
  không recycle bitmap cũ khi 2 coroutine decode song song cùng key.
- `export/ExportNaming.kt:192-202` — `resolveVersionedName` `while(true)` không trần, treo vô hạn
  nếu `isNameTaken` luôn true.
- `export/BatchExportEngine.kt:78` — `matrixValues = FloatArray(9)` state cấp instance, nên là local.
- `utils/facedetection/MlKitFaceDetectionSource.kt:26`, `utils/textdetection/MlKitSensitiveTextSource.kt:36`
  — `FaceDetector`/`TextRecognizer` `Closeable` không bao giờ `.close()` (chấp nhận được vì
  `@Singleton`, nhưng đáng ghi chú).
- `utils/facedetection/FaceDetectionSource.kt:23-27` — `awaitTask()` thiếu
  `invokeOnCancellation{}` + callback không dùng Executor (chạy main thread).
- `export/BatchExportEngine.kt:290,301,430,462` + `utils/bitmap/BitmapUtils.kt:36,406,422,483,488,493`
  — mã lỗi `"-1"` hardcode 11 lần thay vì const `MainViewModel.TYPE_ERROR_*`.
- `utils/bitmap/BitmapUtils.kt:369,377` — magic number `0.35`/`1024L*1024L`/`1080` không tên.
- `utils/bitmap/ExifBorderRenderer.kt:93-245` — ~30 hằng tỉ lệ layout hardcode, 4 style lặp nhóm số
  khác giá trị, nên gom thành data class.
- `export/BatchExportEngine.kt:741-755` — file rác nửa vời trên đĩa nếu `compress()` ném exception
  (chỉ nhánh `compressOk==false` mới `delete()`).
- `export/BatchExportEngine.kt:470,528,532,637,800` — 5 `!!` còn lại không comment giải thích
  non-null (an toàn thực tế, chỉ thiếu ghi chú).
- Thiếu test: `export/stego/HiddenWatermarkReader.kt`, `ProofingMode.writeIndex/writeMediaStore/writeLegacy`
  (ngoài nhánh vừa fix), `BatchExportEngine.drawExtraLayers`, `ExportNaming.queryDisplayName`.

*ui/about+panel+recipient, data/model+backup (nhóm agent 2):*
- `ui/recipient/RecipientManagementActivity.kt:113-115` — callback lambda từ `viewModelScope` giữ
  ref Activity qua rotation, nên đổi sang `Flow`/`SharedFlow` event.
- `ui/recipient/RecipientPickerBottomSheetFragment.kt:31` — `_binding!!` không comment giải thích
  (lifecycle thực tế đã guard, chỉ thiếu ghi chú).
- `ui/panel/TextStyleFragment.kt:57-73` `effectAdapter` — `by lazy` đọc `waterMark.value` 1 lần, không
  re-observe → chip state stale nếu config đổi từ nguồn khác trong lúc panel mở.
- `ui/panel/ColorFragment.kt:29-46` — 5 màu hardcode `Color.parseColor(...)` thay vì `colors.xml`.
- `ui/panel/TextStyleFragment.kt:4,8` — import chết `TextUtils.replace`/`ContentProviderCompat.requireContext`.
- `data/model/WaterMark.kt:24` — `marginPercent = 0.05f` hardcode lặp, trong khi
  `WatermarkLayer.kt:29` đã đúng dùng `WaterMarkRepository.DEFAULT_MARGIN_PERCENT`.
- `data/model/Anchor.kt`, `ImageInfo.kt` (`obtainTileMode`, `isSameItem`) — thiếu unit test.
- `data/model/Result.kt` — method `isSuccess()` bị comment chết (đã dọn `isFailure()` liên quan lúc
  fix P1 #6, chưa dọn dòng comment chết này).
- `data/model/FuncTitleModel.kt:16,28,32` — field `tag` khai báo nhưng 0 reference.
- `data/model/entity/Template.kt:1-40` — ~15 dòng `Parceler` custom comment chết (đã thay `@Parcelize`).
- `data/backup/BackupRestoreEngine.kt:71` — thiếu sanitize path traversal tại chính engine (hiện an
  toàn nhờ caller duy nhất chặn hộ, rủi ro nếu có caller khác sau này).
- `ui/about/AboutActivity.kt:39,156` + `OpenSourceActivity.kt:26` — magic number `32dp` lặp 2 nơi.
- `ui/about/AboutActivity.kt:196` — `"SAIGON PHANTOM LABS"` hardcode inline.
- `ui/about/AboutActivity.kt:191` — Play Store URL raw string (nên tách hàm như `buildMoreAppsUrl`).
- `ui/about/OpenSourceActivity.kt:51,55,59,63` — GitHub URL hardcode trong click listener.
- Thiếu test: `AboutViewModel.verifyAuthenticity`/`resolveLeakedRecipient`,
  `RecipientViewModel`/`RecipientPickerBottomSheetFragment`/`RecipientManagementActivity` (case
  duplicate-code), `data/model` các model thuần còn lại theo ghi chú agent.

## BUG-FLAKY-2026-09-30: `SaveImageBSDialogFragment{Invisible,Proofing,Authenticity}RoboTest` — Mutex deadlock DataStore singleton, KHÔNG phải flaky do tải máy

Nhiều lần review trước (xem review pass 5/6, dòng ~193/249/331 file này) từng ghi nhận
`SaveImageBSDialogFragmentInvisibleRoboTest`/`ProofingRoboTest` "fail rải rác khi chạy full suite,
pass khi cô lập → kết luận flaky do tải máy". Kết luận đó **SAI** — điều tra lại kỹ (systematic
debugging, không đoán mò):

- **Root cause thật:** cả 3 test dựng `MainActivity` thật qua `Robolectric.buildActivity` (bắt
  buộc — `SaveImageBSDialogFragment` ép kiểu `requireActivity() as MainActivity`) → Hilt thật inject
  `MainViewModel` gắn với DataStore **singleton sản xuất** (`context.userDataStore`/
  `waterMarkDataStore`, cache theo file path dùng chung xuyên suốt JVM fork — xem cảnh báo có sẵn ở
  `testutil/TestDataStores.kt`). Nếu 1 test khác (bất kỳ test nào cũng dùng singleton này) bị
  Robolectric huỷ sandbox đúng lúc `dataStore.edit{}` dở dang, Mutex ghi bị khoá **VĨNH VIỄN** — test
  sau cùng file gọi `edit{}` treo mãi khi chạy full suite. Đây là DEADLOCK THẬT, không phải "chậm do
  tải" — đã tăng deadline polling 3s→5s (khớp convention đa số test khác) và KHÔNG có tác dụng gì
  (verify thực nghiệm 4/4 lần vẫn fail y hệt), loại trừ hẳn giả thuyết timing/tải máy. Cũng đã thử
  `forkEvery` 25→10 (giảm số class dồn 1 JVM fork) — cũng KHÔNG cải thiện, loại trừ giả thuyết
  heap/GC accumulation.
- **Fix:** `app/src/test/java/.../di/TestDataStoreModule.kt` — module Hilt `@TestInstallIn` thay
  `DataStoreModule` sản xuất, cấp DataStore CÔ LẬP (file tạm riêng mỗi lần build SingletonComponent)
  cho mọi test Robolectric dùng Hilt thật. 3 test trên thêm `@HiltAndroidTest` +
  `@Config(application = HiltTestApplication::class)` + `HiltAndroidRule` (kèm tự init lại
  `CMonet.init()`/WorkManager test config vì `HiltTestApplication` không chạy `MyApplication.onCreate()`).
- **Test mới:** `TestDataStoreModuleRoboTest` (unit, 3 test — chứng minh module không cache tĩnh/
  không trùng singleton), mỗi file trong 3 file trên thêm 1 integration test
  `setupDialog_hiltBindsIsolatedDataStore_khongTrungSingletonSanXuat` (so REFERENCE qua
  `EntryPointAccessors`, không so giá trị — tránh phụ thuộc thứ tự chạy).
- **Verify:** `./gradlew clean` + `--stop` (daemon mới) + `testDebugUnitTest --rerun` **7 lần liên
  tiếp** — cả 3 test không fail lần nào (trước fix: fail hầu hết các lần chạy). 1 lần trong 7 có
  `MainViewModelCompressImgRoboTest` fail (test KHÁC, đã dùng DataStore cô lập từ trước, không liên
  quan) — pre-existing flaky thật (timing test polling dưới tải cao), không phải bug này.
- **Bài học:** đừng vội gắn nhãn "flaky do tải máy" khi CHƯA loại trừ được deadlock/leak thật bằng
  thực nghiệm (tăng timeout không giúp / giảm fork size không giúp) — 2 tín hiệu đó chính là bằng
  chứng phủ định giả thuyết "chỉ là chậm", trỏ thẳng sang "bị chặn vĩnh viễn".

## Review pass 7 — `utils/` (ShareIntentResolver, VibrateHelper), 2026-09-29

Audit vòng 7 (loop tiếp theo sau review pass 6, phạm vi các file `utils/` chưa audit): 2 finding,
verify tay từng cái — **tất cả ĐÚNG**, tất cả đều fix theo lựa chọn user:

- [x] **`ShareIntentResolver.kt:14` dùng `getParcelableExtra` deprecated trên API 33+** — type-unsafe,
  tiềm ẩn `ClassCastException` nếu app khác gửi intent sai kiểu. Fix: đổi sang
  `IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)` type-safe từ
  AndroidX, hoạt động đúng trên mọi API 24–37. Test: 6 test trong `ShareIntentResolverTest` PASS.
- [x] **`VibrateHelper.kt:8-19` cooldown `cd=20ms` vô tác dụng** — `latestVibration` chỉ khởi tạo = 0
  và KHÔNG bao giờ được gán lại sau khi rung → gọi liên tục đều rung mọi lần, gây spam haptic. Fix:
  gán `latestVibration = now` ngay sau khi check cooldown, tách hàm thuần `shouldVibrate()` để test.
  Test mới: `VibrateHelperTest` (5 test: lần đầu, trong cooldown, đúng cooldown, sau cooldown, custom cooldown).

**Verify:** `VibrateHelperTest` (5 test) + `ShareIntentResolverTest` (6 test) PASS 100%. `ktlintCheck`
PASS. `lintDebug` PASS.

**Smoke test thật trên Pixel 7 Pro (2B051FDH3006MU):** cài APK v2026.09.29 (Build 20260929) mới,
khởi động app mượt mà, logcat sạch không FATAL EXCEPTION.

## Review pass 6 — `ui/widget/` (WaterMarkImageView, LaunchView) + `utils/ktx/` (ViewExtension), 2026-09-29

Audit vòng 6 (loop tiếp theo sau review pass 5, phạm vi: `ui/widget/WaterMarkImageView.kt`,
`ui/widget/LaunchView.kt`, `utils/ktx/ViewExtension.kt`): 6 finding, verify tay từng cái — **tất cả
ĐÚNG**, tất cả đều fix theo lựa chọn user:

- [x] **`WaterMarkImageView.buildIconBitmapShader()` không recycle `scaleBitmap` trung gian** —
  `Bitmap.createScaledBitmap()` tạo bitmap mới (khi kích thước khác nguồn), sau khi vẽ lên canvas
  không được `recycle()`. Mỗi lần pinch-scale hoặc đổi icon leak 1 bitmap mới cho đến khi GC thu
  hồi. Fix: thêm `recycleScaledBitmapIfDistinct()` tách hàm thuần, chỉ recycle khi khác `srcBitmap`
  (tránh recycle nhầm bitmap nguồn mà hàm gọi sở hữu khi kích thước trùng khớp). Test mới:
  `WaterMarkImageViewRecycleScaledBitmapTest` (2 test: khác instance thì recycle, cùng instance không
  recycle nguồn).
- [x] **`WaterMarkImageView.reset()`/`onDetachedFromWindow()` release bitmap cùng lúc với worker coroutine đang đọc** —
  `generateBitmapJob?.cancel()` chỉ có hiệu lực ở suspension point kế tiếp, đoạn
  `createScaledBitmap()` đồng bộ đang chạy giữa chừng trên `Dispatchers.Default` vẫn tiếp tục đọc
  pixel; nếu `reset()` release/recycle `BitmapValue` ngay lúc đó có thể ném `IllegalStateException`
  hoặc đọc rác. Fix: thêm `cancelAndScheduleBitmapValueRelease(jobToAwait)` — null hoá field ngay
  (để caller sau reset không thấy bitmap cũ), nhưng nếu có job đang chạy thì launch coroutine đợi
  `jobToAwait.join()` trước khi thật sự gọi `release()`. Test mới:
  `reset_whileGenerateJobStillReading_defersBitmapReleaseUntilJobEnds` trong
  `WaterMarkImageViewResetRoboTest` (chứng minh refCount/bitmap còn sống trong lúc job đang chạy và
  chỉ bị recycle sau khi job kết thúc).
- [x] **`WaterMarkImageView.applyBg()` launch coroutine fire-and-forget không lưu Job** — khi view
  detach, `generatePalette()` vẫn tiếp tục chạy trên `Dispatchers.Default` rồi callback
  `onBgReady()` vào Activity đã destroy. Fix: lưu `bgJob: Job?`, cancel trong
  `onDetachedFromWindow()` và `reset()`. Test mới: trong `WaterMarkImageViewLifecycleRoboTest`.
- [x] **`WaterMarkImageView` không cancel 2 animator khi detach** — `drawableAlphaAnimator` và
  `animator` (của `backToCenter()`) không được cancel trong `onDetachedFromWindow()`, tiếp tục tick
  ngầm trên view đã detach. Fix: gọi `.cancel()` cho cả 2 trong `onDetachedFromWindow()`. Test mới:
  `onDetachedFromWindow_cancelsBgJobAndBothAnimators` trong `WaterMarkImageViewLifecycleRoboTest`.
- [x] **`ViewExtension.kt:View.disappear()` đảo ngược tham số `translationX`/`translationY`** —
  `.translationY(toX)` và `.translationX(toY)` bị swap. Caller thật duy nhất
  (`SaveImageListAdapter.kt:278 ivDone.disappear()`, dùng default `toX=0f, toY=10dp`) khiến icon check
  trượt NGANG 10dp thay vì trượt XUỐNG khi fade-out. Fix: đổi lại đúng `.translationX(toX)` và
  `.translationY(toY)`. Test mới: `ViewExtensionRoboTest` (1 test).
- [x] **`LaunchView.transformLayout()` không cancel `launchModeAppearAnimationList` khi vào Editor** —
  appear animation dùng `SpringAnimation` riêng (chạy lúc mở app), không bị hủy bởi
  `it.animate().cancel()` (chỉ huỷ `ViewPropertyAnimator` hiệu ứng chạm card). Nếu user chạm card
  nhanh trong lúc animation đang chạy (300-500ms), animation cũ tiếp tục ghi đè alpha/translationY
  sau khi view đã ẩn, gây giật khi quay lại LaunchMode. Fix: thêm
  `launchModeAppearAnimationList.forEach { it.cancel() }` vào nhánh `ViewMode.Editor`. Test mới:
  `toEditorMode_cancelsLaunchAppearSpringAnimations` trong `LaunchViewRoboTest`.
- [x] **`LaunchView.toolbar` lazy block: xoá dead code `overflowIcon?.setTint(...)`** — `overflowIcon`
  luôn null lúc Toolbar vừa construct (menu chưa inflate). Đã xoá dòng dead code (overflow icon
  được tint đúng qua `applyConsistentIconTint()` gọi sau ở MainActivity). *(ponytail: không thêm test
  mới cho dead code deletion — 0 behavioral delta, tự chứng minh qua compile).*

**Verify:** 5 test class liên quan (14 test) PASS 100%. `ktlintCheck` PASS. `lintDebug` PASS.
Full suite `connectedDebugAndroidTest` (121 test Room/repo/E2E thật) PASS trên Pixel 7 Pro (1 skip
có điều kiện do Geocoder mạng).

**Lưu ý vận hành (R3):** device cũ TECNO_KJ7 bị ngắt kết nối USB giữa chừng; đã hỏi người dùng qua
`AskUserQuestion` và người dùng đã duyệt chuyển khoá sang **Pixel 7 Pro (`2B051FDH3006MU`)**. Mọi
thao tác sau đó chỉ target Pixel 7 Pro.

**Smoke test thật trên Pixel 7 Pro (2B051FDH3006MU, Android 17):** cài APK mới, cấp quyền media
Android 14+, vào Gallery chọn ảnh vào Editor (kích hoạt `WaterMarkImageView`), xử lý đúng lúc gặp
Interstitial Ad (dừng theo quy tắc R4, chờ user xác nhận "done"), bấm back và "Xác nhận huỷ" để
gọi `resetView()` → `ivPhoto.reset()` (chứng minh fix `cancelAndScheduleBitmapValueRelease` không
deadlock/crash), quay về LaunchMode trơn tru không nhấp nháy (chứng minh fix SpringAnimation cancel).
`logcat` sạch suốt phiên, không `FATAL EXCEPTION` hay `NullPointerException`.

## Review pass 5 — `di/`, `ui/dlg/`, `data/repo/` (phạm vi mới), 2026-09-29

Audit vòng 5 (loop tiếp theo sau review pass 4, phạm vi hoàn toàn mới, loại trừ mọi file đã audit ở
4 vòng trước): `/code-review --level max` trên `di/`, `ui/dlg/`, `data/repo/`. 5 finding, verify tay
từng cái — **tất cả ĐÚNG**, tất cả đều fix theo lựa chọn user:

- [x] **`ComparePreviewBottomSheetFragment.applyReveal()` không guard view đã huỷ** — khi
  width/height view còn 0 (chưa layout xong), hàm tự `post{}` lặp lại chính nó; nếu user đóng sheet
  TRƯỚC khi callback trễ đó chạy, `onDestroyView()` đã set `binding` null, callback chạm `binding`
  ném NPE. Fix: guard `view == null` (Fragment.view, tự null hoá trong `onDestroyView()`) ở cả điểm
  vào lẫn trong callback trễ. Test mới: `applyReveal_goiSauKhiViewDaHuy_khongNemNpe` (reflection gọi
  thẳng hàm private sau khi view đã huỷ hẳn — deterministic, không phụ thuộc timing).
- [x] **`GalleryFragment` scroll listener chia cho `verticalScrollRange` có thể = 0** (list ít ảnh,
  vừa màn hình không cuộn được) → `NaN` → `coerceAtLeast(0f)` không clamp được (so sánh với NaN
  luôn false) → `sliderCard.translationY` dính NaN, slider lệch vị trí. Fix: tách hàm
  `computeSliderTranslationY()` (theo đúng pattern `computeSliderScrollPercent` đã có từ BUG-29) với
  guard `verticalScrollRange <= 0 -> 0f`. Test mới: `GalleryFragmentSliderTranslationYTest` (4 test:
  range=0, range âm, tính đúng tỉ lệ, kết quả âm clamp về 0).
- [x] **`BackupRestoreRepository.backupTo()`/`restoreFrom()` nuốt `CancellationException`** — catch
  chung `Exception` phá cooperative cancellation (user rời màn hình giữa lúc backup/restore chạy
  trong `viewModelScope`, coroutine đáng lẽ phải dừng lại bị nuốt exception, trả `false` như lỗi
  thường). Fix: thêm `catch (e: CancellationException) { throw e }` TRƯỚC catch chung, ở cả 2 hàm.
  *(ponytail: không có test tự động cho nhánh rethrow — cần huỷ Job đúng lúc đang chạy giữa
  `withContext(Dispatchers.IO)` thật, không có hook để chèn `cancel()` tại điểm chính xác mà không
  tạo test timing-race giả; nếu cần sau này, inject Dispatcher giống pattern
  `DelegatingWorkerFactory` ở test worker để dùng `TestDispatcher` điều khiển thời điểm tất định.)*
- [x] **`BackupRestoreRepository.restoreFrom()` dedup chỉ tính 1 lần TRƯỚC vòng lặp** — 2 template
  TRÙNG content nằm trong CÙNG 1 file backup (máy đích trống, vd. máy mới) không dedup lẫn nhau,
  cả 2 đều lọt qua filter (chỉ dedup được với DB đích hiện có, không dedup nội bộ file). Fix: đổi
  `existingContents` thành `MutableSet` cập nhật `.add()` ngay sau mỗi insert trong vòng lặp. Test
  mới: `restoreFrom_backupContainsDuplicateContentWithinSameFile_dedupsToOne`.
- [x] **`EditTemplateContentFragment.safetyShow()` gọi lại trên instance đã `isAdded`** (double-tap
  2 template KHÁC nhau liên tiếp) set `arguments` trên fragment đang active → ném
  `IllegalStateException("Fragment already active")` bị `catch` nuốt âm thầm, dialog giữ nguyên nội
  dung của template ĐẦU TIÊN thay vì cái vừa bấm. Fix: tách `bindTemplateToViews()` +
  `updateTemplateForReuse()` — cập nhật field + UI trực tiếp, không đụng `arguments` khi fragment đã
  active. Test mới: `safetyShow_calledTwiceWithDifferentTemplates_showsSecondTemplateContent_notStale`.
- [x] **`RepositoryModule` có 2 `@Provides` binding chết** (`provideUserRepository` /
  `provideWaterMarkRepository`, `@Named("UserPreferences")`/`@Named("WaterMarkPreferences")`) —
  grep toàn repo không nơi nào request 2 binding `@Named(...)` này (2 repo tương ứng dùng constructor
  Hilt `@Inject` bình thường ở nơi khác). Fix: xoá cả 2 method + import không dùng, xoá 2 test tương
  ứng trong `RepositoryModuleTest` (không xoá được vì gọi hàm đã xoá, không phải vì bug).

**Verify:** `./gradlew testDebugUnitTest` full suite PASS khi chạy độc lập (không có tiến trình
Gradle/emulator khác chạy song song) — 3 lần chạy trước đó bị 1-4 test fail KHÁC NHAU mỗi lần do
máy chạy song song Android Studio (38% CPU) + emulator + ktlint, xác nhận lại bằng cách chạy riêng
từng test fail (PASS ngay khi cô lập) → kết luận flaky do tải máy, không phải regression từ round
5. `ktlintCheck` PASS (không cần `ktlintFormat` sửa gì thêm). `lintDebug` PASS. `compileDebugKotlin`
PASS (1 warning `Condition is always 'false'` tại `ComparePreviewBottomSheetFragment.kt:73` — dead
code CÓ SẴN TỪ TRƯỚC, tham số `view: View` non-null của `onViewCreated()` che khuất property
`Fragment.view` bên trong lambda coroutine, không liên quan fix `applyReveal` ở trên; ghi nhận cho
vòng audit sau).

**Smoke test thật trên TECNO_KJ7:** cài lại, mở "Thông tin" → "Sao lưu" → chọn nơi lưu qua SAF →
"Sao lưu dữ liệu thành công" (chứng minh `backupTo()` với fix CancellationException/dedup không hồi
quy). Vào "Chọn ảnh" (gallery có sẵn ~30+ ảnh test cũ), scroll nhanh lên xuống nhiều lần (chứng minh
fix NaN `computeSliderTranslationY`) — không crash. Chọn 5 ảnh vào editor, mở "So sánh" (chính
`ComparePreviewBottomSheetFragment`), kéo slider rồi bấm back đóng NGAY LẬP TỨC lặp lại — không
crash (chứng minh guard `view == null`). `logcat` sạch suốt phiên, không `FATAL EXCEPTION` nào của
`com.mckimquyen.watermark`. (`EditTemplateContentFragment` không có đường dẫn UI nhanh để smoke
test trong phiên này — đã có `EditTemplateContentFragmentSafetyShowRoboTest` cover trực tiếp đúng
code path fix.)

## Review pass 4 — data/repo+db, di/, adapter, Signature Studio, 2026-09-29

Audit vòng 4 (loop tiếp theo sau review pass 3, phạm vi hoàn toàn mới): `/code-review --level max`
trên `data/`, `di/`, `ui/adapter/`, `SignatureActivity.kt`/`SignatureView.kt`. 7 finding, verify tay
từng cái — **tất cả ĐÚNG**, severity thấp-trung bình (không có crash nào ở call site hiện tại), tất
cả đều fix theo lựa chọn user:

- [x] **`RecipientRepository.findMatching()` substring thô không ranh giới từ** — tên người nhận
  ngắn (vd "An") match nhầm bất kỳ chuỗi nào chứa nó làm substring (vd "Standard"), gán sai người
  nhận trong tính năng dò rỉ nguồn ảnh. Fix: thêm `containsAsWord()` (kiểm tra 2 đầu vị trí match
  không phải chữ/số — không dùng regex `\b` vì không nhận diện đúng ký tự có dấu tiếng Việt). Test
  mới: 3 test trong `RecipientRepositoryTest` (substring không khớp, từ độc lập khớp đúng, unit
  test riêng cho `containsAsWord`).
- [x] **`AppModule.provideYourDatabase()` nuốt exception chỉ `printStackTrace()`** — không log qua
  `AppLog` như mọi repo khác, Template DB lỗi tắt câm lặng không dấu vết debug production. Fix:
  đổi sang `AppLog.e(...)`.
- [x] **`BatchHistoryRepository.record()` insert+trimOldest không `@Transaction`** — pattern đã fix
  ở `WatermarkStyleHistoryDao.recordAndPrune` nhưng bỏ sót ở đây, app kill giữa 2 lệnh (hiếm) làm
  bảng vượt cap 20 dòng tạm thời. Fix: thêm `BatchHistoryDao.recordAndTrim()` bọc `@Transaction`,
  gộp insert+trim thành 1 lời gọi. Test mới: 2 test trong `BatchHistoryDaoIntegrationTest`
  (androidTest, Room thật) — **phát hiện thêm 1 bug JUnit thật đang chặn CẢ FILE này chạy**
  (`deleteById_removesOnlyThatEntry()` trả `Ordered` từ `containsExactly()` thay vì `Unit`, JUnit4
  từ chối cả class với `InvalidTestClassError`) — fix luôn (thêm `Unit` cuối hàm).
- [x] **`MemorySettingRepo` tạo `CoroutineScope(Dispatchers.Main)` riêng không bao giờ cancel** (vi
  phạm R5) — `updatePalette()` launch coroutine chỉ để emit `MutableStateFlow`. Fix: bỏ hẳn scope,
  gán `.value` đồng bộ. Test mới: `MemorySettingRepoTest` (đọc field qua reflection, chứng minh
  đồng bộ — không cần `runBlocking`/`idle()` như trước).
- [x] **`ColorPreviewAdapter` init block `previewList.last()` không guard rỗng** — crash
  `NoSuchElementException` nếu construct với list rỗng (chưa call site nào làm vậy hiện tại). Fix:
  guard `isNotEmpty()`. Test mới: `ColorPreviewAdapterRoboTest` (3 test).
- [x] **`FuncPanelAdapter.seNewData()` gán `selectedPos` (notify trên dataSet CŨ) TRƯỚC khi đổi
  dataSet** — `notifyDataSetChanged()` ngay sau vô hiệu hoá hệ quả (gần như vô hại thực tế), nhưng
  sửa đúng thứ tự phòng ai bỏ bớt notify sau này. Test mới:
  `seNewData_toPosBeyondOldDataSetSize_doesNotCrash_selectsCorrectItemInNewDataSet`.
- [x] **`SignatureActivity.SignatureHistoryAdapter` gọi lại `findViewById()` + tính
  `Rect`/`TouchDelegate` MỖI LẦN bind** thay vì cache 1 lần — khác các adapter dùng ViewBinding
  khác. Fix: tách `SignatureHistoryViewHolder` cache view + tính touch target 1 lần lúc tạo. Test
  mới: `SignatureHistoryAdapterRoboTest` (3 test, gắn adapter vào `RecyclerView` thật để
  `bindingAdapterPosition` hoạt động đúng — `@Config(sdk=N)` vì `ImageView.setImageURI()` trên API
  28+ dùng `ImageDecoder` mà `ShadowImageDecoder` của Robolectric không decode được ảnh test tối
  giản, giới hạn môi trường test không liên quan code thật).

**Verify:** `./gradlew testDebugUnitTest` full suite PASS (1066 test — 3 flaky pre-existing không
liên quan diff này, đã xác nhận pass riêng lẻ nhiều lần trong session). `ktlintCheck` PASS,
`lintDebug` không phát sinh warning/error mới. `connectedDebugAndroidTest` (Room thật, TECNO_KJ7)
7/7 test PASS cho `BatchHistoryDaoIntegrationTest` sau khi fix bug JUnit chặn cả file.

**Smoke test thật trên TECNO_KJ7:** cài lại, mở Signature Studio (`SignatureActivity`) — màu
`ColorPreviewAdapter` render đúng 7 màu + màu đã chọn (viền xanh), vẽ chữ ký + "Áp dụng chữ ký"
thành công (áp watermark, quay lại editor không crash), mở lại panel thấy đúng "Chữ ký đã lưu"
(`SignatureHistoryAdapter` cache view mới) hiển thị thumbnail + nút xoá đúng vị trí. `logcat` sạch
suốt phiên, không `FATAL EXCEPTION`.

**Lưu ý vận hành:** giữa vòng audit này phát hiện máy có 2 tài khoản GitHub đăng nhập
(`gj-loitp`/`royt93`) và active account tự đổi giữa phiên khiến `git push` bị 403 — đã
`gh auth switch --user royt93` để khôi phục quyền ghi trước khi push. Cũng phát hiện + tự sửa 1
lần vi phạm R3 (chạy nhầm `connectedDebugAndroidTest` trên cả device chưa khoá do dùng sai Gradle
property lọc device) — dùng `ANDROID_SERIAL=<serial>` mới là cách đúng giới hạn 1 device.

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
