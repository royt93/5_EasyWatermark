package com.mckimquyen.watermark.ui.dlg

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.mckimquyen.watermark.databinding.DlgEditTextBinding
import com.mckimquyen.watermark.ui.UiState
import com.mckimquyen.watermark.ui.base.BaseBindFragment
import com.mckimquyen.watermark.utils.TextTokenResolver
import com.mckimquyen.watermark.utils.bitmap.BitmapCache
import com.mckimquyen.watermark.utils.ktx.commitWithAnimation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EditTextContentFragment : BaseBindFragment<DlgEditTextBinding>() {

    /** ENH-02: debounce ghi DataStore khi gõ liên tục — huỷ job cũ mỗi ký tự, chỉ ghi thật sau khi dừng gõ. */
    private var updateTextJob: Job? = null

    /**
     * IDEA-16: Android 10+ redact GPS khỏi EXIF nếu thiếu `ACCESS_MEDIA_LOCATION` → {location} luôn rỗng.
     * Xin quyền lúc user chèn chip {location}; cấp xong thì xoá cache decode (EXIF cũ đọc lúc chưa có
     * quyền, không có toạ độ) để lần decode sau đọc lại bản original.
     */
    private val mediaLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) BitmapCache.clearCache()
        }

    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): DlgEditTextBinding {
        return DlgEditTextBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.etWaterText?.apply {
            setText(initialText(shareViewModel.waterMark.value?.text))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                }

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    val text = s?.toString() ?: ""
                    updateTextJob?.cancel()
                    updateTextJob = viewLifecycleOwner.lifecycleScope.launch {
                        delay(TEXT_UPDATE_DEBOUNCE_MS)
                        shareViewModel.updateText(text)
                    }
                }
            })

            post {
                setSelection(text?.length ?: 0)
                requestFocus()
                val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
        binding?.btnConfirm?.apply {
            setOnClickListener {
                updateTextJob?.cancel()
                shareViewModel.updateText(binding?.etWaterText?.text?.toString() ?: "")
                (requireParentFragment() as? DialogFragment)?.dismiss()
            }
        }

        setupTokenChips()

        binding?.btnGoTemplate?.apply {
            setOnClickListener {
                shareViewModel.goTemplate()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            shareViewModel.uiStateFlow.flowWithLifecycle(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.STARTED
            )
                .collect {
                    if (it !is UiState.UseTemplate) {
                        return@collect
                    }
                    val template = it.template
                    binding?.etWaterText?.setText(template.content)
                }
        }
    }

    override fun onDestroyView() {
        // ENH-02: View có thể bị huỷ (back/rời màn hình) trước khi debounce kịp chạy — flush
        // ngay giá trị cuối cùng để không mất ký tự vừa gõ (huỷ job debounce đang chờ, tránh ghi
        // trùng ngay sau đó).
        updateTextJob?.cancel()
        binding?.etWaterText?.text?.toString()?.let { shareViewModel.updateText(it) }
        super.onDestroyView()
    }

    /** Chip nào bấm thì chèn "{token}" vào etWaterText tại vị trí con trỏ (thay thế phần đang bôi đen nếu có). */
    private fun setupTokenChips() {
        val group = binding?.cgTokens ?: return
        val editText = binding?.etWaterText ?: return
        TextTokenResolver.SUPPORTED_TOKENS.forEach { token ->
            val chip = Chip(requireContext()).apply {
                text = "{$token}"
                contentDescription = "{$token}"
                isCheckable = false
                isClickable = true
                setEnsureMinTouchTargetSize(true)
                setOnClickListener {
                    val start = editText.selectionStart.coerceAtLeast(0)
                    val end = editText.selectionEnd.coerceAtLeast(0)
                    editText.text?.replace(minOf(start, end), maxOf(start, end), "{$token}")
                    if (needsMediaLocationPermission(token)) {
                        mediaLocationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                    }
                }
            }
            group.addView(chip)
        }
    }

    private fun needsMediaLocationPermission(token: String): Boolean =
        isMediaLocationPermissionRequired(token, Build.VERSION.SDK_INT) &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_MEDIA_LOCATION) !=
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val TAG = "TextContentFragment"
        private const val LOCATION_TOKEN_NAME = "location"

        /** IDEA-16: quyền `ACCESS_MEDIA_LOCATION` chỉ tồn tại từ Android 10 (Q) và chỉ cần cho chip {location}. */
        internal fun isMediaLocationPermissionRequired(token: String, sdkInt: Int): Boolean =
            token == LOCATION_TOKEN_NAME && sdkInt >= Build.VERSION_CODES.Q
        private const val TEXT_UPDATE_DEBOUNCE_MS = 200L

        /** BUG-16: `null?.text.toString()` cho ra literal "null"; đây giữ ô nhập trống khi chưa có config. */
        internal fun initialText(text: String?): String = text.orEmpty()

        fun replaceShow(fa: FragmentActivity, containerId: Int) {
            val f = fa.supportFragmentManager.findFragmentByTag(TAG)
            if (f?.isVisible == true) {
                return
            }
            fa.commitWithAnimation {
                replace(
                    containerId,
                    EditTextContentFragment(),
                    TAG
                )
            }
        }
    }
}
