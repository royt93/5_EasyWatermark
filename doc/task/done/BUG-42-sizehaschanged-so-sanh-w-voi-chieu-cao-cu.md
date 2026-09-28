---
id: BUG-42
type: Bug
priority: P2
effort: XS
sources: Claude self-audit 2026-09-27 (3 file cùng 1 dòng sai y hệt — lỗi copy-paste)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/CircleImageView.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/ColoredImageVIew.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/ProgressImageView.kt
---

# `sizeHasChanged = w != oldh || h != oldh` — so chiều RỘNG mới với chiều CAO cũ (3 view)

## Mô tả
Cùng 1 dòng sai lặp ở 3 custom view (`CircleImageView.kt:44`, `ColoredImageVIew.kt:79`,
`ProgressImageView.kt:57`):

```kotlin
override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    sizeHasChanged = w != oldh || h != oldh   // oldw không bao giờ được dùng
}
```

Đúng phải là `w != oldw || h != oldh`. Hệ quả thực tế:
- **View gần vuông** (`CircleImageView` dùng cho avatar/logo tròn trong `a_about.xml:915,974`, cỡ
  48-64dp vuông): `w == oldh` ⇒ `sizeHasChanged = false` dù kích thước ĐÃ đổi →
  `CircleImageView.onSizeChanged` `return` sớm (dòng 45-47), **không tạo lại `destCircleBitmap`**
  theo kích thước mới → mask tròn sai cỡ so với ảnh (bo méo/lệch) sau mỗi lần view resize.
- Ngược lại, view đổi từ `WxH` sang `HxW` (đúng lúc `w == oldh` và `h == oldw`) cũng bị bỏ qua.
- `ProgressImageView`/`ColoredImageVIew`: gán rồi gần như không dùng đúng nghĩa (`ColoredImageVIew`
  vẫn `toBitmap()` mỗi `onDraw` bất chấp cờ) — nợ logic, nhưng sai cùng gốc, sửa 1 lượt.

Ghi chú: `ColoredImageVIew`/`Toolbar.kt` hiện là dead code (đã ghi nhận trong BACKLOG "Audit M3 lần
3") — sửa cho nhất quán, không cần smoke test riêng; `CircleImageView` là chỗ có màn hình THẬT
(About) để verify.

## Cách fix đề xuất
Sửa `oldh` → `oldw` ở vế đầu tại cả 3 file. Nhân dịp này gỡ hẳn field `sizeHasChanged` ở view nào
không thật sự dùng tới nó (tránh để lại cờ chết gây hiểu nhầm lần sau).

## Acceptance Criteria
- [x] 3 file dùng đúng `w != oldw || h != oldh`.
- [x] Unit/widget test: gọi `onSizeChanged(100, 100, 50, 100)` (w đổi, h giữ, `w == oldh` ở bản cũ) → cờ = true.
- [x] `CircleImageView` tạo lại `destCircleBitmap` đúng kích thước mới trong case trên.
- [x] Smoke test About: 2 ảnh tròn render đúng mask, không méo.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-42`, file ticket = `todo/BUG-42-sizehaschanged-so-sanh-w-voi-chieu-cao-cu.md`.

## Kết quả kiểm chứng (2026-09-28)

**Fix:** sửa `w != oldh` → `w != oldw` ở cả 3 file (`CircleImageView`/`ColoredImageVIew`/
`ProgressImageView`). `ProgressImageView` gỡ hẳn field `sizeHasChanged` + override `onSizeChanged`
rỗng (field không đâu đọc lại — dead code thật, không phải chỉ sai công thức). `CircleImageView`
đổi `destCircleBitmap` thành `internal` (private set) và `ColoredImageVIew` đổi `sizeHasChanged`
thành `internal` để test truy cập trực tiếp (theo đúng pattern `GalleryFragment.pendingIncludeSubfolders`
đã có).

- **Điểm tự audit:** 9.5/10 — đúng đề xuất ticket + dọn thêm field chết ở ProgressImageView, diff
  tối thiểu, verify RED/GREEN bằng cách revert tạm công thức để xác nhận test thật sự bắt được bug.
- **Test:** `CircleImageViewSizeChangedWidgetTest` (2 case, dùng `View.layout()` thật trigger
  `onSizeChanged()` qua `setFrame()` — không gọi trực tiếp) + `ColoredImageVIewWidgetTest` (+1 case
  mới, giữ nguyên 1 case cũ không regression). RED verify: revert tạm công thức về `oldh` → cả 2
  test FAIL đúng (BUILD FAILED) → khôi phục fix → GREEN. `./gradlew :app:testDebugUnitTest` không
  regression.
- **Smoke test thật** trên device đã khoá session — **TECNO KJ7 (115333744A005844)**: About screen
  — avatar tròn (logo app) render đúng mask tròn, không méo/lệch. `ColoredImageVIew` là dead code
  (đã ghi nhận BACKLOG "Audit M3 lần 3") không có màn hình thật — chỉ verify qua widget test.
