---
id: IDEA-17
type: Idea
effort: L
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/BatchCaptionBSDialogFragment.kt
---

# Voice-to-text caption khi batch nhiều ảnh (mở rộng FEAT-13)

## Mô tả
FEAT-13 chỉ cho nhập/dán caption dạng text (nhiều dòng/CSV) — chưa có cách nhập bằng GIỌNG NÓI. Khi xử lý hàng chục ảnh liên tiếp, đọc to caption cho từng ảnh có thể nhanh hơn gõ tay, đặc biệt lúc đang thao tác 1 tay hoặc caption dài.

## Đề xuất
Thêm nút micro cạnh mỗi dòng trong `BatchCaptionBSDialogFragment`, dùng `SpeechRecognizer` on-device (Android built-in, không cần key ngoài) điền thẳng kết quả nhận diện vào đúng dòng caption tương ứng ảnh đang duyệt.

## Acceptance Criteria
- [x] Bấm micro, đọc to 1 câu caption cho ảnh đang duyệt — text nhận diện đúng điền vào đúng dòng caption của ảnh đó, không lẫn sang ảnh khác.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-17`, file ticket = `todo/IDEA-17-voice-to-text-caption-khi-batch-nhieu-anh-mo-rong-feat-13.md`.

---

## Kết quả kiểm chứng (2026-09-27)

### Đã làm
| File | Vai trò |
|---|---|
| `data/model/BatchCaptionParser.kt` | Thêm `lineIndexAt()` + `replaceLine()` thuần Kotlin — toàn bộ phần logic có thể sai nằm ở đây, unit test chạy thẳng trên JVM. |
| `ui/dlg/BatchCaptionBSDialogFragment.kt` | Nút mic dùng `RecognizerIntent` + `StartActivityForResult`, chốt dòng đích lúc bấm, nhãn "Đang nhập cho ảnh N/M" bám con trỏ. |
| `f_batch_caption_bottom_sheet.xml` | Nút mic M3 riêng ở hàng tiêu đề + nhãn dòng đích. Giữ nguyên `endIconMode="clear_text"` của FEAT-13. |
| `AndroidManifest.xml` | `<queries>` cho `android.speech.action.RECOGNIZE_SPEECH`. **Không** thêm `RECORD_AUDIO`. |
| `ic_mic.xml`, `values/` + `values-vi/strings.xml` | Icon mic + 3 string (2 locale). |

Theo lựa chọn của user: **`RecognizerIntent`** (không `SpeechRecognizer` → không cần quyền ghi âm,
không có recognizer phải `destroy()`) và **chèn vào dòng chứa con trỏ** (giữ ô multiline của FEAT-13,
không đổi sang RecyclerView để khỏi mất khả năng dán CSV hàng loạt).

Hai cái bẫy thật đã xử lý trong `replaceLine()`: transcript chứa xuống dòng bị làm phẳng thành dấu
cách (không thì một câu nói tách 2 dòng, đẩy lệch mọi caption phía sau sang nhầm ảnh), và transcript
mở đầu bằng `"` được encode CSV (không thì `validate()` báo `InvalidCsv`).

### Test (12 test mới, tất cả xanh)
- 9 unit thuần thêm vào `test/.../data/model/BatchCaptionParserTest.kt`: `lineIndexAt` (đầu input,
  giữa dòng, ngay sau `\n` vs ngay trước `\n`, dòng cuối rỗng, cursor âm/vượt biên); `replaceLine`
  (thay đúng dòng giữa giữ nguyên dòng khác, con trỏ trả về đúng cuối dòng vừa điền, chèn dòng trống
  khi input ngắn hơn `lineIndex`, transcript có `\n` bị làm phẳng, transcript mở đầu bằng `"`
  round-trip qua `validate()` ra đúng caption gốc).
- 3 Robolectric thêm vào `test/.../ui/dlg/BatchCaptionLayoutRoboTest.kt`: `btnVoice`/`tvVoiceTarget`
  tồn tại, nút mic có `contentDescription`, và `tilCaptions` vẫn giữ `END_ICON_CLEAR_TEXT` (chứng minh
  nút mic không tranh slot endIcon của FEAT-13).
- Không cần integration test: không đụng IO/DB/DataStore.

`./gradlew :app:testDebugUnitTest` xanh toàn bộ, `ktlintCheck` xanh, `assembleDebug` xanh.

### Smoke test thật (TECNO KJ7 `115333744A005844`, R3)
Chọn 3 ảnh → Export → "Chú thích hàng loạt" → nút mic hiện đúng góc phải hàng tiêu đề, nhãn
"Đang nhập cho ảnh 1/3" → gõ 3 dòng, nhãn tự đổi "3/3" theo con trỏ → tap dòng 1, nhãn về "1/3" →
tap dòng 2, uiautomator xác nhận `text="Đang nhập cho ảnh 2/3"` → bấm mic: dialog Google mở đúng,
locale tự khớp máy ("Tiếng Việt (Việt Nam)") → huỷ dialog: quay về sheet nguyên vẹn, 3 dòng không đổi,
nhãn vẫn "2/3". `logcat` không có `FATAL EXCEPTION`. Không gặp quảng cáo che UI.

Thiết bị smoke không có luồng mic thật nên không tạo được transcript để quan sát text rơi vào dòng.
Phần đó là ánh xạ transcript → dòng, đã phủ đầy đủ bằng unit test thuần (gồm cả 2 case bẫy ở trên);
phần nhận dạng giọng nói là engine của Google, không thuộc code này.

### Tự audit: 9.5/10
- Đạt acceptance criterion: dòng đích chốt bằng `selectionStart` THẬT lúc bấm mic và lưu vào field,
  callback không đọc lại — dialog hệ thống lấy mất focus cũng không thể lẫn sang dòng khác.
- R5: không magic number (dòng tính từ input, mọi chuỗi ở resource), không `late`/force-unwrap mới,
  không có resource nào phải dispose (đây chính là lý do chọn `RecognizerIntent`).
- Không phá FEAT-13: endIcon clear_text còn nguyên (có test canh), mô hình CSV multiline giữ nguyên.
- Máy không có app nhận dạng → nút mic ẩn; `ActivityNotFoundException` vẫn bọc try/catch phòng ROM
  khai báo có nhưng không mở được.
- Trừ 0.5: chưa quan sát được transcript thật rơi vào dòng trên device (thiếu mic), và nhãn dòng đích
  không bám theo phím mũi tên bàn phím cứng — trần đã ghi `ponytail:` comment, không ảnh hưởng đúng/sai.
