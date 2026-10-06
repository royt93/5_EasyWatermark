# BUG-77: Accessibility — touch target <48dp + missing stateDescription

**Priority:** P2 (a11y)  
**Effort:** S (2-3h)  
**Status:** todo

## Vấn đề

Material Design minimum touch target = 48dp × 48dp (accessibility WCAG). Hiện app có:

1. **item_saving_image.xml:**
   - `ivDone`: 32×32dp (tag button)
   - `ivCompliance`: 28×28dp (badge tap để... gì?)
   - `ivSkipToggle`: 28×28dp (toggle skip, FEAT-17, cần stateDescription "skipped"/"active")

2. **item_image_gallery.xml:**
   - `cbImage`: 28×28dp (chọn/bỏ chọn ảnh)

3. **item_watermark_layer.xml:**
   - `btnMoveUp/btnMoveDown/btnDelete`: 36×36dp (BUG-75 fix)

4. **stateDescription:**
   - `ivSkipToggle` (toggleable, 2 state "Bỏ qua" / "Xuất") — hiện chỉ có contentDescription

## Root cause

Thiết kế mặc định của adapter tiles dùng icon nhỏ gọn. Không tính tới a11y minimum 48dp.

## Fix

### Cách 1: thêm `android:minHeight="48dp" android:minWidth="48dp"`
Giữ visual size, thêm touch padding.

### Cách 2: tăng view size thành 48dp (nếu visual khớp)
Đơn giản hơn, nhưng có thể làm layout méo.

**Chọn cách 1** (minHeight/Width) để không phá layout.

**Thêm stateDescription:**
- `ivSkipToggle`: bind trong adapter ghi `stateDescription = if (isSkipped) "Bỏ qua" else "Xuất"`

## Test

1. Unit test: không thể test layout dp trực tiếp qua JVM (Robolectric không enforce constraint)
2. Widget test: inflate layout + measure -> verify width/height >= 48dp
3. Smoke test: thực tế dùng accessibility tools (TalkBack, Accessibility Scanner)

## Prompt loop

Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).
