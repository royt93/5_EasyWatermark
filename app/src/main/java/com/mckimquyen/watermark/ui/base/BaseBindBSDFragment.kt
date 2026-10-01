package com.mckimquyen.watermark.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding

abstract class BaseBindBSDFragment<VB : ViewBinding> : BaseBSDFragment() {
    private var _binding: VB? = null

    /**
     * REVIEW-13: force-unwrap có chủ đích — `binding` chỉ hợp lệ trong khoảng từ
     * [onCreateView] tới [onDestroyView] (chuẩn vòng đời Fragment). Mọi truy cập đồng bộ trong
     * [bindView]/`onViewCreated`/các callback `viewLifecycleOwner.lifecycleScope` hoặc
     * `observe(viewLifecycleOwner)` đều tự huỷ trước khi view bị destroy nên không bao giờ đọc
     * null. KHÔNG truy cập `binding` từ coroutine dùng `lifecycleScope` (Fragment-level, sống
     * lâu hơn view) hoặc Handler không được huỷ theo view.
     */
    val binding: VB get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = bindView(layoutInflater, container)
        return binding.root
    }

    abstract fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): VB

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
