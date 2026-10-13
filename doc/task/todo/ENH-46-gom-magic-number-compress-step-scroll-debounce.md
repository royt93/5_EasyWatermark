# ENH-46: Gom magic number (quality step, scroll extent, debounce) về const

**Priority:** P2
**Effort:** XS
**Status:** todo

## Vấn đề (R5 magic number)

- `app/src/main/java/com/mckimquyen/watermark/data/repo/UserConfigRepository.kt:60-62` `coerceAtLeast(20)`/`coerceAtMost(100)`/`% 20` — step 20 trùng `android:stepSize="20"` ở `res/layout/dlg_save_file.xml:257`; lệch 1 bên là reset quality user về mặc định.
- `.../ui/widget/UniformScrollGridLayoutManager.kt:53` fallback `110`.
- `.../ui/widget/TouchSensitiveRv.kt:129` debounce `300`.

## Fix

`COMPRESS_LEVEL_MIN/MAX/STEP`, `DEFAULT_SCROLL_EXTENT_PX`, `AUTO_SELECT_DEBOUNCE_MS` kèm comment nguồn gốc giá trị. Test khoá `STEP` khớp `stepSize` layout.

## Prompt loop

Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).
