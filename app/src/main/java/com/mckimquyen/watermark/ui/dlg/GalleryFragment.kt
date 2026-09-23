package com.mckimquyen.watermark.ui.dlg
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.mckimquyen.watermark.AppLog
import com.mckimquyen.watermark.LOG_TAG
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.databinding.FGalleryBinding
import com.mckimquyen.watermark.ui.adapter.GalleryAdapter
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment
import com.mckimquyen.watermark.ui.widget.UniformScrollGridLayoutManager
import com.mckimquyen.watermark.utils.FileUtils
import com.mckimquyen.watermark.utils.MultiPickContract
import com.mckimquyen.watermark.utils.ktx.applyConsistentIconTint
import com.mckimquyen.watermark.utils.ktx.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryFragment : BaseBindBSDFragment<FGalleryBinding>() {

    companion object {
        private val TAG = GalleryFragment::class.java.simpleName

        // M3 Motion duration token (https://m3.material.io/styles/motion) — chuẩn hoá thay magic
        // number rải rác, giữ nguyên giá trị hiện tại (đã khớp token gần nhất) để tránh đổi hành vi.
        private const val MOTION_DURATION_SHORT2 = 120L // micro feedback (FAB pop-out khi bấm)
        private const val MOTION_DURATION_SHORT3 = 150L // scale/fade feedback (slider, hint fade-out)
        private const val MOTION_DURATION_SHORT4 = 200L // fade-in (hint pill xuất hiện)
        private const val MOTION_DURATION_MEDIUM3 = 350L // pop-in nhấn mạnh (FAB xuất hiện)

        /**
         * BUG-29: tỉ lệ "px scroll thật / px kéo slider" — PHẢI dùng phép chia Float, không phải
         * Int/Int (mất hoàn toàn phần thập phân, khiến kéo slider không cuộn hết list hoặc nhảy
         * sai vị trí). Tách hàm riêng để test được trực tiếp không cần dựng RecyclerView/View thật.
         */
        internal fun computeSliderScrollPercent(scrollRange: Int, totalHeight: Int): Float =
            scrollRange.toFloat() / totalHeight
    }

    private var isScrollSliderManually: Boolean = false
    private var refreshRate: Float = 60f

    // ── Memory Leak Fix: adapter created once, cleared in onDestroyView ──────
    private val galleryAdapter by lazy { GalleryAdapter() }

    /** ENH-10: fallback ACTION_PICK cho thiết bị không hỗ trợ Android Photo Picker. */
    private lateinit var pickImageLauncher: ActivityResultLauncher<String>

    /** ENH-10: Android Photo Picker — không cần quyền READ_MEDIA_IMAGES/READ_EXTERNAL_STORAGE. */
    private lateinit var pickImageVisualMediaLauncher: ActivityResultLauncher<PickVisualMediaRequest>

    /** FEAT-08: chọn cả thư mục (SAF tree) — đưa toàn bộ ảnh trực tiếp trong đó vào batch. */
    private lateinit var pickFolderLauncher: ActivityResultLauncher<Uri?>

    /** ENH-33: lựa chọn "Include subfolders" ở dialog trước khi mở SAF picker, đọc lại lúc xử lý kết quả. */
    internal var pendingIncludeSubfolders: Boolean = false
        private set

    private var doOnDismiss: () -> Unit = {}

    fun doOnDismiss(doOnDismiss: () -> Unit) {
        this.doOnDismiss = doOnDismiss
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.d(LOG_TAG, "GalleryFragment onCreate")
        pickImageLauncher =
            registerForActivityResult(MultiPickContract()) { uri: List<Uri?>? ->
                handleActivityResult(uri)
            }
        pickImageVisualMediaLauncher =
            registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris: List<Uri> ->
                handleActivityResult(uris)
            }
        pickFolderLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri: Uri? ->
                handleTreeUriResult(treeUri)
            }
        shareViewModel.query(requireContext().contentResolver)
        AppLog.d(LOG_TAG, "GalleryFragment querying media store...")

        val displayManager: DisplayManager =
            requireContext().applicationContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        refreshRate = displayManager.displays?.getOrNull(0)?.refreshRate ?: 60F
        AppLog.d(LOG_TAG, "GalleryFragment refreshRate=$refreshRate")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val d = object : BottomSheetDialog(requireContext()) {
            override fun onBackPressed() {
                if (galleryAdapter.getSelectedList().isEmpty()) {
                    dismiss()
                } else {
                    galleryAdapter.unSelectAll(binding.rvContent)
                }
            }
        }.apply {
            behavior.isDraggable = false
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            isCancelable = false
        }
        setupBottomSheet(d, expandFully = true)
        return d
    }

    override fun onStart() {
        super.onStart()
        AppLog.d(LOG_TAG, "GalleryFragment onStart — expanding to match_parent")
        val sheetContainer = requireView().parent as? ViewGroup ?: return
        sheetContainer.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FGalleryBinding {
        val rootView = FGalleryBinding.inflate(layoutInflater, container, false)

        val baseToolbarHeight = resources.getDimensionPixelSize(
            com.google.android.material.R.dimen.m3_appbar_size_compact
        )
        rootView.topAppBar.layoutParams.height = baseToolbarHeight
        rootView.topAppBar.setPadding(0, 0, 0, 0)
        val toolbarIconColor = MaterialColors.getColor(
            requireContext(),
            com.google.android.material.R.attr.colorOnSurface,
            Color.BLACK
        )
        rootView.topAppBar.applyConsistentIconTint(toolbarIconColor)

        // ── Navigation ───────────────────────────────────────────────────────
        rootView.topAppBar.setNavigationOnClickListener {
            AppLog.d(LOG_TAG, "GalleryFragment navigation icon clicked — dismissing")
            dismissAllowingStateLoss()
        }

        // ── FAB: initially hidden ────────────────────────────────────────────
        rootView.fab.hide()

        // ── FAB click: spring-out → confirm selection ────────────────────────
        rootView.fab.setOnClickListener {
            val selected = galleryAdapter.getSelectedList()
            AppLog.d(LOG_TAG, "GalleryFragment FAB clicked — selectedCount=${selected.size}")
            // Pop-Out animation before dismiss
            rootView.fab.animate()
                .scaleX(1.15f).scaleY(1.15f)
                .setDuration(MOTION_DURATION_SHORT2)
                .withEndAction {
                    shareViewModel.selectGallery(selected)
                    rootView.fab.hide()
                    dismissAllowingStateLoss()
                }
                .start()
        }

        // ── RecyclerView setup ───────────────────────────────────────────────
        rootView.rvContent.apply {
            layoutManager = UniformScrollGridLayoutManager(requireContext(), 4).also {
                it.scrollBarView = rootView.ivSlider
                it.isItemPrefetchEnabled = true
                it.initialPrefetchItemCount = 4
            }
            adapter = galleryAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(12)
            recycledViewPool.setMaxRecycledViews(0, 16)
            setOnSelect { rv, end ->
                galleryAdapter.select(rv, end)
            }
            setOnUnSelect { rv, end ->
                galleryAdapter.unSelect(rv, end)
            }
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                private var frameCount = 0

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (isScrollSliderManually) return
                    if (++frameCount % 3 != 0) return

                    val verticalScrollRange = recyclerView.computeVerticalScrollRange()
                    val offset = recyclerView.computeVerticalScrollOffset()
                    AppLog.d(LOG_TAG, "GalleryFragment scroll — offset=$offset range=$verticalScrollRange")

                    rootView.sliderCard.translationY =
                        (
                            (offset.toFloat() / verticalScrollRange) *
                                (recyclerView.bottom - recyclerView.paddingBottom)
                            ).coerceAtLeast(0f)
                }
            })
        }

        // ── Menu: system photo picker ────────────────────────────────────────
        rootView.topAppBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.ivSysImage -> {
                    // ENH-10: ưu tiên Android Photo Picker, fallback ACTION_PICK khi không hỗ trợ.
                    if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(requireContext())) {
                        pickImageVisualMediaLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    } else {
                        pickImageLauncher.launch("image/*")
                    }
                    return@setOnMenuItemClickListener true
                }

                R.id.ivPickFolder -> {
                    // FEAT-08: chọn cả thư mục — ảnh hợp lệ trực tiếp trong đó (không đệ quy
                    // subfolder) đưa hết vào batch, song song với multi-pick từng ảnh ở trên.
                    // ENH-33: hỏi trước có muốn quét luôn subfolder (đệ quy có giới hạn) hay không.
                    showPickFolderOptionsDialog()
                    return@setOnMenuItemClickListener true
                }

                R.id.actionSelectToggle -> {
                    if (galleryAdapter.isAllSelected()) {
                        galleryAdapter.unSelectAll(rootView.rvContent)
                    } else {
                        galleryAdapter.selectAll(rootView.rvContent)
                    }
                    return@setOnMenuItemClickListener true
                }
            }
            return@setOnMenuItemClickListener false
        }

        // ── Scroll slider ────────────────────────────────────────────────────
        rootView.sliderCard.apply {
            post { translationX += measuredWidth / 5 }
            setOnTouchListener(object : View.OnTouchListener {
                private var startX = 0f
                private var startY = 0f

                override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                    if (v == null) return false
                    val totalHeight = rootView.rvContent.bottom - rootView.rvContent.paddingBottom
                    val percent = computeSliderScrollPercent(binding.rvContent.computeVerticalScrollRange(), totalHeight)
                    AppLog.d(TAG, "ivSlider totalHeight=$totalHeight percent=$percent")
                    when (event?.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.x
                            startY = event.y
                            this@apply.animate().scaleX(1.4f).scaleY(1.4f).setDuration(MOTION_DURATION_SHORT3).start()
                            binding.rvContent.stopScroll()
                            isScrollSliderManually = true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dy = event.y - startY
                            val targetPos = dy * percent
                            v.translationY = (dy + v.translationY).coerceAtLeast(0f)
                                .coerceAtMost(totalHeight.toFloat())
                            binding.rvContent.stopScroll()
                            binding.rvContent.scrollBy(0, targetPos.toInt())
                        }
                        MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                            this@apply.animate().scaleX(1f).scaleY(1f).setDuration(MOTION_DURATION_SHORT3).start()
                            isScrollSliderManually = false
                        }
                    }
                    return true
                }
            })
        }

        // ── Observe image list ───────────────────────────────────────────────
        shareViewModel.galleryPickedImageList.observe(viewLifecycleOwner) {
            AppLog.d(LOG_TAG, "GalleryFragment galleryPickedImageList updated — count=${it?.size ?: 0}")
            galleryAdapter.submitList(it)
            binding.llEmptyState.isVisible = it.isNullOrEmpty()
        }

        // ── Observe selection count → animate FAB + hint pill ────────────────
        galleryAdapter.selectedCount.observe(viewLifecycleOwner) { count ->
            AppLog.d(LOG_TAG, "GalleryFragment selectedCount changed -> $count")
            val selectToggleItem = rootView.topAppBar.menu.findItem(R.id.actionSelectToggle)
            if (galleryAdapter.isAllSelected()) {
                selectToggleItem?.setIcon(R.drawable.ic_deselect_all)
                selectToggleItem?.setTitle(R.string.action_deselect_all)
            } else {
                selectToggleItem?.setIcon(R.drawable.ic_select_all)
                selectToggleItem?.setTitle(R.string.action_select_all)
            }
            rootView.topAppBar.applyConsistentIconTint(toolbarIconColor)
            if (count > 0) {
                // ENH-09: <plurals> thay vì if/else hardcode — chuẩn Android cho số nhiều, hỗ trợ
                // đúng ngữ pháp khi có bản dịch ngôn ngữ khác (vd tiếng Ả Rập/Nga nhiều dạng số nhiều).
                rootView.fab.text = resources.getQuantityString(R.plurals.gallery_select_photo_count, count, count)
                rootView.fab.extend()
                rootView.tvSelectionHint?.apply {
                    text = resources.getQuantityString(R.plurals.gallery_selected_count, count, count)
                    if (visibility != View.VISIBLE) {
                        visibility = View.VISIBLE
                        alpha = 0f
                        animate().alpha(1f).setDuration(MOTION_DURATION_SHORT4).start()
                    }
                }
                // Pop-in spring animation when FAB first appears or count changes
                if (rootView.fab.visibility != View.VISIBLE) {
                    rootView.fab.show()
                    rootView.fab.scaleX = 0.6f
                    rootView.fab.scaleY = 0.6f
                    rootView.fab.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(MOTION_DURATION_MEDIUM3)
                        .setInterpolator(FastOutSlowInInterpolator())
                        .start()
                }
            } else {
                rootView.tvSelectionHint?.animate()?.alpha(0f)?.setDuration(MOTION_DURATION_SHORT3)
                    ?.withEndAction { rootView.tvSelectionHint?.visibility = View.GONE }?.start()
                rootView.fab.hide()
            }
        }

        return rootView
    }

    /**
     * ── Memory Leak Prevention ────────────────────────────────────────────────
     * Clear the adapter from RecyclerView when view is destroyed.
     * This prevents Glide from holding references to destroyed ViewHolders.
     */
    override fun onDestroyView() {
        AppLog.d(LOG_TAG, "GalleryFragment onDestroyView — clearing adapter to prevent leak")
        try {
            binding.rvContent.adapter = null
        } catch (e: Exception) {
            Log.w(LOG_TAG, "GalleryFragment onDestroyView adapter clear error: ${e.message}")
        }
        super.onDestroyView()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        AppLog.d(LOG_TAG, "GalleryFragment onDismiss — resetting gallery data")
        doOnDismiss.invoke()
        shareViewModel.resetGalleryData()
    }

    /** ENH-33: dialog hỏi có quét luôn ảnh trong subfolder trước khi mở SAF tree picker. */
    private fun showPickFolderOptionsDialog() {
        val switch = MaterialSwitch(requireContext()).apply {
            text = getString(R.string.pick_folder_include_subfolders)
            isChecked = false
        }
        val container = FrameLayout(requireContext()).apply {
            setPadding(24.dp, 8.dp, 24.dp, 0)
            addView(switch)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pick_folder_dialog_title)
            .setMessage(R.string.pick_folder_dialog_message)
            .setView(container)
            .setNegativeButton(R.string.tips_cancel_dialog) { dialog, _ -> dialog.dismiss() }
            .setPositiveButton(R.string.tips_confirm_dialog) { dialog, _ ->
                pendingIncludeSubfolders = switch.isChecked
                pickFolderLauncher.launch(null)
                dialog.dismiss()
            }
            .show()
    }

    internal fun handleTreeUriResult(treeUri: Uri?) {
        if (treeUri == null) return
        // Giữ quyền đọc qua lần khởi động app sau — ảnh trong thư mục vẫn cần đọc lại lúc
        // export (có thể đã sang tiến trình mới, xem BatchExportWorker/ENH-01).
        // BUG-35: Bọc runCatching tránh SecurityException nếu provider không hỗ trợ persist permission.
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.onFailure { e ->
            AppLog.w(LOG_TAG, "Failed to persist treeUri permission: $treeUri", e)
        }
        // ENH-33: liệt kê cây thư mục là chuỗi lệnh gọi ContentResolver (IPC) đồng bộ — với
        // includeSubfolders bật, có thể là nhiều lệnh gọi lồng nhau (tới maxDepth tầng), đủ chậm
        // để treo UI nếu chạy thẳng trên main thread. Chạy trên Dispatchers.IO, cập nhật UI lại
        // trên main thread sau khi có kết quả.
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val images = withContext(Dispatchers.IO) {
                FileUtils.listImagesInTree(context, treeUri, includeSubfolders = pendingIncludeSubfolders)
            }
            if (isAdded) {
                handleActivityResult(images)
            }
        }
    }

    private fun handleActivityResult(list: List<Uri?>?) {
        AppLog.d(LOG_TAG, "GalleryFragment handleActivityResult — rawCount=${list?.size ?: 0}")
        val finalList = list?.filterNotNull()?.filter {
            FileUtils.isImage(requireContext().contentResolver, it)
        } ?: emptyList()
        AppLog.d(LOG_TAG, "GalleryFragment handleActivityResult — validImageCount=${finalList.size}")
        if (finalList.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.tips_do_not_choose_image), Toast.LENGTH_SHORT).show()
            return
        }
        if (FileUtils.isImage(requireContext().contentResolver, finalList.first())) {
            AppLog.d(LOG_TAG, "GalleryFragment handleActivityResult — updating image list from file picker")
            shareViewModel.updateImageList(finalList)
            dismissAllowingStateLoss()
        } else {
            AppLog.d(LOG_TAG, "GalleryFragment handleActivityResult — unsupported file type chosen")
            Toast.makeText(requireContext(), getString(R.string.tips_choose_other_file_type), Toast.LENGTH_SHORT).show()
        }
    }
}
