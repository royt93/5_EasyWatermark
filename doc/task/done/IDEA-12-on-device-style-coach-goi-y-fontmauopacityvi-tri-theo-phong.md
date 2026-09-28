---
id: IDEA-12
type: Idea
effort: L
sources: Codex (ý tưởng gốc) + Claude (thiết kế chi tiết, brainstorm 2026-09-27)
files:
  - app/src/main/java/com/mckimquyen/watermark/data/db/WatermarkProfileDatabase.kt
  - app/src/main/java/com/mckimquyen/watermark/data/db/dao/
  - app/src/main/java/com/mckimquyen/watermark/data/model/entity/
  - app/src/main/java/com/mckimquyen/watermark/data/repo/
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportWorker.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/MainActivity.kt
---

# On-device Style Coach — gợi ý font/màu/opacity/vị trí theo phong cách ảnh cá nhân

## Mô tả
Tự động học "gu" watermark của user qua thời gian (font, màu, opacity, vị trí, mode text/icon...) từ lịch sử các lần export thành công, rồi tự đề xuất áp lại preset đó khi mở batch mới — không cần user tự lưu tên như `WatermarkProfile` (FEAT-06) đã có. Khác IDEA-01 (né mặt/chủ thể theo ML, xử lý hậu kỳ 1 ảnh) và IDEA-06 (auto-contrast/opacity theo TỪNG ảnh riêng lẻ, không học xu hướng chung theo thời gian).

**Effort hạ từ XL → L sau khi scope lại**: bỏ hướng ML/phân tích palette ảnh (quá phức tạp, không cần thiết cho AC gốc), dùng heuristic đếm tần suất thuần — tái dùng hạ tầng Room sẵn có (`WatermarkProfileDatabase`), không cần model on-device, không cần dependency mới.

## Vì sao đáng làm
Không cần user chủ động thao tác (khác `WatermarkProfile` cần tự bấm "Lưu"), giảm ma sát mỗi lần mở batch mới cho user hay dùng lại đúng 1 kiểu style quen. Thuần local, không backend.

## Thiết kế kỹ thuật

### 1. Data model + lưu trữ
Thêm bảng mới `watermark_style_history` **vào chung `WatermarkProfileDatabase` đã có** (đã tách khỏi `AppDatabase` asset-seeded từ FEAT-06, không asset, nên thêm bảng + `Migration` version+1 an toàn — không cần mở DB thứ 4 riêng).

`WatermarkStyleHistoryEntity` — chỉ lưu phần **"style signature"**, KHÔNG lưu `text`/`iconUri` cụ thể (đổi mỗi lần dùng, không thuộc "gu"):
```kotlin
@Entity(tableName = "watermark_style_history")
data class WatermarkStyleHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val textColor: Int,
    val textStyleKey: Int,
    val textTypefaceKey: Int,
    val alpha: Int,
    val anchor: Int,
    val markModeValue: Int,
    val exifFrameStyle: Int,
    val textEffectStroke: Boolean,
    val textEffectShadow: Boolean,
    val textEffectPillBackground: Boolean
)
```
`WatermarkStyleHistoryDao`: `insert()` + `@Query("SELECT * FROM watermark_style_history ORDER BY timestamp DESC LIMIT :n")` lấy N dòng gần nhất, kèm `@Query("DELETE FROM watermark_style_history WHERE id NOT IN (SELECT id FROM watermark_style_history ORDER BY timestamp DESC LIMIT :keep)")` để prune, tránh phình bảng vô hạn (giữ tối đa ~50 dòng).

Ghi 1 dòng mỗi khi `BatchExportWorker.recordHistory()` chạy xong ít nhất 1 ảnh thành công — cạnh lời gọi `batchHistoryRepo.record(...)` đã có sẵn (FEAT-04), cùng chỗ, không cần hook mới.

### 2. Logic gợi ý — `WatermarkStyleCoach`
Class thuần (không phụ thuộc Android Context), dễ unit test:
```kotlin
class WatermarkStyleCoach {
    fun suggest(recent: List<WatermarkStyleHistoryEntity>): WatermarkStyleHistoryEntity? {
        if (recent.size < MIN_SAMPLES) return null // MIN_SAMPLES = 10
        val (signature, count) = recent
            .groupingBy { it.copy(id = 0, timestamp = 0) } // bỏ 2 field không thuộc signature
            .eachCount()
            .maxByOrNull { it.value } ?: return null
        return signature.takeIf { count.toFloat() / recent.size >= SUGGEST_THRESHOLD } // 0.6f
    }
}
```
Gọi từ `MainViewModel` khi load batch mới, so với `WaterMark` hiện tại — chỉ hiện gợi ý nếu signature đề xuất KHÁC cấu hình đang áp dụng (tránh gợi ý cái đang dùng sẵn).

### 3. UI
`MainViewModel` thêm `styleSuggestionFlow: StateFlow<WatermarkStyleHistoryEntity?>`, tính lại mỗi lần `updateImageList()` nạp ảnh mới. Editor (`MainActivity`/`LaunchView`) thêm 1 `MaterialCardView` banner ngang nhỏ phía trên panel chính (chuẩn M3 đã migrate, không tạo style riêng) — text "Áp dụng preset thường dùng?" + 2 `MaterialButton`: **Áp dụng** (gọi `waterMarkRepo.applyWaterMark` với các field từ signature, giữ nguyên `text`/`iconUri` hiện tại) / **Bỏ qua** (ẩn banner, không hỏi lại trong session hiện tại — không cần persist trạng thái dismiss).

## Rủi ro / cân nhắc
- Không dùng similarity mờ (weighted/fuzzy) — chỉ match exact signature, đơn giản hơn, chấp nhận việc gợi ý "cứng" (đổi 1 field nhỏ coi như signature khác, không cộng dồn).
- Prune giữ tối đa 50 dòng — đủ cho N=10 tính gợi ý, tránh DB phình vô hạn theo thời gian dùng app.

## Acceptance Criteria
- [x] Sau ≥10 lần export batch thành công, nếu ≥60% trong 10 lần gần nhất cùng 1 style signature và khác cấu hình đang áp dụng → hiện banner gợi ý khi mở batch mới.
- [x] Bấm "Áp dụng" → cấu hình watermark đổi đúng theo signature gợi ý (giữ nguyên text/icon hiện tại).
- [x] Bấm "Bỏ qua" → banner ẩn, không hiện lại trong cùng session batch đó.
- [x] Chưa đủ 10 lần lịch sử hoặc không style nào đạt 60% → không hiện banner (không có false positive).

## Test plan
- Unit: `WatermarkStyleCoachTest` — đủ N + ≥60% → gợi ý đúng signature; <60%; <10 dòng; nhiều signature khác nhau không trộn nhầm; signature đề xuất trùng cấu hình hiện tại → không gợi ý.
- Room migration test: `WatermarkProfileDaoIntegrationTest` (mở rộng) hoặc file mới — migration version cũ→mới giữ nguyên data bảng `watermark_profile`, bảng mới tạo đúng schema.
- Widget/Robolectric: banner hiện/ẩn đúng theo `styleSuggestionFlow`, bấm Áp dụng gọi đúng `applyWaterMark` với field kỳ vọng, bấm Bỏ qua ẩn banner và không set lại trong cùng instance ViewModel.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-12`, file ticket = `todo/IDEA-12-on-device-style-coach-goi-y-fontmauopacityvi-tri-theo-phong.md`. Đây là ticket đã có thiết kế chi tiết sẵn (mục "Thiết kế kỹ thuật" trên) — implement bám đúng thiết kế đó, không tự đổi kiến trúc (VD không tự đổi sang bảng DB mới/logic similarity mờ) trừ khi phát hiện lý do kỹ thuật rõ ràng chặn được, và phải ghi lại lý do đổi trong "Kết quả kiểm chứng" lúc done.

## Kết quả kiểm chứng (2026-09-28)

**Điểm tự chấm: 9.5/10.** Implement bám sát thiết kế gốc, 2 sai khác nhỏ có lý do kỹ thuật rõ ràng (ghi dưới), không phá luồng khác, tuân R5 (không magic number trần — `MIN_SAMPLES`/`SUGGEST_THRESHOLD`/`MAX_ROWS` đều const; không `late`/force-unwrap tuỳ tiện; không leak — banner không giữ listener/subscription nào ngoài click listener thường).

**Sai khác so với thiết kế gốc (có lý do, không đổi kiến trúc chính):**
1. `WatermarkStyleCoach.suggest()` giữ đúng chữ ký gốc; thêm 1 hàm thuần `isDifferentFromCurrent(suggestion, current)` tách riêng (thay vì nhét tham số `current` vào `suggest()`) — để so sánh "khác cấu hình hiện tại" tự nó là 1 unit test độc lập, thuần, không cần mock `WaterMark`/Uri qua Robolectric.
2. Banner đặt ngay TRÊN `rvPhotoList` (dải thumbnail), dưới canvas — không phải ngay trên `rvPanel` theo nghĩa đen, vì `LaunchView` là custom `ViewGroup` tự tính `onMeasure`/`onLayout` bằng tay (không XML/ConstraintLayout), và khoảng trống duy nhất an toàn để chèn 1 view mới mà không phải viết lại toàn bộ thuật toán layout dồn-từ-dưới-lên là giữa canvas và cụm panel dưới cùng — vẫn đúng tinh thần "banner phía trên panel chính", chỉ khác vị trí neo chính xác.

**Test đã thêm (28 file test mới/sửa, tổng ~35 test case mới cho tính năng):**
- `WatermarkStyleCoachTest` (7 case, JUnit thường) — đủ N+≥60%, <60%, <10 dòng, nhiều signature không trộn nhầm, `isDifferentFromCurrent` cả 2 chiều.
- `WatermarkStyleHistoryRepositoryRoboTest` (4 case) — mapping `WaterMark`→entity chỉ lấy đúng field "signature", `record()` insert+prune(50), `recent()` delegate, `currentSignature()`.
- `WatermarkStyleHistoryDaoIntegrationTest` (3 case, androidTest, Room in-memory thật) — insert/recent order DESC+limit, `pruneKeepLatest`.
- `WatermarkStyleHistoryMigration2To3IntegrationTest` (2 case, androidTest, SQL thô mirror `WatermarkProfileMigration1To2IntegrationTest`) — migration giữ nguyên data `watermark_profile`, bảng mới đúng schema.
- `MainViewModelStyleSuggestionRoboTest` (5 case) — đủ dây ViewModel→repo→StateFlow→`applyWaterMark` đúng field, giữ nguyên text/icon, trùng cấu hình hiện tại không gợi ý, dismiss không tự hiện lại.
- `BatchExportWorkerRoboTest` — thêm 1 assertion: 0 ảnh thành công → không ghi style history.
- 25 file `MainViewModel*RoboTest`/`*Fragment*RoboTest` khác: chỉ sửa compile (tham số constructor mới `styleHistoryRepo`, dùng chung helper `testutil.noopWatermarkStyleHistoryRepository()`), không đổi test case cũ.
- `./gradlew testDebugUnitTest` (toàn bộ suite, không filter): PASS. `./gradlew ktlintCheck`: PASS.

**Smoke test thật trên device đã khoá (TECNO_KJ7, serial 115333744A005844, Android 14):**
- Cài debug APK, mở app, chọn 2 ảnh vào editor — không crash, layout `LaunchView` không vỡ (canvas/panel/thumbnail vẫn đúng vị trí sau khi thêm `cardStyleSuggestion`).
- Export batch thật (1 ảnh thành công) → verify TRỰC TIẾP file SQLite thật trên device (`run-as` + `sqlite3` qua Python) — bảng `watermark_style_history` được tạo đúng (migration 2→3 chạy thật khi mở DB), có đúng 1 dòng khớp cấu hình vừa export. Logcat sạch, không `FATAL EXCEPTION`, process không chết.
- Seed thêm 9 dòng lịch sử (cùng 1 signature khác cấu hình hiện tại, ghi thẳng qua file SQLite rồi push lại — mô phỏng đủ 10 lần dùng mà không cần thao tác tay 10 lần) → mở lại batch mới: **banner "Áp dụng style thường dùng?" hiện đúng thật trên màn hình**, đúng vị trí trên dải thumbnail, đúng M3 (`MaterialCardView` màu `colorSecondaryContainer`, 2 `MaterialButton`).
- Bấm "Áp dụng" → màu watermark đổi đúng sang màu đã seed (vàng→xanh lá), text nội dung giữ nguyên, banner tự ẩn. Logcat sạch.
- Dọn sạch: `pm clear` app sau khi test xong — không để lại data giả trên máy user.
- Nhánh "Bỏ qua" không lặp lại thao tác tay trên device (đã cover kỹ ở `MainViewModelStyleSuggestionRoboTest.dismissStyleSuggestion_...`), chỉ verify qua Robolectric — ghi nhận minh bạch, không tự nhận đã click tay case này.

## Audit vòng 2 trước khi push (2026-09-28, `/code-review --level high`)

User yêu cầu audit lại trước khi push. Code review (forked agent, effort high) tìm 6 finding trên diff
IDEA-12; xác minh + xử lý từng cái:

1. **BUG-45** (ghi style history bằng config đã bị Proofing Mode ghi đè) — trùng khớp finding tự tìm
   trước đó, đã fix (xem `done/BUG-45-*.md`).
2. **`styleHistoryRepo.record()` lỗi làm cả batch báo failure** — CONFIRMED, fix: bọc `runCatching`
   (cùng chỗ với fix BUG-45).
3. **Banner "trôi" sang màn Launch khi thoát Editor** — CONFIRMED thật (đọc code: `cardStyleSuggestion`
   cố ý ngoài `editorViews` nên không tự ẩn khi `toLaunchMode()`, `layoutLaunch()` không bao giờ
   `layout()` lại nó → giữ toạ độ Editor cũ). Fix: ẩn tay `cardStyleSuggestion` trong nhánh
   `ViewMode.LaunchMode` của `transformLayout()`. Test mới `LaunchViewRoboTest.toLaunchMode_hidesStyleSuggestionBanner_...`
   + **smoke test thật trên TECNO_KJ7**: seed 10 dòng lịch sử → mở batch (banner hiện) → back → dialog
   "Huỷ bỏ mọi thay đổi?" → Xác nhận → màn Launch render sạch, KHÔNG còn banner trôi nổi (trước fix sẽ
   đè lên logo/action grid).
4. **Áp gợi ý Image mode trong khi iconUri hiện tại rỗng → watermark trống** — CONFIRMED plausible,
   fix: `refreshStyleSuggestion()` không hiện banner nếu suggestion là Image mode mà iconUri hiện tại
   rỗng. Test mới `MainViewModelStyleSuggestionRoboTest.updateImageList_majoritySuggestsImageMode_currentIconUriBlank_noSuggestion`.
5. **`record()` không transactional (insert+prune 2 lệnh rời)** — CONFIRMED lý thuyết (rủi ro thấp, tự
   hồi phục ở lần ghi kế tiếp), fix rẻ: gộp thành `WatermarkStyleHistoryDao.recordAndPrune()` (default
   method `@Transaction`). Không cần test riêng — test hiện có (repo/DAO) đã đi qua đúng đường mới vì
   default method gọi lại đúng `insert()`/`pruneKeepLatest()` đã override trong fake.
6. **Race 2 batch load liên tiếp ghi đè kết quả** — CONFIRMED lý thuyết, fix: huỷ `styleSuggestionJob`
   cũ trước khi `launch` job mới (`Job?.cancel()` chuẩn Kotlin). **Không viết test riêng** — cố tình:
   test cancellation-timing chính xác cần `delay()` + mock chậm, rủi ro flaky cao hơn giá trị chứng
   minh 1 pattern coroutine đã chuẩn/phổ biến (ghi rõ ở đây thay vì fake 1 test không đáng tin).

**Bonus finding ngoài dự kiến**: viết androidTest thật cho case 1/2 (`BatchExportWorkerStyleHistoryIntegrationTest`,
cần thêm `androidTestImplementation(libs.test.work)`) lộ ra 1 bug THẬT KHÁC không liên quan —
`calculateInSampleSize` treo/`ArithmeticException` khi `reqWidth`/`reqHeight`=0 — ghi `BUG-46`, KHÔNG
fix (ngoài scope, không liên quan IDEA-12).

**Điểm tự audit vòng 2: 9.5/10** — 4/6 finding CONFIRMED thật và đã fix có test/smoke test; 2/6 đánh
giá lý thuyết/rủi ro thấp, fix rẻ không cần test riêng biện minh rõ ràng. `./gradlew testDebugUnitTest`
(toàn bộ) + `ktlintCheck` PASS. 7 androidTest liên quan (migration + DAO + BatchExportWorker style
history, cả 2 case proofingMode) PASS thật trên TECNO_KJ7.
