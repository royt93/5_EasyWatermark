# BUG-75: WatermarkLayerAdapter — captured position lỗi sau drag-reorder

**Priority:** P2 (thiệt hại dữ liệu, nhưng hiếm kích hoạt)  
**Effort:** XS (1-2h)  
**Status:** todo

## Vấn đề

`WatermarkLayerAdapter` (FEAT-03 layer phụ) capture `position` trong closure callback (dòng 39, 42, 45, 46). Khi user drag-reorder layer, `items` list thay đổi, nhưng lambda vẫn dùng `position` cũ.

**Kịch bản crash:**
1. Bind layer 0 (Text "Chữ"): `onDelete(position=0)` → lambda capture `position=0`
2. User drag layer 0 → layer 1: `submitList([layer 1, layer 0])`  
3. Holder layer 0 recycle + rebind tại position 1 (hoặc reuse layer 1 ở position 0)
4. User bấm Delete → callback dùng `position=0` (cũ) nhưng layer ở pos 0 giờ là layer 1
5. **Delete nhầm layer thứ 2 thay vì layer 1**

## Root cause

Dòng 39, 42, 45, 46:
```kotlin
holder.binding.root.setOnClickListener { onTap(position) }
holder.binding.btnMoveUp.setOnClickListener { onMoveUp(position) }
holder.binding.btnMoveDown.setOnClickListener { onMoveDown(position) }
holder.binding.btnDelete.setOnClickListener { onDelete(position) }
```

`position` là parameter của `onBindViewHolder`, nhưng được capture trong closure. Khi ViewHolder reuse cho item khác, closure vẫn dùng `position` cũ, không phải vị trí thực tế lúc click.

## Fix

Dùng `getBindingAdapterPosition()` (dynamic lookup) thay vì captured `position`:
```kotlin
holder.binding.root.setOnClickListener { onTap(holder.bindingAdapterPosition) }
holder.binding.btnMoveUp.setOnClickListener { onMoveUp(holder.bindingAdapterPosition) }
holder.binding.btnMoveDown.setOnClickListener { onMoveDown(holder.bindingAdapterPosition) }
holder.binding.btnDelete.setOnClickListener { onDelete(holder.bindingAdapterPosition) }
```

## Test

1. Unit test regression: `WatermarkLayerAdapterRoboTest.tapDeleteAfterReorder_usesCurrentPositionNotCaptured()`
   - Bind layer 0 (position 0)
   - `submitList([layer 1, layer 0])` (thay đổi order)
   - Rebind layer 0 tại position 1
   - Tap delete → callback phải nhận `position=1` (hiện tại), không phải `position=0` (cũ)

2. Smoke test: batch export với 2+ layer phụ, drag-reorder, xoá từng layer → verify layer xuất đúng

## Prompt loop

Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).
