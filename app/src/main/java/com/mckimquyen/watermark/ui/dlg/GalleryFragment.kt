package com.mckimquyen.watermark.ui.dlg
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.mckimquyen.watermark.AppLog
import com.mckimquyen.watermark.LOG_TAG
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.databinding.FGalleryBinding
import com.mckimquyen.watermark.ui.adapter.GalleryAdapter
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment
import com.mckimquyen.watermark.ui.widget.UniformScrollGridLayoutManager
import com.mckimquyen.watermark.utils.FileUtils
import com.mckimquyen.watermark.utils.MultiPickContract

class GalleryFragment : BaseBindBSDFragment<FGalleryBinding>() {

    companion object {
        private val TAG = GalleryFragment::class.java.simpleName
    }

    private var isScrollSliderManually: Boolean = false
    private var refreshRate: Float = 60f

    // ── Memory Leak Fix: adapter created once, cleared in onDestroyView ──────
    private val galleryAdapter by lazy { GalleryAdapter() }

    private lateinit var pickImageLauncher: ActivityResultLauncher<String>

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
                .setDuration(120)
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
                    pickImageLauncher.launch("image/*")
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
                    val percent = binding.rvContent.computeVerticalScrollRange() / totalHeight
                    AppLog.d(TAG, "ivSlider totalHeight=$totalHeight percent=$percent")
                    when (event?.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.x
                            startY = event.y
                            this@apply.animate().scaleX(1.4f).scaleY(1.4f).setDuration(150).start()
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
                            this@apply.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
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
        }

        // ── Observe selection count → animate FAB + hint pill ────────────────
        galleryAdapter.selectedCount.observe(viewLifecycleOwner) { count ->
            AppLog.d(LOG_TAG, "GalleryFragment selectedCount changed -> $count")
            if (count > 0) {
                val label = if (count == 1) "Select 1 photo" else "Select $count photos"
                rootView.fab.text = label
                rootView.tvSelectionHint?.apply {
                    text = "$count selected"
                    if (visibility != View.VISIBLE) {
                        visibility = View.VISIBLE
                        alpha = 0f
                        animate().alpha(1f).setDuration(200).start()
                    }
                }
                // Pop-in spring animation when FAB first appears or count changes
                if (rootView.fab.visibility != View.VISIBLE) {
                    rootView.fab.show()
                    rootView.fab.scaleX = 0.6f
                    rootView.fab.scaleY = 0.6f
                    rootView.fab.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(350)
                        .setInterpolator(OvershootInterpolator(2f))
                        .start()
                }
            } else {
                rootView.tvSelectionHint?.animate()?.alpha(0f)?.setDuration(150)
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
