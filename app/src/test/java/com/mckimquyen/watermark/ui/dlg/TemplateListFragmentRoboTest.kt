package com.mckimquyen.watermark.ui.dlg

import android.os.Build
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.ui.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class TemplateListFragmentRoboTest {

    @Test
    fun templateListFragment_initializesViewsAndM3Button() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        shadowOf(Looper.getMainLooper()).idle()

        val fragment = TextContentTemplateListFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, TextContentTemplateListFragment.TAG)
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()

        val view = fragment.view
        assertThat(view).isNotNull()

        val tvTitle = view!!.findViewById<TextView>(R.id.tvTitle)
        val ivBack = view.findViewById<View>(R.id.ivBack)
        val rvTemplate = view.findViewById<RecyclerView>(R.id.rvTemplate)
        val btnAdd = view.findViewById<MaterialButton>(R.id.btnAdd)

        assertThat(tvTitle).isNotNull()
        assertThat(tvTitle.text).isEqualTo(activity.getString(R.string.dialog_title_template_title))

        assertThat(ivBack).isNotNull()
        assertThat(rvTemplate).isNotNull()
        assertThat(btnAdd).isNotNull()
        assertThat(btnAdd.text).isEqualTo(activity.getString(R.string.dialog_button_add_template))

        activity.supportFragmentManager.beginTransaction().remove(fragment).commitNow()
        shadowOf(Looper.getMainLooper()).idle()
    }
}
