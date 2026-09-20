package com.mckimquyen.watermark.ui.panel

import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.view.View
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.ui.MainActivity
import com.mckimquyen.watermark.ui.MainViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Integration Test for TileModeFragment:
 * - Tests toggle between Repeat and Decal (Single) modes
 * - Verifies interaction with MainViewModel.selectedImage
 * - Verifies dynamic TalkBack contentDescription updates
 * - Verifies Position Anchor button visibility
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class TileModeFragmentIntegrationRoboTest {

    @Suppress("UNCHECKED_CAST")
    @Test
    fun tileModeFragment_togglesBetweenRepeatAndDecal_withAccessibleAnnouncements() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val viewModel = activity.let {
            MainActivity::class.java.getDeclaredMethod("getViewModel").apply { isAccessible = true }
                .invoke(it) as MainViewModel
        }

        val repoField = MainViewModel::class.java.getDeclaredField("waterMarkRepo").apply { isAccessible = true }
        val repo = repoField.get(viewModel) as WaterMarkRepository
        val selectedImageFlowField = WaterMarkRepository::class.java.getDeclaredField("_selectedImage").apply { isAccessible = true }
        val selectedImageFlow = selectedImageFlowField.get(repo) as MutableStateFlow<ImageInfo>

        val testRepeatImage = ImageInfo(
            uri = Uri.parse("content://media/test.jpg"),
            tileMode = Shader.TileMode.REPEAT.ordinal
        )

        runBlocking {
            selectedImageFlow.emit(testRepeatImage)
        }
        shadowOf(Looper.getMainLooper()).idle()

        val fragment = TileModeFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, TileModeFragment.TAG)
            .commitNow()

        val view = fragment.requireView()
        val tgTileMode = view.findViewById<MaterialButtonToggleGroup>(R.id.tgTileMode)
        val btnRepeat = view.findViewById<MaterialButton>(R.id.btnTileModeRepeat)
        val btnDecal = view.findViewById<MaterialButton>(R.id.btnTileModeDecal)
        val btnAnchor = view.findViewById<MaterialButton>(R.id.btnPositionAnchor)

        // Initially in REPEAT mode:
        assertThat(tgTileMode.checkedButtonId).isEqualTo(R.id.btnTileModeRepeat)
        assertThat(btnAnchor.visibility).isEqualTo(View.GONE)
        assertThat(btnRepeat.contentDescription?.toString())
            .contains(activity.getString(R.string.state_enabled))
        assertThat(btnDecal.contentDescription?.toString())
            .contains(activity.getString(R.string.state_disabled))

        // Switch to DECAL (CLAMP) mode:
        val testDecalImage = testRepeatImage.copy(tileMode = Shader.TileMode.CLAMP.ordinal)
        runBlocking {
            selectedImageFlow.emit(testDecalImage)
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(tgTileMode.checkedButtonId).isEqualTo(R.id.btnTileModeDecal)
        assertThat(btnAnchor.visibility).isEqualTo(View.VISIBLE)
        assertThat(btnDecal.contentDescription?.toString())
            .contains(activity.getString(R.string.state_enabled))
        assertThat(btnRepeat.contentDescription?.toString())
            .contains(activity.getString(R.string.state_disabled))
    }
}
