package com.mckimquyen.watermark.ui.base

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.ui.MainViewModel

open class BaseFragment : Fragment() {

    protected val shareViewModel: MainViewModel by activityViewModels()

    protected val config: WaterMark?
        get() {
            return shareViewModel.waterMark.value
        }
}
