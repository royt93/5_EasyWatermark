# Feature Source of Truth

> Quản lý danh sách tính năng theo quy tắc R2 toàn cục:
> ✅ Implemented | 🟡 In progress | 📋 Picked | ⏸️ Deferred | ❌ Skipped | 💭 Ideas

## 🟡 In progress

- 📋 **FEAT-29: Hiện khung thẻ ngay trong preview editor** (chọn 2026-10-03, làm sau FEAT-27)
  - Xoá hạn chế "khung thẻ chỉ áp lúc export" của FEAT-28

---

## 📋 Picked (Sprint 2026-09-29)

_(trống)_

---

## ✅ Implemented (Đã hoàn thành trước đây)

Xem chi tiết trong `doc/feat.md` và `doc/task/BACKLOG.md`:
- ✅ FEAT-27: Quick Share Bar — chia sẻ nhanh 1 chạm sau khi xuất (2026-10-03) — `QuickShareHelper`
  lọc Zalo/Messenger/Telegram/Drive đã cài qua `PackageManager` + `<queries>` (danh sách phải khớp
  manifest, có test chống lệch), dựng intent dùng chung với nút Chia sẻ (`openShare(targetPackage)`).
  Hàng icon "Chia sẻ nhanh" hiện sau khi xuất xong, ẩn khi không có app nào hoặc batch lỗi hết.
  Smoke test thật Pixel 7 Pro: Zalo, Messenger, Drive mở đúng kèm ảnh. Telegram: intent gửi đúng
  (`START ... pkg=org.telegram.messenger`) nhưng Telegram không lên foreground, kể cả khi gửi bằng
  `adb shell am start` thuần. **Chưa kết luận được nguyên nhân** — lúc test có tiến trình
  `am instrument ...lenslauncher.test` chen vào foreground nên kết quả bị nhiễu; cần thử lại khi máy sạch.
  **Đã sửa 1 bug thật tìm ra lúc smoke test:** WorkManager replay WorkInfo SUCCEEDED của phiên trước cho
  `MainViewModel` mới, làm ảnh MỚI chưa xuất bị coi là "xong" (nút "Chia sẻ" hiện nhưng bị vô hiệu ngay khi
  mở dialog). `observeExportWork` giờ bỏ qua work xong nếu repo không có ảnh nào mang kết quả (test
  `MainViewModelExportStateRestoreRoboTest`). **Chưa giải thích được:** dialog đôi lúc về lại "Xuất vào bộ
  sưu tập" sau khi quay về từ app đích khi cùng 1 phiên — chưa tái hiện lại được, không khẳng định đã hết.
  Đủ 12 locale. Test: 7 unit helper + 2 dialog.
- ✅ FEAT-28: Frame & Shadow Builder — khung thẻ bo góc + đổ bóng + nền màu (2026-10-03) —
  `CardFrameRenderer` (hàm thuần, BitmapShader + BlurMaskFilter), `CardFramePbFragment` (chip
  "Thẻ ảnh": switch + slider bo góc/đổ bóng + màu nền), áp trong `BatchExportEngine` SAU watermark/
  khung EXIF, TRƯỚC resize. Lưu trong `WaterMark` + profile (Room v3→v4, test migration).
  Smoke test thật Pixel 7 Pro: ảnh 1440×3120 → file 1758×3438 (padding 159px khớp `computeLayout`),
  góc bo + bóng + nền trắng đúng. Test: unit + Robolectric + widget + 3 instrumentation pass.
  Ước tính ở grid preview export cộng cả khung EXIF + khung thẻ (`PreviewResult.framedWidth/Height`
  từ `BatchExportEngine.resolveFramedSize`, smoke test: `1758×3438 · ~1,2 MB` khớp file thật). Preview
  editor không hiện khung (chỉ áp lúc export, đã ghi trong UI).
- ✅ FEAT-26: Dual Watermark Preset Text + Logo 2 góc (2026-10-02) — 4 preset 1-chạm
  (`DualWatermarkPreset`), `DualPresetBuilder.applyPreset` tái dùng `extraLayers` FEAT-03; chip
  "Dấu kép" + nút trong Layer Manager mở `DualPresetPickerBSDFragment`. `MainViewModel.applyDualPreset`
  ép tile CLAMP TRƯỚC khi áp config (neo 9-grid chỉ có hiệu lực ở CLAMP; áp cùng lúc sẽ bị
  `applyNewConfig` huỷ job → view vẫn vẽ REPEAT) rồi phát `UiState.ApplyAnchor`. Smoke test thật
  TECNO KJ7: dialog hiển thị đúng, layer chính về góc dưới phải (`onDraw CLAMP`). Thiếu logo →
  toast nhắc chọn logo. Đủ 12 locale. Test: unit + widget + Robolectric + instrumentation pixel.
- ✅ FEAT-25: Watermark Timestamp tự động từ EXIF (2026-09-30) — token `{time}`/`{datetime}`
  trong `TextTokenResolver`; `ExportNaming.buildBaseTokens` ưu tiên `TAG_DATETIME_ORIGINAL` →
  `TAG_DATETIME` → `TAG_DATETIME_DIGITIZED` (parse qua `parseExifDateTime`, hỗ trợ nhiều định
  dạng), fallback `queryFileLastModified` (MediaStore `DATE_TAKEN`/`DATE_MODIFIED` hoặc
  `File.lastModified()`) rồi mới tới `System.currentTimeMillis()`; chip chèn nhanh trong
  `EditTextContentFragment`. Smoke test thật trên device TECNO KJ7: watermark render đúng
  `{time}`→`17:28`, `{datetime}`→`2026-09-29 17:28`.
- ✅ FEAT-01: Position Anchor 9-grid (2026-09-05)
- ✅ FEAT-02: Naming template cho file xuất (2026-09-10)
- ✅ FEAT-03: Watermark đa lớp (2026-09-24)
- ✅ FEAT-04: Lịch sử batch gần đây (2026-09-22)
- ✅ FEAT-05: Backup/Restore Template & Signature qua SAF + ZIP (2026-09-16)
- ✅ FEAT-06: Watermark profile đầy đủ (2026-09-22)
- ✅ FEAT-07: Preview grid + ước tính dung lượng trước batch export (2026-09-13)
- ✅ FEAT-08: Chọn cả thư mục ảnh vào batch (2026-09-12)
- ✅ FEAT-09: Preset resize theo nền tảng mạng xã hội (2026-09-10)
- ✅ FEAT-10: Tự nhận diện hãng máy để gợi ý style EXIF (2026-09-13)
- ✅ FEAT-11: Hiệu ứng viền/bóng/nền pill cho text watermark (2026-09-12)
- ✅ FEAT-12: Undo/Redo editor (2026-09-22)
- ✅ FEAT-13: Caption riêng từng ảnh trong batch export (2026-09-24)
- ✅ FEAT-14: Tham số hoá EXIF frame style (2026-09-10)
- ✅ FEAT-15: Chọn thư mục XUẤT ảnh bằng SAF (2026-09-22)
- ✅ FEAT-16: Crop & Straighten tỉ lệ chuẩn trước watermark (2026-09-24)
- ✅ FEAT-17: Skip 1 ảnh tại grid preview export (2026-09-21)
- ✅ FEAT-18: So sánh trước/sau bằng slider (2026-09-22)
- ✅ FEAT-19: Chính sách xử lý trùng tên file khi export (2026-09-20)
- ✅ FEAT-20: Chia sẻ ZIP sau batch export (2026-09-20)
- ✅ FEAT-21: Dán ảnh từ Clipboard (2026-09-20)
- ✅ FEAT-22: Chụp ảnh từ Camera (2026-09-20)
- ✅ FEAT-23: Ghi nhớ vị trí watermark riêng theo portrait/landscape (2026-09-23)
- ✅ FEAT-24: MRU icon/logo gần đây (2026-09-22)

---

## ⏸️ Deferred

- ⏸️ BUG-14: VIP secret hardcode trong APK
- ⏸️ BUG-15: AdMob rewarded release dùng test ID
- ⏸️ ENH-17: VIP key device-bound
- ⏸️ IDEA-08: Referral VIP qua Share App
- ⏸️ IDEA-04: Cloud sync Brand Kit
- ⏸️ IDEA-05: Chợ template cộng đồng
- ⏸️ IDEA-11: Live Camera Watermark AR Preview
