---
id: BUG-09
type: Bug
priority: P1
effort: S
sources: Codex, Internal (2/4, verify trực tiếp xác nhận đúng)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainActivity.kt
verified: true
---

# Nhận ảnh share (`ACTION_SEND`) thiếu `EXTRA_STREAM`, lặp/rơi ảnh

## Mô tả
Đã đọc trực tiếp, xác nhận:
```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    this.intent = intent   // chỉ replace, KHÔNG xử lý ảnh
}

override fun onStart() {
    super.onStart()
    if (intent?.action == ACTION_SEND && intent?.data != null) {
        dealWithImage(listOf(intent?.data!!))
    }
}
```

2 vấn đề:
1. **Chỉ đọc `intent.data`**: hệ thống Android share sheet (share ảnh từ Gallery/app khác qua `ACTION_SEND`, type `image/*`) đặt URI ảnh ở `Intent.EXTRA_STREAM`, không phải `intent.data`. Chỉ check `data` bỏ sót phần lớn trường hợp share ảnh thực tế.
2. **`onNewIntent` không xử lý ngay, chỉ `onStart` xử lý**: nếu app đang mở sẵn ở foreground (`launchMode` khiến hệ thống gọi `onNewIntent` thay vì tạo Activity mới → `onNewIntent` được gọi nhưng KHÔNG kèm `onStart` lại vì Activity chưa từng stop), ảnh share vào bị bỏ qua hoàn toàn (không có nơi nào gọi `dealWithImage`).
3. Ngược lại, mỗi lần user background rồi mở lại app bình thường (home button → mở lại, không qua share), `onStop→onStart` chạy lại nhưng `intent` cũ (từ lần share trước, vẫn còn `action == ACTION_SEND`) chưa được "tiêu thụ" → `dealWithImage` chạy lại với ảnh cũ, ghi đè lựa chọn hiện tại của người dùng.

## Cách fix đề xuất
- Đọc cả `intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)` khi `intent.data == null`.
- Gọi xử lý ảnh trực tiếp trong `onNewIntent` (không đợi `onStart`).
- Sau khi xử lý xong, "tiêu thụ" intent: `setIntent(Intent(this, MainActivity::class.java))` hoặc set `intent?.action = null` để `onStart` không lặp lại xử lý.

## Acceptance Criteria
- [ ] Share ảnh từ Gallery vào app (app đang đóng) hoạt động đúng.
- [ ] Share ảnh vào app khi app đang mở sẵn ở foreground cũng nhận được ảnh.
- [ ] Background rồi mở lại app không tự động re-import ảnh share cũ.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-09`, file ticket = `todo/BUG-09-action-send-thieu-extra-stream.md`.
