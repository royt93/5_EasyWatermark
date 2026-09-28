package com.mckimquyen.watermark.ui.recipient

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.entity.Recipient
import com.mckimquyen.watermark.databinding.FRecipientPickerBinding
import com.mckimquyen.watermark.databinding.ItemRecipientPickerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * IDEA-10: BottomSheet cho phép chọn nhanh Người nhận trước khi xuất batch ảnh.
 */
@AndroidEntryPoint
class RecipientPickerBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FRecipientPickerBinding? = null
    val binding get() = _binding!!

    private val viewModel: RecipientViewModel by viewModels()

    private var currentSelectedCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentSelectedCode = arguments?.getString(ARG_SELECTED_CODE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FRecipientPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = RecipientPickerAdapter(
            selectedCode = currentSelectedCode,
            onSelect = { recipient ->
                setFragmentResult(
                    REQUEST_KEY,
                    bundleOf(
                        RESULT_CODE to recipient?.code,
                        RESULT_NAME to recipient?.name
                    )
                )
                dismiss()
            }
        )

        binding.rvRecipients.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecipients.adapter = adapter

        binding.btnManageRecipients.setOnClickListener {
            startActivity(Intent(requireContext(), RecipientManagementActivity::class.java))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recipients.collect { list ->
                    adapter.submitList(list)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class RecipientPickerAdapter(
        private val selectedCode: String?,
        private val onSelect: (Recipient?) -> Unit
    ) : RecyclerView.Adapter<RecipientPickerAdapter.ViewHolder>() {

        private var items: List<Recipient> = emptyList()

        // lint NotifyDataSetChanged: danh sách người nhận thường rất nhỏ (vài chục), không cần
        // DiffUtil/AsyncListDiffer — notifyDataSetChanged() đơn giản và đủ nhanh.
        @Suppress("NotifyDataSetChanged")
        fun submitList(newItems: List<Recipient>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size + 1 // +1 for "None / Mặc định"

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemRecipientPickerBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            if (position == 0) {
                holder.bindNone(selectedCode == null, onSelect)
            } else {
                val item = items[position - 1]
                holder.bind(item, selectedCode == item.code, onSelect)
            }
        }

        class ViewHolder(private val binding: ItemRecipientPickerBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bindNone(isSelected: Boolean, onSelect: (Recipient?) -> Unit) {
                binding.tvRecipientName.text = binding.root.context.getString(R.string.recipient_none)
                binding.tvRecipientCode.visibility = View.GONE
                binding.ivSelectedCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
                binding.root.setOnClickListener { onSelect(null) }
            }

            // lint SetTextI18n: `codeLabel`/`notePart` đều đã qua getString()/dữ liệu người dùng
            // (item.notes) — không còn literal chữ cứng nào cần dịch, chỉ nối 2 chuỗi đã an toàn.
            @Suppress("SetTextI18n")
            fun bind(item: Recipient, isSelected: Boolean, onSelect: (Recipient?) -> Unit) {
                binding.tvRecipientName.text = item.name
                binding.tvRecipientCode.visibility = View.VISIBLE
                val notePart = if (!item.notes.isNullOrBlank()) " · ${item.notes}" else ""
                val codeLabel = binding.tvRecipientCode.context.getString(R.string.recipient_code_display, item.code)
                binding.tvRecipientCode.text = "$codeLabel$notePart"
                binding.ivSelectedCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
                binding.root.setOnClickListener { onSelect(item) }
            }
        }
    }

    companion object {
        const val TAG = "RecipientPickerBottomSheetFragment"
        const val REQUEST_KEY = "request_recipient_picker"
        const val RESULT_CODE = "result_recipient_code"
        const val RESULT_NAME = "result_recipient_name"
        private const val ARG_SELECTED_CODE = "arg_selected_code"

        fun newInstance(selectedCode: String?): RecipientPickerBottomSheetFragment {
            return RecipientPickerBottomSheetFragment().apply {
                arguments = bundleOf(ARG_SELECTED_CODE to selectedCode)
            }
        }
    }
}
