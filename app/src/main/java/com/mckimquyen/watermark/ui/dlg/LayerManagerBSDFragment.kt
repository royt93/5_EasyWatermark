package com.mckimquyen.watermark.ui.dlg

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentManager
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.Anchor
import com.mckimquyen.watermark.data.model.WatermarkLayer
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.databinding.DlgLayerManagerBinding
import com.mckimquyen.watermark.ui.adapter.WatermarkLayerAdapter
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment
import com.mckimquyen.watermark.utils.ktx.toast

/**
 * FEAT-03: quản lý layer watermark PHỤ — thêm/xoá/đổi z-order/sửa từng layer. 2 trạng thái
 * (danh sách / form sửa 1 layer) chuyển bằng visibility trong CÙNG layout (xem
 * `dlg_layer_manager.xml`), không dùng child fragment — giữ đơn giản cho tối đa
 * [WaterMarkRepository.MAX_EXTRA_LAYERS] layer.
 */
class LayerManagerBSDFragment : BaseBindBSDFragment<DlgLayerManagerBinding>() {

    /** `null` = đang thêm layer MỚI; khác null = đang sửa layer tại index này. */
    private var editingIndex: Int? = null

    /** Layer đang chỉnh trong form — nguồn sự thật khi bấm Lưu, tránh đọc rải rác từng field. */
    private var draftLayer: WatermarkLayer = WatermarkLayer(markMode = WaterMarkRepository.MarkMode.Text)

    private val adapter = WatermarkLayerAdapter(
        onTap = { index -> openEditForm(index) },
        onMoveUp = { index -> shareViewModel.reorderLayer(index, index - 1) },
        onMoveDown = { index -> shareViewModel.reorderLayer(index, index + 1) },
        onDelete = { index -> shareViewModel.removeLayer(index) }
    )

    private val pickIconLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            draftLayer = draftLayer.copy(iconUri = uri)
            binding.btnPickLayerIcon.text = uri.lastPathSegment ?: getString(R.string.layer_edit_pick_icon)
        }
    }

    private val anchorOrder = listOf(
        Anchor.TOP_LEFT, Anchor.TOP_CENTER, Anchor.TOP_RIGHT,
        Anchor.CENTER_LEFT, Anchor.CENTER, Anchor.CENTER_RIGHT,
        Anchor.BOTTOM_LEFT, Anchor.BOTTOM_CENTER, Anchor.BOTTOM_RIGHT
    )

    override fun bindView(layoutInflater: LayoutInflater, container: ViewGroup?): DlgLayerManagerBinding =
        DlgLayerManagerBinding.inflate(layoutInflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvLayers.adapter = adapter

        val anchorNames = anchorOrder.map { getString(anchorNameRes(it)) }
        binding.atvLayerAnchor.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, anchorNames)
        )

        binding.btnAddLayer.setOnClickListener {
            val currentSize = shareViewModel.waterMark.value?.extraLayers?.size ?: 0
            if (currentSize >= WaterMarkRepository.MAX_EXTRA_LAYERS) {
                toast(getString(R.string.layer_manager_max_reached, WaterMarkRepository.MAX_EXTRA_LAYERS))
            } else {
                openEditForm(null)
            }
        }

        binding.tgLayerMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val isImage = checkedId == binding.btnLayerModeImage.id
            binding.tilLayerText.visibility = if (isImage) View.GONE else View.VISIBLE
            binding.btnPickLayerIcon.visibility = if (isImage) View.VISIBLE else View.GONE
        }
        binding.btnPickLayerIcon.setOnClickListener {
            pickIconLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.btnSaveLayer.setOnClickListener { saveDraftLayer() }

        shareViewModel.waterMark.observe(viewLifecycleOwner) {
            val layers = it?.extraLayers.orEmpty()
            adapter.submitList(layers)
            binding.tvEmpty.visibility = if (layers.isEmpty()) View.VISIBLE else View.GONE
            // Nếu đang sửa layer mà layer đó vừa bị xoá (vd xoá từ danh sách khi form đang mở lần
            // trước) — quay lại danh sách để tránh Lưu đè lên index không còn tồn tại.
            val currentEditingIndex = editingIndex
            if (currentEditingIndex != null && currentEditingIndex !in layers.indices) {
                showList()
            }
        }

        showList()
    }

    private fun openEditForm(index: Int?) {
        editingIndex = index
        draftLayer = if (index != null) {
            shareViewModel.waterMark.value?.extraLayers?.getOrNull(index) ?: WatermarkLayer(markMode = WaterMarkRepository.MarkMode.Text)
        } else {
            WatermarkLayer(markMode = WaterMarkRepository.MarkMode.Text)
        }
        bindDraftToForm()
        showEdit()
    }

    private fun bindDraftToForm() {
        val isImage = draftLayer.markMode == WaterMarkRepository.MarkMode.Image
        binding.tgLayerMode.check(if (isImage) binding.btnLayerModeImage.id else binding.btnLayerModeText.id)
        binding.tilLayerText.visibility = if (isImage) View.GONE else View.VISIBLE
        binding.btnPickLayerIcon.visibility = if (isImage) View.VISIBLE else View.GONE
        binding.etLayerText.setText(draftLayer.text)
        binding.btnPickLayerIcon.text = draftLayer.iconUri.lastPathSegment ?: getString(R.string.layer_edit_pick_icon)
        binding.slLayerAlpha.value = draftLayer.alpha.toFloat().coerceIn(0f, 255f)
        binding.slLayerDegree.value = draftLayer.degree.coerceIn(0f, 360f)
        binding.slLayerMargin.value = (draftLayer.marginPercent * 100f).coerceIn(0f, 20f)
        val anchorPos = anchorOrder.indexOf(Anchor.obtain(draftLayer.anchor)).coerceAtLeast(0)
        binding.atvLayerAnchor.setText(binding.atvLayerAnchor.adapter.getItem(anchorPos).toString(), false)
    }

    private fun saveDraftLayer() {
        val isImage = binding.tgLayerMode.checkedButtonId == binding.btnLayerModeImage.id
        val anchorText = binding.atvLayerAnchor.text?.toString().orEmpty()
        val anchorNames = anchorOrder.map { getString(anchorNameRes(it)) }
        val centerFallbackIndex = anchorOrder.indexOf(Anchor.CENTER)
        val anchorIndex = anchorNames.indexOf(anchorText).let { if (it >= 0) it else centerFallbackIndex }
        val layer = draftLayer.copy(
            markMode = if (isImage) WaterMarkRepository.MarkMode.Image else WaterMarkRepository.MarkMode.Text,
            text = binding.etLayerText.text?.toString().orEmpty(),
            alpha = binding.slLayerAlpha.value.toInt(),
            degree = binding.slLayerDegree.value,
            anchor = anchorOrder[anchorIndex].ordinal,
            marginPercent = binding.slLayerMargin.value / 100f
        )
        if (!isImage && layer.text.isBlank()) {
            toast(R.string.layer_edit_text_hint)
            return
        }
        if (isImage && layer.iconUri == Uri.EMPTY) {
            toast(R.string.layer_edit_pick_icon)
            return
        }
        val index = editingIndex
        if (index != null) {
            shareViewModel.updateLayer(index, layer)
        } else {
            shareViewModel.addLayer(layer)
        }
        showList()
    }

    private fun showList() {
        binding.groupList.visibility = View.VISIBLE
        binding.groupEdit.visibility = View.GONE
    }

    private fun showEdit() {
        binding.groupList.visibility = View.GONE
        binding.groupEdit.visibility = View.VISIBLE
    }

    private fun anchorNameRes(anchor: Anchor): Int = when (anchor) {
        Anchor.TOP_LEFT -> R.string.anchor_top_left
        Anchor.TOP_CENTER -> R.string.anchor_top_center
        Anchor.TOP_RIGHT -> R.string.anchor_top_right
        Anchor.CENTER_LEFT -> R.string.anchor_center_left
        Anchor.CENTER -> R.string.anchor_center
        Anchor.CENTER_RIGHT -> R.string.anchor_center_right
        Anchor.BOTTOM_LEFT -> R.string.anchor_bottom_left
        Anchor.BOTTOM_CENTER -> R.string.anchor_bottom_center
        Anchor.BOTTOM_RIGHT -> R.string.anchor_bottom_right
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val d = super.onCreateDialog(savedInstanceState)
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return d
    }

    companion object {
        const val TAG = "LayerManagerBSDFragment"

        fun safetyShow(manager: FragmentManager) {
            try {
                val f = manager.findFragmentByTag(TAG) as? LayerManagerBSDFragment
                when {
                    f == null -> LayerManagerBSDFragment().show(manager, TAG)
                    !f.isAdded -> f.show(manager, TAG)
                }
            } catch (ie: IllegalStateException) {
                ie.printStackTrace()
            }
        }
    }
}
