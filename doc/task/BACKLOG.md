# Task Backlog — EasyWatermark

> Sinh ngày 2026-09-04. **Re-audit 2026-09-10**: đọc lại toàn bộ `app/src/main` + `cmonet/src/main`, đối chiếu 5 commit mới từ ngày sinh backlog (Frame presets EXIF border, Share App, chip token, AdMob debug/release, preview token — xem `doc/feat.md`), cross-check 4 nguồn độc lập: **Claude fork nội bộ** (đọc trực tiếp), **codex exec** (OpenAI Codex, sandbox read-only), **claude -p** (session riêng, allowlist Read/Grep/Glob), **agy** (timeout 2 lần sau ~10 phút, không có kết quả — bỏ qua, 3/4 nguồn còn lại đủ đồng thuận). Mỗi finding quan trọng đã verify lại bằng đọc trực tiếp source, không chỉ tin theo báo cáo AI.

## Quy ước

- Mỗi ticket là 1 file `.md` trong `doc/task/todo/` → di chuyển sang `doc/task/inprogress/` khi bắt đầu làm, `doc/task/done/` khi xong (đổi trạng thái = di chuyển file).
- Prefix: `BUG-` (lỗi cần fix), `ENH-` (cải tiến tính năng có sẵn), `FEAT-` (tính năng mới thực dụng), `IDEA-` (tính năng độc quyền/đột phá, effort cao, để tham khảo định hướng dài hạn).
- `priority`: P0 (crash/mất dữ liệu/bảo mật/doanh thu, core feature) > P1 (leak/perf/crash edge-case) > P2 (nhỏ, tối ưu).
- `effort`: XS (<2h) / S (nửa ngày) / M (1-2 ngày) / L (3-5 ngày) / XL (>1 tuần, cần thiết kế riêng).
- `sources`: agent nào tìm ra/đồng thuận — độ đồng thuận cao = độ tin cậy cao.
- **Prompt loop:** mỗi ticket trong `todo/` có section "## Prompt loop" trỏ tới [PROMPT_TEMPLATE.md](PROMPT_TEMPLATE.md) — Definition of Done dùng chung (audit >9/10 + unit/widget/integration test đủ mọi case + smoke test thật trên device đã khoá → mới được move `done/` + push).

## BUGS_TO_FIX (6 todo, 2 deferred + 30 done) — ưu tiên P0 trước

> **Re-audit 2026-09-16** (xem `## Re-audit 2026-09-16` cuối file) tìm thêm 1 finding P0 thật NGOÀI danh sách dưới — **`app/keystore.jks` + `gradle.properties` (chứa password ký release plaintext) đang commit vào git, cả 2 remote GitHub đều PUBLIC** — user đã xác nhận biết và sẽ tự xử lý riêng (không phải task thường, không tạo ticket .md — đây là sự cố bảo mật cần quyết định business, không phải code fix qua `/loop`).

| ID | Priority | Effort | Tiêu đề |
|---|---|---|---|
| [BUG-14](todo/BUG-14-vip-secret-hardcode-trong-apk.md) | P0 | M | VIP secret hardcode base64, lặp 3 chỗ + 1 secret thứ 2 chưa từng ticket hoá (mở rộng 2026-09-10) — **deferred, xem ghi chú cuối file** |
| [BUG-15](todo/BUG-15-admob-rewarded-release-dung-test-id.md) | P0 | XS | `ADMOB_REWARDED_ID` build release vẫn dùng ID test — mất doanh thu, vi phạm chính sách AdMob — **deferred, xem ghi chú cuối file** |
| [BUG-28](todo/BUG-28-watermarkimageviewreset-khong-release-bitmap-refcount-nhu-on.md) | P2 | S | `WaterMarkImageView.reset()` không release bitmap refcount như `onDetachedFromWindow()` |
| [BUG-32](todo/BUG-32-ten-file-export-vua-go-co-the-chua-duoc-luu-neu-bam-export-n.md) | P2 | XS | Tên file export vừa gõ có thể chưa được lưu nếu bấm Export ngay khi ô tên còn đang focus |
| [BUG-33](todo/BUG-33-nut-mo-galleryshare-sau-batch-xu-ly-sai-khi-co-anh-loi-trong.md) | P2 | S | Nút mở Gallery/Share sau batch xử lý sai khi có ảnh lỗi trong danh sách |
| [BUG-35](todo/BUG-35-chon-thu-muc-saf-co-the-crash-neu-he-thong-tu-choi-persist-p.md) | P2 | XS | Chọn thư mục SAF có thể crash nếu hệ thống từ chối persist permission |

**Đã DONE** (xem `doc/task/done/`): BUG-01 (inSampleSize/rotation), BUG-02 (BitmapCache NPE), BUG-03 (batch export báo thành công giả), BUG-04 (contentResolver insert force-unwrap), BUG-05 (OOM batch export — *lưu ý: BUG-21 phát hiện phần còn sót*), BUG-06 (icon cache race leak), BUG-07 (text shader indexOf + kích thước âm), BUG-08 (postDelayed interstitial không huỷ), BUG-09 (ACTION_SEND thiếu EXTRA_STREAM), BUG-10 (GalleryFragment observe sai lifecycle), BUG-11 (compressImg guard rỗng + leak file tạm), BUG-12 (removeImage crash IndexOutOfBounds), BUG-13 (QR debounce + off Main thread), BUG-16 (literal "null" trong Edit watermark), BUG-17 (FilmStrip coerceAtLeast — hoá ra đã fix kèm FEAT-14, chỉ thiếu test+ticket), BUG-18 (ExifPbFragment lazy button leak), BUG-19 (MediaStore ghi thất bại không guard), BUG-20 (EditTextContentFragment collect theo viewLifecycleOwner), BUG-21 (generateImage early-return không recycle) — **sprint P2 2026-09-11, xem `## Sprint 2026-09-11` cuối file**. Thêm BUG-22 (SaveImageBSDialogFragment tràn viewport màn hình nhỏ — phát hiện ngoài kế hoạch gốc, fix cùng ngày). **BUG-23 2026-09-16** (xem `doc/task/done/BUG-23-...md`): CMonet chưa init ở recovery mode — root cause fix bảo vệ ~30 điểm gọi cùng lúc (không chỉ AboutActivity như mô tả gốc); phát hiện phụ qua smoke test: màn Recovery Mode thiếu xử lý edge-to-edge, đã fix kèm. **BUG-24 2026-09-16** (xem `doc/task/done/BUG-24-...md`): `EditTemplateContentFragment.safetyShow()` ép kiểu sai lớp (`as? SaveImageBSDialogFragment` thay vì chính lớp nó) khiến guard chống trùng dialog vô hiệu; test mới tự-verify bằng cách revert fix để chứng minh bắt được bug; smoke test double-tap trên Samsung Galaxy S24 Ultra (đổi device giữa chừng session theo yêu cầu user, không dùng Tecno). **BUG-25 2026-09-16** (xem `doc/task/done/BUG-25-...md`): `TextPaintStyle`/`TextTypeface.obtainSealedClass()` throw exception SAU `.catch` trong `WaterMarkRepository.waterMark` Flow — đổi sang fallback default; Robolectric integration test tái hiện đúng kịch bản ordinal hỏng qua DataStore thật (đã verify bắt được bug bằng revert); smoke test golden-path (cycle style/typeface chip) trên OPPO CPH1989 (đổi device lần 2 trong session, "dùng oppo"). **BUG-26 2026-09-16** (xem `doc/task/done/BUG-26-...md`): `TemplateRepository` không bảo vệ exception xảy ra khi Room lazy-mở asset DB LẦN QUERY ĐẦU (không phải lúc `build()`) — thêm `.catch` cho `getAllTemplate()` + try/catch cho 3 hàm ghi; test dùng fake `TemplateDao` ném exception (đã verify bắt được bug bằng revert); smoke test golden-path Template List load thật trên OPPO, không xác nhận trực quan được bước `insertTemplate()` qua UI do bottom-sheet lồng bàn phím che khuất (đã ghi rõ giới hạn trong ticket). **BUG-31 2026-09-16** (xem `doc/task/done/BUG-31-...md`, hoàn tất nhóm P1) — `BackupRestoreEngine.readBackup()` giới hạn kích thước/entry (20MB) + tổng số entry (500), chặn OOM/zip-bomb; unit test dùng payload zip-bomb THẬT (100MB nén thành entry nhỏ), đã verify bắt được bug bằng revert; smoke test xác nhận luồng Backup thật hoạt động ("Backup saved successfully"), Restore picker mở đúng và lọc đúng MIME, nhưng không hoàn tất được bước chọn file cuối do quirk input-injection của DocumentsUI trên ROM OPPO (không phải lỗi app — đã ghi bằng chứng logcat). **BUG-27 2026-09-16** (xem `doc/task/done/BUG-27-...md`, bắt đầu nhóm P2) — `SaveImageListAdapter.onCreateViewHolder` đọc `parent.height` lúc RecyclerView chưa layout xong (thumbnail đầu co về 0dp); fix mạnh hơn đề xuất gốc: bỏ hẳn dependency vào `parent.height` runtime, dùng `R.dimen.save_result_row_height` cố định (144dp, dùng chung với XML); test tự-verify bằng revert; smoke test thật trên Google Pixel 7 Pro (đổi device 3 lần trong session, "dùng pixel") xác nhận cả 6 thumbnail hiển thị đúng, không ảnh nào 0dp. Nhân tiện fix theo yêu cầu user giữa chừng: đổi copyright hardcode "© 2026 McKim Quyen" → "© SAIGON PHANTOM LABS" ở `SplashActivity.kt`/`LaunchView.kt`, gộp về 1 string resource `R.string.app_copyright` dùng chung. **BUG-36 2026-09-16** (xem `doc/task/done/BUG-36-...md`) — điều tra "watermark tile đè lên UI chrome" phát hiện qua screenshot BUG-27 → **KHÔNG PHẢI BUG**: ảnh test tình cờ là screenshot của chính app (1440×3120 trùng độ phân giải Pixel 7 Pro), tạo ảo giác "màn hình trong màn hình" (2 lớp chữ "WATERMARK" chồng nhau — 1 thật ở toolbar, 1 là nội dung ảnh). Control test với ảnh thường (tỉ lệ dọc tương tự) xác nhận watermark tile bám đúng bounds `ivPhoto`, không tràn UI thật. Không sửa code, không push. **BUG-29/30/34 2026-09-17** (xem `doc/task/done/`, 3 fix XS gộp chung 1 loop): BUG-29 tách `GalleryFragment.computeSliderScrollPercent()` dùng `Float` thay `Int/Int`; BUG-30 tách `AboutActivity.buildMoreAppsUrl()` đổi sang `store/search?q=pub:` + `Uri.encode`; BUG-34 tách `SignatureRepository.resolveWriteResult()` theo đúng pattern `MediaStoreWriteResolver` (BUG-19), xoá file rác khi `compress()` thất bại. Cả 3 đã verify bắt được bug bằng revert. Smoke test thật trên TECNO KJ7 (quay lại device đã khóa ban đầu theo "chỉ dùng tecno" giữa chừng session): slider gallery cuộn mượt đúng tỉ lệ (xác nhận qua `uiautomator dump` tìm đúng bounds `sliderCard`), More Apps mở đúng Play Store `AssetBrowserActivity` (không xác nhận được nội dung trang do máy test không có mạng thật — môi trường, không phải code), Signature Studio vẽ + Apply tạo file `.webp` thật kích thước hợp lệ trên đĩa.

## ENHANCEMENTS (16 todo, 1 deferred + 19 done) — cải tiến tính năng có sẵn

| ID | Effort | Tiêu đề |
|---|---|---|
| [ENH-17](todo/ENH-17-vip-key-device-bound.md) | S | VIP key gắn thiết bị (device-bound) — mitigation cho BUG-14 (mới 2026-09-10) — **deferred cùng BUG-14/15** |
| [ENH-21](todo/ENH-21-cmonetsimplesp-log-khong-gate-debug-ro-log-setting-monet-moi.md) | XS | `cmonet/SimpleSp` log không gate DEBUG, rò log setting Monet mọi lần đọc/ghi ở bản release |
| [ENH-22](todo/ENH-22-applovinkt-167-dong-dead-code-toan-bo-bi-comment-nen-xoa.md) | XS | `Applovin.kt` 167 dòng dead code, toàn bộ bị comment — nên xoá |
| [ENH-23](todo/ENH-23-repositorymoduleprovidememorysettingrepository-gan-nham-name.md) | XS | `RepositoryModule.provideMemorySettingRepository()` gắn nhầm `@Named("WaterMarkPreferences")` |
| [ENH-24](todo/ENH-24-userconfigrepository-ghi-datastore-vo-ich-moi-lan-mo-app-cho.md) | XS | `UserConfigRepository` ghi DataStore vô ích mỗi lần mở app cho tính năng đã gỡ (changelog) |
| [ENH-28](todo/ENH-28-signaturebottomsheetfragmentsavebitmaptocache-leak-file-desc.md) | XS | `SignatureBottomSheetFragment.saveBitmapToCache()` leak file descriptor khi `compress()` ném exception |
| [ENH-29](todo/ENH-29-khong-don-file-cache-tam-qrsignature-cu-tich-luy-rac-khong-g.md) | XS | Không dọn file cache tạm QR/signature cũ, tích luỹ rác không giới hạn |
| [ENH-30](todo/ENH-30-textwatermarkbsdfragmentet-field-khong-bao-gio-duoc-gan-auto.md) | XS | `TextWatermarkBSDFragment.et` field không bao giờ được gán, auto-focus không hoạt động |
| [ENH-25](todo/ENH-25-cmonet-compilesdk-lech-voi-app-qua-appstargetsdk-gan-nhu-dea.md) | S | `cmonet` compileSdk lệch với `:app` qua `Apps.targetSdk` gần như dead object |
| [ENH-26](todo/ENH-26-productflavors-apptestapprelease-rong-khong-khac-biet-gi-nha.md) | S | `productFlavors` `appTest`/`appRelease` rỗng, không khác biệt gì, nhân đôi build variant vô ích |
| [ENH-27](todo/ENH-27-mainviewmodelremoveimage-tinh-sai-selectedpos-khi-xoa-anh-cu.md) | S | `MainViewModel.removeImage()` tính sai `selectedPos` khi xoá ảnh CUỐI danh sách |
| [ENH-34](todo/ENH-34-chuan-hoa-lua-chon-webp-theo-api-moi-webplossywebplossless.md) | S | Chuẩn hoá lựa chọn WEBP theo API mới (`WEBP_LOSSY`/`WEBP_LOSSLESS`) |
| [ENH-35](todo/ENH-35-preview-export-nen-phan-anh-ro-anh-loikhong-doc-duoc-ngay-tr.md) | S | Preview export nên phản ánh rõ ảnh lỗi/không đọc được ngay trong grid (trước khi export thật) |
| [ENH-31](todo/ENH-31-restore-backup-nen-validate-signature-la-anh-that-truoc-khi.md) | M | Restore backup nên validate signature là ảnh thật trước khi ghi ra đĩa |
| [ENH-32](todo/ENH-32-preview-export-nen-huy-job-va-recycle-bitmap-khi-viewholder.md) | M | Preview export nên huỷ job và recycle bitmap khi ViewHolder bị tái sử dụng (RecyclerView recycle) |
| [ENH-33](todo/ENH-33-ho-tro-quet-thu-muc-saf-de-quy-co-gioi-han-tuy-chon-include.md) | M | Hỗ trợ quét thư mục SAF đệ quy có giới hạn (tuỳ chọn "Include subfolders") |

**Đã DONE**: ENH-04 (pinch-to-resize), ENH-05 (preview token khớp export — xác nhận đã triển khai 2026-09-06, xem `doc/feat.md` mục 4). **Sprint ENH S/XS 2026-09-12** (xem `## Sprint ENH 2026-09-12` cuối file): ENH-02 (debounce ghi DataStore khi gõ text), ENH-03 (gate `Log.d` bằng `BuildConfig.DEBUG`), ENH-07 (SignatureRepository qua Hilt DI), ENH-11 (vòng đời Ad Banner đầy đủ), ENH-12 (MonetManufacturer dựa API chính thức), ENH-13 (hiển thị số ảnh thành công/thất bại), ENH-18 (hằng số "Unknown Device" chung), ENH-19 (EXIF border co chữ tránh tràn), ENH-20 (preview filename query bất đồng bộ). **Sprint ENH hiệu năng M 2026-09-12** (xem `## Sprint ENH hiệu năng M 2026-09-12` cuối file): ENH-06 (gộp mở InputStream decode), ENH-15 (BitmapCache reference counting an toàn khi evict), ENH-16 (throttle rebuild shader khi pinch), ENH-14 (downsample trực tiếp khi decode export — làm với AC hạ chuẩn, user quyết định 2026-09-12). **Sprint ENH-08/09/10 2026-09-12** (xem `## Sprint ENH-08/09/10 2026-09-12` cuối file): ENH-08 (ImageInfo bất biến hoàn toàn), ENH-09 (hardcode string sang resources + plurals), ENH-10 (Android Photo Picker thay ACTION_PICK legacy). **ENH-01 2026-09-12** (xem `## ENH-01 2026-09-12` cuối file): batch export qua WorkManager + huỷ + tiến độ notification — phát hiện + fix crash `foregroundServiceType` thật qua smoke test Samsung SM-S928B.

## NEW_FEATURES (14 todo + 1 inprogress + 10 done) — tính năng mới thực dụng, 1-2 tuần

| ID | Effort | Tiêu đề |
|---|---|---|
| [FEAT-04](todo/FEAT-04-lich-su-batch-gan-day.md) | M | Lịch sử batch export gần đây |
| [FEAT-06](todo/FEAT-06-watermark-profile-day-du.md) | M | Watermark profile đầy đủ |
| [FEAT-12](todo/FEAT-12-undo-redo-editor.md) | M | Undo/Redo chỉnh sửa watermark trong editor |
| [FEAT-19](todo/FEAT-19-chinh-sach-xu-ly-trung-ten-file-khi-export-lai-cung-batchnam.md) | S | Chính sách xử lý trùng tên file khi export lại cùng batch/naming pattern |
| [FEAT-21](todo/FEAT-21-dan-anh-tu-clipboard-de-watermark-nhanh.md) | S | Dán ảnh từ Clipboard để watermark nhanh |
| [FEAT-22](todo/FEAT-22-chup-anh-truc-tiep-tu-camera-roi-watermark-ngay.md) | S | Chụp ảnh trực tiếp từ Camera rồi watermark ngay |
| [FEAT-24](todo/FEAT-24-danh-sach-iconlogo-gan-day-dung-nhanh-mru-quick-pick.md) | S | Danh sách icon/logo gần đây dùng nhanh (MRU quick-pick) |
| [FEAT-15](todo/FEAT-15-chon-thu-muc-xuat-anh-bang-saf-khac-feat-08-chon-thu-muc-ngu.md) | M | Chọn thư mục XUẤT ảnh bằng SAF (khác FEAT-08 — chọn thư mục NGUỒN ảnh vào batch) |
| [FEAT-17](todo/FEAT-17-bo-qua-skip-1-anh-trong-batch-ngay-tai-man-export-preview-kh.md) | M | Bỏ qua (skip) 1 ảnh trong batch ngay tại màn export preview |
| [FEAT-18](todo/FEAT-18-so-sanh-truocsau-bang-slider-beforeafter-compare-ca-trong-ed.md) | M | So sánh trước/sau bằng slider (before/after compare) — editor lẫn preview batch |
| [FEAT-20](todo/FEAT-20-chia-se-ngay-cac-anh-vua-export-xong-share-sheetgoi-zip.md) | M | Chia sẻ ngay các ảnh vừa export xong (share sheet/gói ZIP) |
| [FEAT-23](todo/FEAT-23-ghi-nho-vi-tri-watermark-rieng-theo-ti-le-khung-anh-portrait.md) | M | Ghi nhớ vị trí watermark riêng theo tỉ lệ khung ảnh (portrait/landscape) |
| [FEAT-03](todo/FEAT-03-multi-layer-watermark.md) | L | Watermark đa lớp (chồng text + logo/QR cùng lúc) |
| [FEAT-16](todo/FEAT-16-cropstraighten-nhanh-truoc-khi-watermark.md) | L | Crop/straighten nhanh trước khi watermark |

**Đang làm** (`inprogress/`): FEAT-13 (caption riêng theo ảnh trong batch — code + unit test xong, **thiếu smoke test thật riêng cho ticket này**, xem `## FEAT-13/FEAT-05 2026-09-16` cuối file).

**Đã DONE**: FEAT-01 (9-grid position anchor — xác nhận đã triển khai 2026-09-05, xem `doc/feat.md` mục 7), FEAT-02 (naming template file xuất), FEAT-09 (preset resize theo nền tảng), FEAT-14 (Custom Frame Builder tham số hoá EXIF). **FEAT-11 2026-09-12** (xem `## FEAT-11 2026-09-12` cuối file): hiệu ứng viền/bóng/nền pill cho text watermark. **FEAT-08 2026-09-12** (xem `## FEAT-08 2026-09-12` cuối file): chọn cả thư mục (SAF tree) để batch. **FEAT-10 2026-09-13** (xem `## FEAT-10 2026-09-13` cuối file): tự nhận diện hãng máy để gợi ý style khung EXIF. **FEAT-07 2026-09-13** (xem `## FEAT-07 2026-09-13` cuối file, verify 1 phần — xem ghi chú giới hạn môi trường trong file done): preview grid watermark + ước tính dung lượng trước khi export cả batch. **FEAT-05 2026-09-16** (xem `## FEAT-13/FEAT-05 2026-09-16` cuối file): xuất/nhập Template + Signature (backup/restore) qua SAF + zip, smoke test thật đầy đủ trên TECNO KJ7.

## MATERIAL_YOU_MIGRATION (9 done) — chuyển đổi toàn diện UI/UX sang Material You (Material 3)

> Hoàn thành ngày 2026-09-13: thay thế hoàn toàn giao diện kính tối giả lập "iOS Liquid Glass v2" (hơn 15 drawable kính mờ, ~25 token `glass_*`, hardcode `#007AFF`, ép Dark theme, né tránh Android 15 Edge-to-Edge) bằng chuẩn Material Design 3 / Material You đồng bộ Dynamic Color (Monet), Light/Dark adaptive, M3 Shapes, Typography và Edge-to-Edge tự nhiên. Đã smoke test và xác minh hình ảnh trực tiếp trên thiết bị Google Pixel 7 Pro (Android 14/15).

| ID | Priority | Effort | Tiêu đề | Trạng thái |
|---|---|---|---|---|
| [M3-01](done/M3-01-material-you-foundation-theme-color-system.md) | P0 | M | Thiết kế nền tảng Material You M3: Color System, Typography, Shape & Edge-to-Edge | **DONE** |
| [M3-02](done/M3-02-migrate-main-screen-launchview-editor.md) | P1 | M | Migrate Màn hình chính (MainActivity & LaunchView) sang Material You M3 | **DONE** |
| [M3-03](done/M3-03-migrate-gallery-picker-screen.md) | P1 | S | Migrate Màn hình chọn ảnh (Gallery Picker) sang Material You M3 | **DONE** |
| [M3-04](done/M3-04-migrate-save-export-bottom-sheet.md) | P1 | M | Migrate Bottom Sheet xuất ảnh (Save/Export Dialog) sang Material You M3 | **DONE** |
| [M3-05](done/M3-05-migrate-signature-studio-screen.md) | P1 | S | Migrate Màn hình Chữ ký (Signature Studio) sang Material You M3 | **DONE** |
| [M3-06](done/M3-06-migrate-about-open-source-screens.md) | P2 | S | Migrate Màn hình About & Open Source sang Material You M3 | **DONE** |
| [M3-07](done/M3-07-migrate-vip-management-screen.md) | P2 | S | Migrate Màn hình Quản lý VIP (VipManagementActivity) sang Material You M3 | **DONE** |
| [M3-08](done/M3-08-migrate-edit-panels-dialogs-bottom-sheets.md) | P1 | M | Migrate toàn bộ Dialogs, Bottom Sheets và Panels chỉnh sửa sang Material You M3 | **DONE** |
| [M3-09](done/M3-09-cleanup-ios-glass-assets-lint-verification.md) | P2 | S | Dọn dẹp triệt để tài nguyên iOS Glass, Lint & Kiểm thử hồi quy toàn diện | **DONE** |

## UNIQUE_IDEAS (18) — tính năng độc quyền/đột phá, effort cao

| ID | Effort | Tiêu đề |
|---|---|---|
| [IDEA-01](todo/IDEA-01-ai-auto-placement-nhan-dien-chu-the.md) | L | Auto-placement bằng on-device ML (né mặt người/chủ thể) — **đồng thuận 4/4 agent (đợt gốc)** |
| [IDEA-08](todo/IDEA-08-referral-vip-qua-share-app.md) | M | Referral VIP qua Share App — tái dùng hạ tầng sẵn có, không cần backend (mới 2026-09-10) |
| [IDEA-06](todo/IDEA-06-auto-contrast-opacity-harmonizer.md) | M | Auto-contrast/opacity harmonizer theo từng ảnh |
| [IDEA-07](todo/IDEA-07-batch-qr-smart-bridge.md) | M | Batch QR Smart-Bridge (hash SHA-256 + xác thực nguồn gốc) |
| [IDEA-03](todo/IDEA-03-content-authenticity-stamp-c2pa.md) | L | Content authenticity stamp kiểu C2PA |
| [IDEA-09](todo/IDEA-09-watermark-survivability-preview.md) | L | Watermark Survivability Preview — mô phỏng crop/recompress mạng xã hội (mới 2026-09-10) |
| [IDEA-10](todo/IDEA-10-recipient-fingerprint-batch.md) | L | Recipient Fingerprint Batch — watermark riêng theo người nhận, truy nguồn rò rỉ (mới 2026-09-10) |
| [IDEA-15](todo/IDEA-15-brand-compliance-scoring-cham-diem-moi-anh-theo-rule-thuong.md) | L | Brand Compliance Scoring — chấm điểm mỗi ảnh theo rule thương hiệu trước khi export (mới 2026-09-16) |
| [IDEA-16](todo/IDEA-16-watermark-tu-sinh-noi-dung-theo-gps-thoi-tiet-luc-chup.md) | L | Watermark tự sinh nội dung theo GPS + thời tiết lúc chụp (mới 2026-09-16) |
| [IDEA-17](todo/IDEA-17-voice-to-text-caption-khi-batch-nhieu-anh-mo-rong-feat-13.md) | L | Voice-to-text caption khi batch nhiều ảnh, mở rộng FEAT-13 (mới 2026-09-16) |
| [IDEA-02](todo/IDEA-02-invisible-watermark-steganography.md) | XL | Invisible watermark / steganography chống xoá |
| [IDEA-04](todo/IDEA-04-cloud-sync-brand-kit.md) | XL | Cloud sync Brand Kit đa thiết bị |
| [IDEA-05](todo/IDEA-05-cho-template-cong-dong.md) | XL | Chợ template cộng đồng (network effect) |
| [IDEA-11](todo/IDEA-11-live-camera-watermark-ar-preview-xem-watermark-ngay-tren-vie.md) | XL | Live Camera Watermark / AR preview — watermark ngay trên viewfinder trước khi chụp (mới 2026-09-16, 2 nguồn đồng thuận) |
| [IDEA-12](todo/IDEA-12-on-device-style-coach-goi-y-fontmauopacityvi-tri-theo-phong.md) | XL | On-device Style Coach — gợi ý font/màu/opacity/vị trí theo phong cách ảnh cá nhân (mới 2026-09-16) |
| [IDEA-13](todo/IDEA-13-client-proofing-mode-xuat-album-proof-cho-khach-chon-anh-dan.md) | XL | Client Proofing Mode — album proof cho khách chọn ảnh, dành cho photographer (mới 2026-09-16) |
| [IDEA-14](todo/IDEA-14-smart-redaction-watermark-tu-phat-hien-thong-tin-nhay-cam-tr.md) | XL | Smart Redaction + Watermark — tự phát hiện thông tin nhạy cảm trước khi đóng dấu (mới 2026-09-16) |
| [IDEA-18](todo/IDEA-18-bo-sinh-khung-exif-border-moi-theo-bang-mau-anh-khong-gioi-h.md) | XL | Bộ sinh khung EXIF border mới theo bảng màu ảnh, không giới hạn 4 style cố định (mới 2026-09-16) |

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

## Sprint ENH hiệu năng M 2026-09-12 — nhóm bitmap/render (ENH-06/15/16/14)

4 ticket effort M trong nhóm "ENH hiệu năng M" hoàn thành theo `PROMPT_TEMPLATE.md` — chi tiết từng ticket xem "Kết quả kiểm chứng" trong file ở `doc/task/done/`.

- **ENH-14 (quyết định riêng qua AskUserQuestion sau khi 3 ticket đầu đã push)**: ticket yêu cầu đo bằng Android Studio Memory Profiler — không làm được qua CLI/ADB. User chọn phương án "làm nhưng hạ chuẩn AC" (thay vì bỏ qua hẳn hoặc chờ tự đo bằng Android Studio): thay Profiler bằng bằng chứng gián tiếp (`allocationByteCount` đo trực tiếp trên bitmap thật, tỷ lệ đúng dự đoán lý thuyết ~4 lần khi downsample cạnh dài còn 1/2). Rủi ro lệch vị trí/scale watermark KHÔNG xảy ra vì code tính toạ độ đã sẵn tính động theo kích thước bitmap thật, không cần sửa gì thêm ngoài truyền `reqLongEdge` vào hàm decode.
- **Giới hạn phát hiện khi smoke test ENH-14 trên device thật**: không có sẵn ảnh nguồn đủ lớn (>2160px cạnh dài) trên Samsung S24 Ultra để chứng minh nhánh downsample thực sự kích hoạt ngoài đời thật (thư mục ảnh mẫu trên máy chỉ toàn Full HD ~1920px) — bằng chứng cho đúng nhánh downsample dựa vào unit test dùng ảnh JPEG thật tự tạo 3200x1600.

- **ENH-06**: dừng ở 3 lần mở `InputStream`/ảnh (không đạt mục tiêu aspirational "1-2" trong ticket gốc) — quyết định có chủ đích, ghi rõ lý do trong ticket (gộp thêm bằng `mark/reset` rủi ro `IOException` không đoán trước trên SAF/cloud provider, đổi lấy giảm 1 lần mở stream không đáng).
- **ENH-15**: chọn Phương án B (reference counting) thay vì Phương án A (chỉ giảm cacheSize) — vì Phương án A không giải quyết gốc rủi ro use-after-recycle nêu trong ticket. Audit đủ 2 nơi giữ tham chiếu trực tiếp bitmap từ cache (`WaterMarkImageView`, `MainViewModel.generateImage()`).
- **ENH-16**: chọn Hướng 1 (throttle theo thời gian, 40ms) trong 3 hướng đề xuất của ticket — đơn giản nhất, đủ hiệu quả (giảm >75% lần rebuild theo test mô phỏng 120fps), không đổi accuracy cuối cùng nhờ force-apply ở `onScaleEnd`.
- **Giới hạn chung không tránh được của cả 3 ticket**: 2/2 Acceptance Criteria dạng "đo bằng Android Studio Memory/CPU Profiler" (ENH-15, ENH-16) không thực hiện được vì môi trường làm việc chỉ có CLI + ADB, không có Android Studio UI — thay bằng bằng chứng gián tiếp (unit test mô phỏng đúng cơ chế, hoặc số liệu đếm thực nghiệm qua `ContentProvider`/`LruCache` giả lập). Ghi rõ trong từng ticket, không tự nhận đã đo Profiler khi chưa đo.
- **Pinch 2 ngón thật không giả lập được qua `adb shell input`** (chỉ hỗ trợ 1 pointer/không multi-touch) — AC "pinch mượt hơn theo cảm nhận thực tế" của ENH-16 còn để ngỏ trong ticket, cần người dùng xác nhận tay thật.
- **Smoke test thật trên Samsung Galaxy S24 Ultra (SM-S928B, serial R5CX613VZBR)** — đổi từ OnePlus CPH1989 theo yêu cầu tường minh của user giữa phiên ("hãy dùng s24u"): batch 2 ảnh, bật Icon watermark mode (decode+hiển thị icon bitmap từ cache), chuyển qua lại giữa 2 ảnh nhiều lần, theo dõi `adb logcat *:E` xuyên suốt — không `FATAL EXCEPTION`/`AndroidRuntime` nào liên quan app, không crash.
- **`uiautomator dump` tiếp tục không đáng tin cậy** trên cả OnePlus lẫn Samsung S24 Ultra (trả cây UI cũ/sai màn hình) — xác nhận đây là vấn đề công cụ trên các máy test gần đây, không phải riêng 1 hãng; toàn bộ thao tác tap phải tính toạ độ từ ảnh chụp màn hình thật, có lúc cần crop ảnh phóng to vùng nút để đo chính xác khi UI có nhiều cột hẹp sát nhau (row Text/Icon/Signature/QR Code).

## Sprint ENH-08/09/10 2026-09-12 — code quality (immutable state, i18n, Photo Picker)

3 ticket effort M hoàn thành theo `PROMPT_TEMPLATE.md` — chi tiết từng ticket xem "Kết quả kiểm chứng" trong file ở `doc/task/done/`.

- **ENH-08**: chuyển toàn bộ field `var` của `ImageInfo` sang `val`, compiler tự bắt hết chỗ mutate còn sót (2 file test cũ dùng `.apply { field = x }` bị lỗi biên dịch, sửa dùng constructor arg). Phát hiện bug thật khớp đúng cảnh báo ticket: `SaveImageListAdapter.differCallback.areItemsTheSame` dùng full equals thay vì so theo `uri` — chỉ "đúng" tình cờ khi object còn mutable, sau khi bất biến sẽ khiến DiffUtil coi mọi update jobState là item khác hẳn. Sửa cả `areItemsTheSame` lẫn `updateJobState()` (dùng `uri` + thực sự `submitList()` thay vì chỉ `notifyItemChanged` suông).
- **ENH-09**: audit rộng hơn 2 file ticket nêu, tìm thêm 2 chỗ hardcode (`SaveImageBSDialogFragment` Toast lỗi share, `AboutActivity` version display). Thêm 2 `<plurals>` cho label chọn ảnh gallery — string mới chỉ thêm ở `values/strings.xml` base (English), không dịch 11 locale khác, theo đúng tiền lệ ENH-13.
- **ENH-10**: chỉ thay 2 điểm dùng `ACTION_PICK` ngoài (icon picker đơn + nút "pick via system" trong GalleryFragment) bằng Android Photo Picker, KHÔNG đụng luồng chọn ảnh chính (GalleryFragment tự query MediaStore hiển thị grid trong app — đúng phạm vi 2 file ticket nêu). Phát hiện bất ngờ: Robolectric SDK giả lập hiện tại đã báo `isPhotoPickerAvailable()=true` (giống thiết bị Android 13+ thật) — ban đầu viết test sai giả định nhánh fallback luôn chạy trong Robolectric, phải sửa lại test để verify đúng nhánh Photo Picker thật đang chạy.
- **Hạ tầng test bị treo/crash liên tục, nặng hơn hẳn các sprint trước** trong phiên này: full `testAppReleaseDebugUnitTest` (42 class) treo 0% CPU nhiều lần liên tiếp (tới ~30 phút/lần không tiến triển), 1 lần daemon Gradle crash hẳn ("daemon disappeared unexpectedly") — xác nhận qua `sysctl vm.swapusage` swap có lúc lên 6.5/8GB, RAM free chỉ còn ~60-70MB (máy chạy đồng thời Android Studio + Chrome nhiều tab + LarkSuite + 2 phiên Claude). Khắc phục: chia nhỏ thành 7 batch × 6 class, `./gradlew --stop` giữa mỗi batch để tránh daemon tích luỹ trạng thái xấu — toàn bộ 42 class pass, chỉ 1 test debounce QR không liên quan (`QrCodeBottomSheetFragmentRoboTest`, từ sprint BUG-13 cũ) fail 1 lần khi chạy chung batch nhưng pass ngay khi chạy riêng lẻ (flaky do tải máy, không phải regression — xác nhận không đụng file đó trong sprint này).
- **Device đổi 2 lần giữa sprint** theo yêu cầu tường minh của user: Samsung Galaxy S24 Ultra → Samsung A50s (SM_A507FN, R58MA6WYRPE) → TECNO_BG6 (118743744X002560, máy cuối cùng dùng để smoke test sprint này).
- **Bug thật phát hiện qua smoke test trên TECNO_BG6** (không phải theo kế hoạch — patch giữa chừng sau khi unit test đã pass hết): batch export 2 ảnh (1 ảnh camera nặng dùng làm Icon watermark tile, 1 ảnh nhẹ), ảnh nặng mất >60s xử lý — file export ra ĐÚNG và ĐẦY ĐỦ trên đĩa nhưng card trong danh sách kẹt vĩnh viễn ở icon "đang xử lý". Root cause: race condition trong `SaveImageListAdapter.updateJobState()` (chính code vừa sửa cho ENH-08) — đọc `differ.currentList` (chỉ cập nhật bất đồng bộ) làm nguồn build update tiếp theo, nhiều lệnh gọi liên tiếp nhanh làm mất update trước đó. Sửa bằng `pendingList` mutate đồng bộ + thêm test regression tái hiện đúng race (`updateJobState_rapidSuccessiveCallsForDifferentItems_noUpdateLost`). Xem chi tiết trong `doc/task/done/ENH-08-immutable-state-watermark-repository.md`. Bài học: unit test pass không đảm bảo hết bug timing/race — smoke test batch nhiều ảnh với ít nhất 1 ảnh đủ nặng để tạo độ trễ khác biệt giữa các item là bước bắt buộc, không thể bỏ qua dù đã có unit test.

## ENH-01 2026-09-12 — batch export qua WorkManager + huỷ + tiến độ notification

Ticket effort L, chi tiết đầy đủ xem "Kết quả kiểm chứng" trong `doc/task/done/ENH-01-batch-export-workmanager-huy-tien-do.md`.

- Trích xuất logic export (`generateImage`/`generateList`) từ `MainViewModel` sang `BatchExportEngine` (không `viewModelScope`) để `BatchExportWorker` (`@HiltWorker`) dùng được; tên file/token tách sang `ExportNaming`. Giữ ~4 hàm cũ trên `MainViewModel` làm thin delegate 1 dòng — 4 test file cũ gọi trực tiếp các hàm này không phải sửa.
- 2 bug thật tự phát hiện qua tự audit code trước khi build: observer `observeForever` rò rỉ (không gỡ ở `onCleared()`), và ViewModel mới sau process death không tự bắt lại trạng thái batch đang chạy nền (sửa bằng `reattachExportWorkIfRunning()` gọi từ `SaveImageBSDialogFragment.onViewCreated()` thay vì đặt trong `init{}` của ViewModel — tránh ép 12+ test file không liên quan phải bootstrap WorkManager).
- **Bug thật phát hiện qua smoke test trên Samsung SM-S928B (Galaxy S24 Ultra)**: crash 100% khi bấm Export — `IllegalArgumentException: foregroundServiceType 0x00000001 is not a subset of foregroundServiceType attribute 0x00000000 in service element of manifest file`. Nguyên nhân: `setForeground()` promote với `FOREGROUND_SERVICE_TYPE_DATA_SYNC` (API 34+) nhưng chỉ khai `<uses-permission>` là chưa đủ — phải override tường minh `android:foregroundServiceType="dataSync"` trên `<service>` `SystemForegroundService` của chính thư viện WorkManager qua manifest merge. Crash lặp lại khiến app tự vào Recovery Mode (cơ chế crash-guard sẵn có). 17 unit test ENH-01 đều pass TRƯỚC KHI phát hiện bug này — Robolectric không validate `foregroundServiceType` thật ở OS-level, đây là lớp lỗi CHỈ smoke test thật trên device mới bắt được. Verify lại sau fix: export batch 2 ảnh thật qua Photo Picker, cả 2 thành công, watermark đúng, `logcat` sạch không còn `FATAL EXCEPTION`.
- Chưa verify được qua thao tác tay: tiến độ % trên notification khi export đủ lâu để quan sát (batch test 2 ảnh nhỏ hoàn tất dưới 1 giây), và bấm Cancel thật giữa batch đang chạy — cả 2 đã có unit test xác nhận đúng logic, chỉ thiếu ảnh chụp thao tác vật lý trên UI hệ thống.

## FEAT-11 2026-09-12 — hiệu ứng viền/bóng/nền pill cho text watermark

Ticket effort S, chi tiết đầy đủ xem "Kết quả kiểm chứng" trong `doc/task/done/FEAT-11-hieu-ung-vien-bong-text.md`.

- 3 hiệu ứng (Outline/Shadow/Pill BG) thêm vào `WaterMark` dạng `Boolean` độc lập (không phải sealed class như `TextPaintStyle` — vì phải kết hợp tự do, không loại trừ nhau). Màu tương phản B/W tự tính theo luminance màu chữ, không thêm color picker riêng (ngoài phạm vi AC).
- Export dùng chung 100% code path với preview (`WaterMarkImageView.buildTextBitmapShader` + `PainKtx.applyConfig`) — không cần sửa gì thêm ở `BatchExportEngine`, verify bằng smoke test thật (export file, zoom kiểm tra bằng mắt khớp preview).
- Smoke test trên Pixel 7 Pro xác nhận chip UI toggle độc lập đúng (3 chip active đồng thời), Material You dynamic color áp dụng đúng cho trạng thái checked (`?attr/colorPrimary`, không hardcode hex).
- Nhân tiện dọn `BACKLOG.md`: bảng NEW_FEATURES liệt kê nhầm FEAT-02/FEAT-09/FEAT-14 là "todo" trong khi cả 3 đã xong từ trước (nằm ở `done/`) — sửa lại đúng trạng thái.

## FEAT-08 2026-09-12 — chọn cả thư mục (SAF tree) để batch

Ticket effort S, chi tiết đầy đủ xem "Kết quả kiểm chứng" trong `doc/task/done/FEAT-08-chon-thu-muc-saf-batch.md`.

- Thêm nút "Choose folder" cạnh nút "pick via system" có sẵn (ENH-10) trong menu `GalleryFragment` — không đụng layout/FAB/grid hiện có.
- Logic lọc ảnh trong cây SAF (`FileUtils.filterImageUris`) tách riêng khỏi phần gọi `DocumentFile.fromTreeUri` thật để test được bằng mock, không cần dựng cả `DocumentsProvider` giả.
- Kết quả đẩy thẳng qua `handleActivityResult()` đã có sẵn cho multi-pick — không viết code riêng cho đường dẫn mới, giảm rủi ro hồi quy.
- Smoke test thật trên Pixel 7 Pro qua đúng picker SAF hệ thống: chọn thư mục `Pictures` (có sẵn cả ảnh trực tiếp lẫn nhiều subfolder) — xác nhận chỉ ảnh trực tiếp được đưa vào batch, subfolder không bị đệ quy, không crash.

## FEAT-10 2026-09-13 — tự nhận diện hãng máy để gợi ý style khung EXIF

Ticket effort S, chi tiết đầy đủ xem "Kết quả kiểm chứng" trong `doc/task/done/FEAT-10-frame-preset-nhan-dien-hang-may.md`.

- `ExifFrameStyle.suggestFor(make)` — hàm thuần map chuỗi hãng máy (từ `TAG_MAKE` EXIF) sang 1 trong 4 style có sẵn (Classic/Polaroid/Film Strip/Minimal), khớp đúng ví dụ AC gốc.
- Theo dõi "ảnh nào user đã tự tay đổi style" bằng `MutableSet<Uri>` session-scoped trên `MainViewModel` (không persist DataStore) — tránh thêm state per-ảnh mới cho ticket effort S, chấp nhận reset khi app restart vì chỉ là gợi ý UX.
- Test race timing của DataStore-Flow-backed LiveData bằng polling helper (`awaitExifFrameStyle`) thay vì 1 lần `idle()`, theo đúng pattern `awaitJobFinished` đã dùng ở ENH-01.
- Smoke test thật trên Pixel 7 Pro: ảnh test không có EXIF Make → tự động khoanh chọn đúng "Minimal". Nhánh hãng máy cụ thể (Fujifilm/Leica/Canon...) dùng unit test JVM làm bằng chứng chính do máy thật không có ảnh với EXIF hãng tương ứng.

## FEAT-07 2026-09-13 — preview grid watermark + ước tính dung lượng trước khi export cả batch

Ticket effort M, chi tiết đầy đủ xem "Kết quả kiểm chứng" trong `doc/task/done/FEAT-07-preview-grid-truoc-khi-export.md`. **Lưu ý: verify 1 phần** — máy bị nghẽn tài nguyên nghiêm trọng suốt phiên (swap tới 7.85/8.19GB) khiến bộ test Robolectric không chạy được tới kết quả cuối cùng và smoke test trên device không hoàn tất do thao tác chạm qua adb bị lệch bất thường; user đã được thông báo và trực tiếp quyết định đẩy code lên với giới hạn này, cần verify lại đầy đủ ở phiên sau.

- Không xây màn hình mới — nâng cấp grid `SaveImageListAdapter`/`rvResult` đã có sẵn trong `SaveImageBSDialogFragment` (hiện TRƯỚC khi user bấm export thật) từ hiển thị ảnh gốc sang hiển thị bitmap đã áp watermark + text ước tính kích thước/dung lượng.
- `BatchExportEngine.generatePreviewBitmap()` (hàm mới) — decode ảnh nhỏ (480px, cache sẵn) rồi vẽ watermark trực tiếp, tái dùng nguyên vẹn `buildTextBitmapShader`/`buildIconBitmapShader`/`applyConfig` (không viết lại logic render). Cố ý không vẽ khung EXIF border trong preview — khớp quy ước sản phẩm đã có cho live editor.
- `OutputImageUtils.estimateOutputBytes()` (hàm thuần mới) — ước tính dung lượng bằng heuristic bits-per-pixel theo quality, không nén thử thật (ghi rõ là ước tính tương đối, không chính xác tuyệt đối).
- Trong lúc viết `BatchExportEnginePreviewRoboTest`, phát hiện + sửa 1 giả định sai: Robolectric shadow `BitmapFactory` decode MỌI uri (kể cả không tồn tại) thành bitmap giả 100x100 thay vì trả lỗi — tận dụng luôn để test thẳng nhánh render watermark thật.
- Phụ: 1 lần chạy nhầm `:app:ktlintFormat` (sửa lỗi trailing-comma) format lại toàn bộ main source set rồi phải revert — việc đổi mtime hàng loạt làm lộ ra 265 vi phạm ktlint tồn tại từ trước trên ~60 file không liên quan (nợ kỹ thuật có sẵn của dự án, không phải do FEAT-07 — 13 file FEAT-07 sạch 100%). Đáng làm ticket dọn dẹp riêng.

## Ghi chú re-audit 2026-09-10 (khác biệt so với đợt sinh backlog gốc)

- **FEAT-01, ENH-05** đã triển khai xong ngoài luồng backlog — move sang `done/`, không phải làm lại.
- **FEAT-10** chỉ done 1 phần lúc re-audit (4 style khung — xong; tự nhận diện hãng máy — chưa) — đã tách lại scope trong file ticket, hoàn thành 2026-09-13 (xem `## FEAT-10 2026-09-13` phía trên).
- **BUG-14** mở rộng đáng kể phạm vi (3 vị trí lặp secret + 1 secret thứ 2 chưa ticket hoá + cả package `feature/vip/` chưa từng audit) — nâng ưu tiên lên **P0**.
- **BUG-05 (đã done)** vẫn còn sót: `generateImage()` có nhánh early-return không recycle bitmap ở đường lỗi (không phải đường thành công) — theo dõi tiếp ở BUG-21 mới, không mở lại BUG-05.
- 2 ý kiến độc lập lệch nhau về hướng fix BUG-14: `claude -p` + fork nội bộ khuyến nghị **native NDK/JNI** (app chưa có hạ tầng network, effort thấp hơn); `codex exec` khuyến nghị **server-side verify** (trust boundary thật, thu hồi/giới hạn được key) — đã hỏi user quyết định qua AskUserQuestion trong phiên này.

## Gợi ý sprint đầu tiên (cập nhật sau re-audit)

Nhóm P0 giờ có 3 ticket cùng khu vực rủi ro cao: BUG-14 + ENH-17 (bảo mật VIP), BUG-15 (doanh thu Ad thật/test lẫn lộn) — nên gộp 1 sprint đầu, xử lý trước cả nhóm BUG-02..05 cũ (đã done). Sau đó nhóm P1 mới phát hiện (BUG-18/19/21) cùng khu vực `MainViewModel`/`ExifPbFragment` nên làm chung sprint kế tiếp với BUG-07/08/09/10/12 cũ. FEAT effort S/XS (FEAT-02, FEAT-09, FEAT-14) vẫn là lựa chọn tốt để có tính năng "nhìn thấy được" song song.

## Re-audit 2026-09-16 — toàn bộ source code, 4 nguồn độc lập

Re-audit toàn diện theo yêu cầu user ("đọc toàn bộ source code, rã task như scrum master"), đối chiếu với ~75 ticket đã có để tránh trùng lặp. 4 nguồn độc lập: **Claude fork nội bộ** (đọc trực tiếp, phạm vi VIP/Ad/cmonet — vùng chưa từng audit sâu), **codex exec --sandbox read-only** (đọc toàn bộ `app/src/main` + `cmonet/src/main`), **claude --dangerously-skip-permissions -p** (session riêng, allowlist Read/Grep/Glob, đọc toàn bộ), **agy --dangerously-skip-permissions -p** — timeout/không có output (giống tiền lệ đợt re-audit 2026-09-10), bỏ qua, 3/4 nguồn còn lại đủ đồng thuận.

**Kết quả: 46 ticket mới** (BUG-23..35, ENH-21..35, FEAT-15..24, IDEA-11..18) — đã ghi vào `doc/task/todo/`, cập nhật đủ 4 bảng ở trên.

- **Phát hiện khẩn cấp ngoài kế hoạch (claude, đã verify trực tiếp)**: `app/keystore.jks` + `gradle.properties` (chứa `KEY_PASSWORD`/`STORE_PASSWORD` plaintext) đang **commit vào git**, và **cả 2 remote GitHub (`royt93/5_EasyWatermark`, `tplloi/EasyWatermark`) đều PUBLIC** (đã verify qua `gh repo view`) — keystore ký release + password lộ công khai. Báo ngay user qua `AskUserQuestion` (không tự ý xử lý — rewrite git history/rotate key là quyết định business/bảo mật nghiêm trọng, không phải code fix qua `/loop` thường). **User quyết định: bỏ qua, tự xử lý riêng sau** — không tạo ticket `.md`, không đụng file này trong `/loop`.
- **1 finding của codex bị loại bỏ sau verify (không tạo ticket)**: "Hilt module tạo binding `DataStore` thiếu qualifier" (P0 theo codex) — verify trực tiếp: `UserConfigRepository`/`WaterMarkRepository` có `@Inject constructor` riêng, injection site (`MainViewModel`) chỉ request type KHÔNG qualifier — Dagger resolve qua constructor injection, KHÔNG qua `@Provides` method có `@Named` trong `RepositoryModule` (những method đó unreachable trong graph thật). Code đã compile thành công liên tục trong toàn bộ phiên (nhiều lần `kapt`/build) — claim "P0 crash" của codex sai, đây chỉ là dead code (đã ghi nhận riêng ở **ENH-23**, mức độ thấp hơn hẳn, không phải bug build-breaking). Bài học: **luôn verify trực tiếp trước khi tin AI, đặc biệt claim P0/crash** (đúng tinh thần README dòng đầu file này).
- Nhiều finding trùng lặp giữa 2 nguồn (codex + claude) đã gộp thành 1 ticket duy nhất thay vì tạo 2 bản gần giống nhau: ENH-29 (dọn cache QR/signature), FEAT-18 (before/after slider), FEAT-20 (share sau export), IDEA-11 (live camera watermark).
- **Chưa verify sâu từng finding còn lại bằng cách chạy code thật** (chỉ đọc source + vài lần grep xác nhận trọng điểm) — khi bắt đầu implement 1 ticket bất kỳ trong 46 file này, bước đầu tiên của `/loop` (đọc file + audit) PHẢI re-confirm lại finding còn đúng, không mặc định tin 100%.

## FEAT-13/FEAT-05 2026-09-16 — caption riêng batch + backup/restore + code review round

Cả 2 ticket implement độc lập ngoài luồng `/loop` chuẩn (session khác, sau đó reconcile lại backlog) — chi tiết đầy đủ xem "Kết quả kiểm chứng"/"Tiến độ" trong file tương ứng.

- **FEAT-13**: xong code + unit test (`BatchExportEngineCaptionRoboTest` mới regression), nhưng CHƯA smoke test thật riêng — còn ở `inprogress/`, không tự ý move `done/` dù điểm tự-audit code 9/10, đúng nguyên tắc PROMPT_TEMPLATE.md không hạ chuẩn Definition of Done.
- **FEAT-05**: xong đủ cả 3 điều kiện, move `done/`. Triển khai khác nhẹ đề xuất gốc (zip thuần thay JSON, tránh thêm dependency).
- **`/code-review master..dev high`** (agent riêng, review toàn bộ diff so với `master`) tìm 6 phát hiện trên code 2 ticket này — đã fix hết, quan trọng nhất:
  1. **Bug nghiêm trọng** (FEAT-13): caption rỗng không skip vẽ ở export thật (khác preview) → ảnh xuất ra bị tô đen kín. Preview code path và export code path độc lập tự suy diễn cùng 1 rule rồi lệch nhau — bài học: rule dùng ở 2 nơi phải extract hàm chung ngay từ đầu, không đợi review phát hiện.
  2. **Lỗ bảo mật zip-slip** (FEAT-05): tên file trong zip backup (input không tin cậy từ SAF) không sanitize trước khi ghi đĩa.
  3. Phát hiện phụ khi viết test cho fix #2: `FileProvider.getUriForFile()` cache `PathStrategy` theo authority ở static field AndroidX, sống sót qua ranh giới Application/Context của từng `@Test` dưới Robolectric — CÙNG LỚP BUG với deadlock DataStore đã gặp trước đó trong session (`context.xDataStore` singleton). Bài học lặp lại: bất kỳ API nào cache theo authority/key toàn cục (không theo Context instance) đều có nguy cơ y hệt dưới Robolectric multi-test-trong-1-JVM — cần kiểm tra trước khi viết test mới đụng `FileProvider`/tương tự.
- Full unit test 271/271 PASS sau mọi vòng fix. Commit: `32229a8`, `0d9b891`, `cb43eda`, `c40f0b4`, `9648623`, `8624f1c`, `569707b`, `c3af54a`, `0a94828` — đã push `origin/dev`.

## Audit Material You Migration (2026-09-13)

- **Hiện trạng source code trước migrate:**
  - Ứng dụng bị ảnh hưởng sâu bởi giao diện "iOS Liquid Glass v2": hơn 20 tệp drawable kính mờ (`bg_glass_*`, `bg_floating_*`, `bg_ios_switch_*`), khoảng 25 token màu `glass_*`, hardcode màu xanh iOS `#007AFF` / `#FF007AFF` và đỏ `#FF3B30`.
  - Ép buộc Dark theme thông qua `Theme.Material3.Dark.NoActionBar` và `forceDarkAllowed=false`, làm vô hiệu hóa khả năng Dynamic Color (Monet) của Android 12+.
  - Bật `android:windowOptOutEdgeToEdgeEnforcement = true` trong `values-v35/themes.xml` để né tránh Android 15 Edge-to-Edge.
  - Sử dụng các widget legacy: `androidx.cardview.widget.CardView` (thay vì `MaterialCardView`), `SwitchCompat` (thay vì `MaterialSwitch`), `SeekBar` (thay vì `Slider`), `RadioGroup` (thay vì `Segmented Button` / `Single-select Chips`).
- **Kế hoạch thực hiện Epic M3:**
  - Tách thành 9 ticket độc lập (M3-01 đến M3-09) theo đúng chuẩn `PROMPT_TEMPLATE.md`.
  - Khởi đầu với **M3-01 (Foundation)** để dựng chuẩn Theme, Color Roles (DayNight + Dynamic Color), Edge-to-Edge và ContextExtension trước.
  - Sau đó migrate tuần tự theo các module UI: M3-02 (Main/Launch), M3-03 (Gallery Picker), M3-04 (Save BottomSheet), M3-05 (Signature), M3-06 (About), M3-07 (VIP), M3-08 (Dialogs & Panels).
  - Kết thúc bằng **M3-09 (Cleanup & Verification)** để dọn sạch toàn bộ dead drawable/color asset, bảo đảm lint và kiểm thử hồi quy 100%.

## Hoàn thành Material You Migration (2026-09-13)

- **Kết quả thực thi toàn diện:**
  - **M3-01 (Foundation):** Chuyển theme gốc sang `Theme.Material3.DayNight.NoActionBar`. Hỗ trợ trọn vẹn cả Light mode và Dark mode. Kích hoạt Dynamic Color Monet tự động trích xuất màu từ hình nền người dùng trên Android 12+. Gỡ bỏ cờ opt-out Edge-to-Edge để tuân thủ 100% Android 15 (API 35).
  - **M3-02 (Main Screen & LaunchView):** Chuyển LaunchView từ kính đen/glow sang `colorSurface` phẳng chuẩn M3. Hai nút chính nâng cấp thành `FilledButton` (tròn góc pill) và `TonalButton`. Thanh công cụ và panel chức năng biên tập ảnh chuyển sang M3 `colorSurfaceContainerHigh` pill (corner radius 28dp).
  - **M3-03 (Gallery Picker):** Khung cuộn và thẻ ảnh nâng cấp `MaterialCardView`, nút FAB tròn `ExtendedFloatingActionButton`, huy hiệu số lượng ảnh chọn đổi sang `bg_m3_selection_badge`.
  - **M3-04 (Save/Export Dialog):** Nâng cấp dialog sang M3 BottomSheetDialog với viền bo tròn 28dp, thanh kéo kéo chuẩn M3 `drag_handle`. Thay thế toàn bộ EditText cũ thành `TextInputLayout` (OutlinedBox), thanh chất lượng dùng M3 `Slider`, định dạng ảnh xuất dùng `Single-select Chips` phong cách M3.
  - **M3-05 (Signature Studio):** Xóa bỏ các gradient tím và viền kính. Thanh công cụ M3, khung canvas vẽ bo góc 24dp, công tắc phát sáng neon chuyển sang M3 `MaterialSwitch`, nút lưu chuyển sang M3 `FilledButton` theo dynamic color.
  - **M3-06 (About & Open Source):** Thẻ thông tin nâng cấp `MaterialCardView` (`colorSurfaceContainer`), các nút "Rate Us", "More Apps", "Share" dùng M3 Buttons.
  - **M3-07 (VIP Management):** Header trạng thái, thẻ nhập mã kích hoạt, nút kích hoạt và các gói VIP chuyển sang M3 `MaterialCardView` và `MaterialButton`.
  - **M3-08 (Dialogs & Panels):** Di dời toàn bộ 14 dialogs/bottom sheets (EXIF border, QR code, 9-grid position anchor, edit text, tile mode, text style, compress image...) sang chuẩn token M3.
  - **M3-09 (Cleanup & Verification):** Dọn dẹp triệt để 13 tệp drawable kính cũ, xóa token `glass_*`, build thành công APK Debug (`assembleAppReleaseDebug`).
- **Xác minh trực tiếp trên thiết bị Pixel 7 Pro (`2B051FDH3006MU`):**
  - Đã cài đặt APK và kiểm thử trực quan trên màn hình thật ở cả chế độ Light mode và Dark mode.
  - Dynamic Color Monet hoạt động hoàn hảo: nút bấm và điểm nhấn giao diện tự động mang sắc tố ấm (peach/coral) hài hòa với hình nền thiết bị.
  - Không phá vỡ bất kỳ logic nghiệp vụ xử lý ảnh hoặc xuất watermark nào.
  - Toàn bộ thay đổi được lưu trữ an toàn trong working directory, tuân thủ nghiêm ngặt chỉ thị **KHÔNG COMMIT CODE**.

