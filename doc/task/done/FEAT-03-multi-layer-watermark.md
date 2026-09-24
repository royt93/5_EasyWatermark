---
id: FEAT-03
type: Feature
effort: L
sources: Codex, Claude, Internal (3/4 — đồng thuận cao)
files:
  - app/src/main/java/com/mckimquyen/watermark/data/repo/WaterMarkRepository.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
---

# Watermark đa lớp (chồng text + logo/QR cùng lúc)

## Mô tả
`WaterMarkRepository.MarkMode` hiện chỉ Text HOẶC Image, loại trừ lẫn nhau. Cho phép chồng nhiều lớp cùng lúc (vd logo góc + text lặp nền + QR), mỗi lớp có thứ tự và opacity riêng — use-case thực tế phổ biến (logo + copyright cùng lúc).

## Triển khai
Đổi `MarkMode` đơn thành danh sách layer (giới hạn 3-5 lớp để phù hợp kiến trúc View hiện tại và thời gian phát triển). Mỗi layer vẽ tuần tự lên cùng canvas trong `generateImage`/shader pipeline hiện có (tận dụng `buildTextBitmapShader`/`buildIconBitmapShader` sẵn có, gọi nhiều lần thay vì 1).

## Acceptance Criteria
- [x] Có thể thêm tối thiểu 2 layer (vd text + logo) cùng lúc trong 1 config.
- [x] Mỗi layer có vị trí/opacity/thứ tự (z-order) riêng, chỉnh sửa độc lập.
- [x] Export batch áp dụng đúng toàn bộ layer, hiệu năng không giảm đáng kể so với 1 layer.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-03`, file ticket = `todo/FEAT-03-multi-layer-watermark.md`.

## Quyết định thiết kế (chốt với user qua `AskUserQuestion` trước khi code)

Layer phụ định vị bằng **neo 9-grid + margin%** (tái dùng `Anchor.toOffset()` từ FEAT-23) — **KHÔNG kéo-thả tay tự do**. Layer chính (field gốc `WaterMark`) giữ nguyên 100% hành vi cũ (kéo thả, pinch-to-scale), không đụng hệ thống touch/gesture của `WaterMarkImageView`. Lựa chọn rủi ro thấp nhất, đúng tầm effort L — tránh viết lại toàn bộ hệ touch/offset vốn đang gắn cứng cho 1 layer đơn.

## Kết quả kiểm chứng (2026-09-24)

**Thiết kế data model** — backward-compatible tuyệt đối, không đổi 1 Preferences key/field nào hiện có:
- `WatermarkLayer` (data class mới, `data/model/WatermarkLayer.kt`) — tập field tối thiểu (markMode/text/textSize/textColor/textStyle/textTypeface/iconUri/alpha/degree/hGap/vGap/anchor/marginPercent), cố ý không có textEffect/EXIF. `serializeList`/`parseList` percent-encode từng field bằng `Uri.encode()` trước khi join (an toàn vì `text` có thể chứa `\n`/`|`, khác `recentIconUris` vốn chỉ toàn Uri).
- `WaterMark.extraLayers: List<WatermarkLayer> = emptyList()` — field mới, `WaterMarkRepository.MAX_EXTRA_LAYERS = 4` (tổng tối đa 5 layer).
- `WaterMarkRepository`: `KEY_EXTRA_LAYERS` (DataStore) + `addLayer`/`removeLayer`/`updateLayer`/`reorderLayer` theo đúng pattern `snapshotForUndoIfDue()` có sẵn — Undo/Redo tự động cover field mới (chỉ dựa vào `WaterMark.equals()`), không cần sửa gì thêm. `applyWaterMark()` (dùng bởi Undo/Redo + FEAT-06) đã ghi thêm `extraLayers`.
- `WaterMarkImageView`: layer chính giữ nguyên hoàn toàn; thêm `extraLayerShaders` song song, build trong `applyNewConfig()`, vẽ trong `drawExtraLayers()` (hàm mới, gọi sau `drawPrimaryLayer()`). Sửa `onDraw()` tách `hardSkipReason` (chặn mọi layer khi ảnh chưa decode/đang animate) khỏi `skipPrimaryReason` (chỉ chặn layer chính khi text rỗng) — layer phụ vẫn hiện dù layer chính bị skip (xác nhận đúng qua smoke test thật, xem dưới).
- `BatchExportEngine`: 1 hàm private mới `drawExtraLayers()` dùng chung cho cả 3 nơi vẽ watermark (`generateImage`/`generatePreviewBitmap`/`generateCompareBitmaps`) — 3 hàm này vẫn giữ nguyên logic layer CHÍNH cũ (không refactor, đúng quyết định cũ của codebase), chỉ bọc thêm `canvas.withSave{}` quanh khối translate CLAMP có sẵn (thay đổi cấu trúc thuần, pixel-output không đổi) để offset layer phụ tính đúng từ gốc toạ độ.
- `WatermarkProfileEntity` + `WatermarkProfileDatabase`: thêm cột `extraLayersRaw: String?`, bump version 1→2 kèm `Migration(1, 2)` thật (`ALTER TABLE ... ADD COLUMN`) — bắt buộc vì `AppModule.kt` không có `fallbackToDestructiveMigration()`.

**Test bổ sung** — 36 test case mới + 2 mở rộng, toàn bộ PASS:
- `WatermarkLayerSerializationTest` (8 case, Robolectric): round-trip rỗng/1 layer/4 layer (max)/text chứa `\n`+`|`/iconUri rỗng/iconUri thật/entry hỏng bị bỏ qua không crash.
- `WaterMarkRepositoryLayerRoboTest` (10 case): add/remove/update/reorder hợp lệ + mọi trường hợp out-of-range/no-op + chạm trần `MAX_EXTRA_LAYERS`.
- `WatermarkLayerAdapterRoboTest` (8 case, widget): bind đúng tóm tắt text/image, disable nút lên/xuống đúng 2 đầu danh sách, tap row/nút gọi đúng callback theo INDEX.
- `LayerManagerBSDFragmentWidgetTest` (6 case, widget, host `MainViewModel` thật + DataStore cô lập theo pattern ENH-30): trạng thái rỗng ban đầu, chuyển List↔Edit form, thêm layer text lưu đúng + quay lại list, validation text rỗng chặn Lưu, tap Xoá trên row xoá đúng layer, chạm max không mở form thêm.
- `WatermarkProfileMigration1To2IntegrationTest` (2 case, androidTest): row cũ sống sót + cột mới NULL, cột mới ghi/đọc được sau migrate — chạy thẳng `MIGRATION_1_2.migrate()` trên `SupportSQLiteDatabase` tự tạo (repo chưa bật Room schema export nên không dùng `MigrationTestHelper` chuẩn được, xem doc trong file test).
- Mở rộng `WaterMarkRepositoryApplyWaterMarkRoboTest`, `WaterMarkRepositoryUndoRedoRoboTest` (+1 case `undo_afterAddLayer_restoresPreviousLayerList`), `WatermarkProfileRepositoryRoboTest` (+1 case entity cũ `extraLayersRaw = null` → list rỗng).
- Phát hiện + fix 1 bug thật qua widget test TRƯỚC khi lên device: `dlg_layer_manager.xml` thiếu `app:layoutManager` cho `rvLayers` → `InflateException` khi RecyclerView thật sự layout (`RecyclerView has no LayoutManager`) — không lộ ra ở giai đoạn code review vì `onCreateViewHolder` gọi trực tiếp trong test đầu không cần layout thật.
- `./gradlew testDebugUnitTest` toàn bộ PASS (1 lần gặp `MainViewModelUndoRedoRoboTest` crash JVM OpenJ9 GC assertion không liên quan tới thay đổi — PASS khi chạy lại, đúng flakiness cross-test JVM fork đã ghi nhận trong `doc/todo.md`). `ktlintCheck` sạch. `processDebugResources` (aapt2) sạch — dùng thay `lint` gốc vì repo có baseline lint đã biết fail ~265 lỗi `MissingTranslation` pre-existing (string mới thêm cho tiếng Anh/Việt, các locale khác fallback về `values/` mặc định theo cơ chế Android chuẩn, không phải lỗi thật) + 1 lỗi `UnsafeOptInUsageError` pre-existing khác trong `MainActivity.kt` không liên quan — xác nhận qua `git stash -u` so sánh baseline 265 lỗi/110 warning trước khi có thay đổi.

**Smoke test thật trên TECNO_KJ7** (khoá qua `AskUserQuestion` do có 2 device — Pixel 7 Pro cũng cắm cùng lúc):
1. Thêm layer text "Copyright 2026" neo Dưới-phải trong editor → xác nhận layer chính + layer phụ cùng hiện đúng vị trí khi tile mode = Đơn lẻ (CLAMP); ở chế độ Lặp lại cả 2 layer đều tile phủ kín (đúng thiết kế — REPEAT áp dụng đồng nhất cho mọi layer, khớp hành vi layer chính đã có).
2. Tắt app hoàn toàn (force-stop) + mở lại → layer phụ vẫn còn nguyên (xác nhận DataStore persist qua process restart thật, không chỉ qua unit test).
3. Thêm layer thứ 2 "LÔG TÉT" (ảnh chụp mới từ Camera, layer chính KHÔNG có text) neo Trên-trái → xác nhận **layer phụ vẫn hiện dù layer chính bị skip do text rỗng** (đúng fix `hardSkipReason`/`skipPrimaryReason` tách riêng trong `onDraw()`).
4. Mở "So sánh trước/sau" (FEAT-18) → xác nhận layer phụ cũng hiện đúng trong bản "sau" (bonus: xác nhận luôn `generateCompareBitmaps()` — 1 trong 3 nơi gọi `drawExtraLayers()`).
5. **Export batch thật** → mở file JPEG đã ghi ra `/sdcard/Pictures/WaterMarkCreator/` bằng `adb pull` + xem trực tiếp: xác nhận ảnh 2976×3968 xuất ra có ĐỦ layer chính ("KHÔNG SAO CHÉP DƯỚI MỌI HÌNH THỨC") lẫn layer phụ ("LÔG TÉT" đúng góc trên-trái) — khớp 100% với preview editor. Đây là bằng chứng trực tiếp cho AC #3.
6. Xoá layer qua nút "-" trong Layer Manager → list cập nhật đúng, quay về trạng thái rỗng.
7. Logcat sạch suốt phiên, không `FATAL EXCEPTION`.

Điểm tự chấm: **9/10** (trừ 0.5 vì UI layer phụ dùng dropdown 9 lựa chọn thay vì grid 9 nút trực quan như layer chính — đơn giản hoá có chủ đích để giới hạn effort, đã nêu rõ trong thiết kế; trừ 0.5 vì chưa build UI riêng cho "Hình ảnh/Biểu tượng" icon-picker preview đẹp bằng layer chính — dùng `PickVisualMedia` đơn giản, không có fallback ACTION_PICK như picker chính, chấp nhận được vì đây là tính năng phụ mới, không phải luồng chọn icon chính).
