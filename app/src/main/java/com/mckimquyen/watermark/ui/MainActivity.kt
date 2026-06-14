package com.mckimquyen.watermark.ui
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.forEach
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.mckimquyen.watermark.BaseActivity
import com.mckimquyen.watermark.BuildConfig
import com.mckimquyen.watermark.LOG_TAG
import com.mckimquyen.watermark.MyApplication
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.FuncTitleModel
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.ViewInfo
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.rateAppInApp
import com.mckimquyen.watermark.ui.about.AboutActivity
import com.mckimquyen.watermark.ui.adapter.FuncPanelAdapter
import com.mckimquyen.watermark.ui.adapter.PhotoListPreviewAdapter
import com.mckimquyen.watermark.ui.dlg.CompressImageDialogFragment
import com.mckimquyen.watermark.ui.dlg.GalleryFragment
import com.mckimquyen.watermark.ui.dlg.SaveImageBSDialogFragment
import com.mckimquyen.watermark.ui.dlg.TextWatermarkBSDFragment
import com.mckimquyen.watermark.ui.panel.AlphaPbFragment
import com.mckimquyen.watermark.ui.panel.ColorFragment
import com.mckimquyen.watermark.ui.panel.DegreePbFragment
import com.mckimquyen.watermark.ui.panel.HorizonPbFragment
import com.mckimquyen.watermark.ui.panel.TextContentDisplayFragment
import com.mckimquyen.watermark.ui.panel.TextSizePbFragment
import com.mckimquyen.watermark.ui.panel.TextStyleFragment
import com.mckimquyen.watermark.ui.panel.TileModeFragment
import com.mckimquyen.watermark.ui.panel.VerticalPbFragment
import com.mckimquyen.watermark.ui.widget.CenterLayoutManager
import com.mckimquyen.watermark.ui.widget.LaunchView
import com.mckimquyen.watermark.ui.widget.onItemClick
import com.mckimquyen.watermark.utils.FileUtils
import com.mckimquyen.watermark.utils.PickImageContract
import com.mckimquyen.watermark.utils.VibrateHelper
import com.mckimquyen.watermark.utils.ktx.bgColor
import com.mckimquyen.watermark.utils.ktx.colorPrimary
import com.mckimquyen.watermark.utils.ktx.colorSurface
import com.mckimquyen.watermark.utils.ktx.commitWithAnimation
import com.mckimquyen.watermark.utils.ktx.isNight
import com.mckimquyen.watermark.utils.ktx.openLink
import com.mckimquyen.watermark.utils.ktx.preCheckStoragePermission
import com.mckimquyen.watermark.utils.ktx.titleTextColor
import com.mckimquyen.watermark.utils.ktx.toColor
import com.mckimquyen.watermark.utils.ktx.toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    private lateinit var pickIconLauncher: ActivityResultLauncher<String>
    private lateinit var signatureLauncher: ActivityResultLauncher<Intent>
    private val viewModel: MainViewModel by viewModels()

    private val currentBgColor: Int
        get() = ((launchView.parent as? View?)?.background as? ColorDrawable)?.color ?: colorSurface

    private val contentFunList: List<FuncTitleModel> by lazy {
        listOf(
            FuncTitleModel(
                type = FuncTitleModel.FuncType.Text,
                title = getString(R.string.water_mark_mode_text),
                iconRes = R.drawable.ic_func_text
            ),
            FuncTitleModel(
                type = FuncTitleModel.FuncType.Icon,
                title = getString(R.string.water_mark_mode_image),
                iconRes = R.drawable.ic_func_sticker
            ),
            FuncTitleModel(
                type = FuncTitleModel.FuncType.Signature,
                title = "Signature",
                iconRes = R.drawable.ic_func_text
            ),
            FuncTitleModel(
                type = FuncTitleModel.FuncType.QRCode,
                title = getString(R.string.qr_code),
                iconRes = R.drawable.ic_func_qr_code
            ),
            FuncTitleModel(
                type = FuncTitleModel.FuncType.ExifBorder,
                title = "Leica EXIF",
                iconRes = R.drawable.ic_func_layout_vertical
            )
        )
    }

    private val styleFunList: List<FuncTitleModel> by lazy {
        listOf(
            FuncTitleModel(
                type = FuncTitleModel.FuncType.TileMode,
                title = getString(R.string.title_tile_mode),
                iconRes = R.drawable.ic_tile_mode
            ),
            FuncTitleModel(
                type = FuncTitleModel.FuncType.TextSize,
                title = getString(R.string.title_text_size),
                iconRes = R.drawable.ic_func_size
            ),
            FuncTitleModel(
                FuncTitleModel.FuncType.TextStyle,
                getString(R.string.title_text_style),
                R.drawable.ic_func_typeface
            ),
            FuncTitleModel(
                FuncTitleModel.FuncType.Color,
                getString(R.string.title_text_color),
                R.drawable.ic_func_color
            ),
            FuncTitleModel(
                FuncTitleModel.FuncType.Alpha,
                getString(R.string.style_alpha),
                R.drawable.ic_func_opacity
            ),
            FuncTitleModel(
                FuncTitleModel.FuncType.Degree,
                getString(R.string.title_text_rotate),
                R.drawable.ic_func_angle
            )
        )
    }

    private val layoutFunList: List<FuncTitleModel> by lazy {
        listOf(
            FuncTitleModel(
                FuncTitleModel.FuncType.Horizon,
                getString(R.string.title_horizon_layout),
                R.drawable.ic_func_layour_horizontal
            ),
            FuncTitleModel(
                FuncTitleModel.FuncType.Vertical,
                getString(R.string.title_vertical_layout),
                R.drawable.ic_func_layout_vertical
            )
        )
    }

    private val funcAdapter by lazy {
        FuncPanelAdapter(ArrayList(contentFunList)).apply {
            setHasStableIds(true)
        }
    }

    private val photoListPreviewAdapter by lazy { PhotoListPreviewAdapter(this) }

    private val vibrateHelper: VibrateHelper by lazy { VibrateHelper.get() }

    private lateinit var launchView: LaunchView

    private var bgTransformAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // applyEdgeToEdge() is invoked by BaseActivity.onCreate() — no duplicate window setup needed
        if (MyApplication.recoveryMode) {
            setContentView(R.layout.a_recovery)
            initRecoveryView()
            return
        }
        launchView = LaunchView(this)
        setContentView(launchView)
        // Apply status-bar top padding and navigation bar bottom padding to the complete root view
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(launchView) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(launchView)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
            }
        }
        initView()
        initObserver()
        registerResultCallback()
        checkHadCrash()
        SaveImageBSDialogFragment.safetyHide(this@MainActivity.supportFragmentManager)
    }

    private fun initRecoveryView() {
        val tvCrashInfo = findViewById<TextView>(R.id.tvCrashInfo).apply {
            with(getSharedPreferences(MyApplication.SP_NAME, MODE_PRIVATE)) {
                val crashInfo = getString(MyApplication.KEY_STACK_TRACE, "")
                text = crashInfo
            }
        }
        val btnCopy = findViewById<Button>(R.id.btnCopy).apply {
            setOnClickListener {
                try {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(tvCrashInfo.text, tvCrashInfo.text)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this@MainActivity, R.string.copy_success, Toast.LENGTH_SHORT)
                        .show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, R.string.copy_failed, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
        val btnSendEmail = findViewById<Button>(R.id.btnEmail).apply {
            setOnClickListener {
                viewModel.extraCrashInfo(this@MainActivity, tvCrashInfo.text.toString())
            }
        }
        val btnStore = findViewById<Button>(R.id.btnStore).apply {
            setOnClickListener {
                openLink(Uri.parse("market://details?id=com.mckimquyen.watermark")) {
                    Toast.makeText(this@MainActivity, R.string.store_not_found, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

        findViewById<Button>(R.id.btnCloseRecoveryMode).apply {
            setOnClickListener {
                (MyApplication.instance as MyApplication).launchSuccess()
                Toast.makeText(this@MainActivity, R.string.recovery_mode_closed, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (MyApplication.recoveryMode) {
            return
        }
    }

    private fun registerResultCallback() {
        pickIconLauncher = registerForActivityResult(PickImageContract()) { uri: Uri? ->
            handleActivityResult(REQ_PICK_ICON, listOf(uri))
        }

        signatureLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d(LOG_TAG, "[MAIN] signatureLauncher callback: resultCode=${result.resultCode}")
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val uriStr = result.data?.getStringExtra("signature_uri")
                Log.d(LOG_TAG, "[MAIN] signature_uri string from intent: $uriStr")
                if (uriStr != null) {
                    val signatureUri = android.net.Uri.parse(uriStr)
                    Log.d(LOG_TAG, "[MAIN] parsed Uri: $signatureUri  scheme=${signatureUri.scheme}")
                    // Grant read permission so ContentResolver can open this FileProvider URI
                    try {
                        contentResolver.takePersistableUriPermission(
                            signatureUri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        Log.d(LOG_TAG, "[MAIN] takePersistableUriPermission OK")
                    } catch (se: SecurityException) {
                        // FileProvider URIs don't support persistable grants – that's fine,
                        // the URI is already readable within this process lifetime.
                        Log.d(LOG_TAG, "[MAIN] takePersistableUriPermission SKIPPED (expected for FileProvider): ${se.message}")
                    }
                    Log.d(LOG_TAG, "[MAIN] calling viewModel.updateIcon(uri)")
                    viewModel.updateIcon(signatureUri)
                } else {
                    Log.d(LOG_TAG, "[MAIN] uriStr is NULL → nothing to update")
                }
            } else {
                Log.d(LOG_TAG, "[MAIN] resultCode is NOT RESULT_OK → ignored")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
    }

    override fun onStart() {
        super.onStart()
        // Accepting shared images from other apps
        if (intent?.action == ACTION_SEND && intent?.data != null) {
            dealWithImage(listOf(intent?.data!!))
        }
    }

    override fun onResume() {
        super.onResume()
        if (MyApplication.recoveryMode) {
            return
        }
        lifecycleScope.launch {
            delay(1000)
            if (this@MainActivity.isFinishing) {
                return@launch
            }
            (MyApplication.instance as? MyApplication?)?.launchSuccess()
        }
    }

    override fun onDestroy() {
        bgTransformAnimator?.cancel()
        bgTransformAnimator = null
        super.onDestroy()
    }

    private fun checkHadCrash() {
        with(getSharedPreferences(MyApplication.SP_NAME, MODE_PRIVATE)) {
            val isCrash = getBoolean(MyApplication.KEY_IS_CRASH, false)
            if (!isCrash) {
                return@with
            }
            val crashInfo = getString(MyApplication.KEY_STACK_TRACE, "")
            edit {
                putBoolean(MyApplication.KEY_IS_CRASH, false)
                putString(MyApplication.KEY_STACK_TRACE, "")
            }
            showCrashDialog(crashInfo)
        }
    }

    private fun showCrashDialog(crashInfo: String?) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.tips_tip_title)
            .setMessage(R.string.msg_crash)
            .setNegativeButton(
                R.string.tips_cancel_dialog
            ) { dialog, _ -> dialog?.dismiss() }
            .setPositiveButton(
                R.string.crash_mail
            ) { dialog, _ ->
                viewModel.extraCrashInfo(this, crashInfo)
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun initObserver() {
        lifecycleScope.launch {
            viewModel.uiStateFlow.flowWithLifecycle(
                this@MainActivity.lifecycle,
                Lifecycle.State.STARTED
            ).collect {
                if (it == UiState.GoEditDialog) {
                    TextWatermarkBSDFragment.safetyShow(supportFragmentManager)
                }
            }
        }
        viewModel.waterMark.observe(this) {
            if (it == null) {
                Log.d(LOG_TAG, "[MAIN] waterMark observer: value is NULL, skip")
                return@observe
            }
            Log.d(LOG_TAG, "[MAIN] waterMark observer: markMode=${it.markMode}, iconUri=${it.iconUri}, text='${it.text}'")
            launchView.post {
                Log.d(LOG_TAG, "[MAIN] launchView.post → setting ivPhoto.config")
                launchView.ivPhoto.config = it
            }
            if (it.markMode == WaterMarkRepository.MarkMode.Image && launchView.tabLayout.selectedTabPosition == 0) {
                Log.d(LOG_TAG, "[MAIN] markMode=Image → hideDetailPanel()")
                hideDetailPanel()
            }
            viewModel.resetJobStatus()
        }
        viewModel.selectedImage.observe(this) {
            if (it == null || it.uri.toString().isBlank()) {
                return@observe
            }
            try {
                val isAnimating = launchView.toEditorMode()
                if (isAnimating) {
                    launchView.ivPhoto.updateUri(true, it)
                    selectTab(0)
                } else {
                    launchView.ivPhoto.updateUri(false, it)
                }
            } catch (se: SecurityException) {
                se.printStackTrace()
                // reset the uri because we don't have permission -_-
                viewModel.selectImage(Uri.EMPTY)
            }
        }
        viewModel.imageList.observe(this) {
            photoListPreviewAdapter.selectedPos = viewModel.nextSelectedPos
            photoListPreviewAdapter.submitList(it.first.toList()) {
                if (it.second.not()) {
                    return@submitList
                }
                launchView.rvPhotoList.apply {
                    post { smoothScrollToPosition(0) }
                }
            }
        }
        viewModel.saveResult.observe(this) {
            if (it.isFailure()) {
                when (it.code) {
                    MainViewModel.TYPE_ERROR_SAVE_OOM -> {
                        toast(getString(R.string.error_save_oom))
                        CompressImageDialogFragment.safetyShow(supportFragmentManager)
                        viewModel.resetJobStatus()
                    }

                    MainViewModel.TYPE_ERROR_FILE_NOT_FOUND -> toast(getString(R.string.error_file_not_found))
                    MainViewModel.TYPE_ERROR_NOT_IMG -> toast(getString(R.string.error_not_img))
                    else -> toast("${getString(R.string.tips_error)}: ${it.message}")
                }
                viewModel.resetJobStatus()
            } else {
                toast(it.message)
                if (it.code == MainViewModel.TYPE_JOB_FINISH) {
                    launchView.postDelayed({
                        com.roy.sdkadbmob.AdManager.showInterstitial(this@MainActivity) {}
                    }, 800)
                }
            }
        }

        viewModel.colorPalette.observe(this) { palette ->
            val bgColor = palette.bgColor(this)
            val titleTextColor = palette.titleTextColor(this)

            bgTransformAnimator = currentBgColor.toColor(bgColor) {
                val c = it.animatedValue as Int
                if (launchView.isEdit()) {
                    doApplyBgChanged(c)
                } else {
                    doApplyBgChanged()
                }
            }
            funcAdapter.textColor.toColor(titleTextColor) {
                val c = it.animatedValue as Int
                funcAdapter.applyTextColor(c)
                launchView.tabLayout.setTabTextColors(c, this.colorPrimary)
                launchView.toolbar.menu.forEach { menuItem ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        menuItem.iconTintList = ColorStateList.valueOf(c)
                    } else {
                        menuItem.icon?.setTint(c)
                    }
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initView() {
        doApplyBgChanged()
        // prepare MotionLayout
        launchView.setListener {
            onModeChange { _, newMode ->
                when (newMode) {
                    LaunchView.ViewMode.Editor -> {
//                        launchView.logoView.stop()
                    }

                    LaunchView.ViewMode.LaunchMode -> {
//                        launchView.logoView.start()
                    }
                }
            }
        }
        // setting tool bar
        launchView.toolbar.apply {
            title = null
            setSupportActionBar(this)
            supportActionBar?.title = null
            // Back arrow (left) — same exit-editor flow as the system back button.
            navigationIcon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_arrow_back)
            setNavigationOnClickListener {
                @Suppress("DEPRECATION")
                onBackPressed()
            }
            // Small WATERMARK branding logo next to the back arrow (the raw drawable is too wide).
            logo = buildSmallWatermarkLogo()
        }
        // go about page
        launchView.ivGoAboutPage.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        // pick image button
        launchView.ivSelectedPhotoTips.setOnClickListener {
            preCheckStoragePermission {
                performFileSearch(REQ_CODE_PICK_IMAGE)
            }
        }
        // setting bg
        launchView.ivPhoto.apply {
            onBgReady { palette ->
                viewModel.updateColorPalette(palette)
            }
            onOffsetChanged {
                viewModel.updateOffset(it)
            }
            onScaleEnd {
                viewModel.updateTextSize(it)
            }
        }
        // functional panel in recyclerView
        launchView.rvPanel.apply {
            adapter = funcAdapter
            setHasFixedSize(true)
            layoutManager = CenterLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
            onItemClick { _, pos, v ->
                val snapView = snapHelper.findSnapView(launchView.rvPanel.layoutManager)
                if (snapView == v) {
                    val item = (this.adapter as FuncPanelAdapter).dataSet[pos]
                    handleFuncItem(item)
                    funcAdapter.selectedPos = pos
                } else {
                    smoothScrollToPosition(pos)
                }
            }

            onSnapViewPreview { snapView, _ ->
                vibrateHelper.doVibrate(snapView)
            }

            onSnapViewSelected { snapView, pos ->
                funcAdapter.selectedPos = pos
                handleFuncItem(funcAdapter.dataSet[pos])
                vibrateHelper.doVibrate(snapView)
            }

            post {
                canAutoSelected = false
                scrollToPosition(0)
                canAutoSelected = true
            }
        }
        // image list
        launchView.rvPhotoList.apply {
            enableBorder = true
            adapter = photoListPreviewAdapter
            setHasFixedSize(true)
            layoutManager =
                CenterLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false).apply {
                    onStartSmoothScroll {
                        canTouch = false
                    }
                    onStopSmoothScroll {
                        canTouch = true
                    }
                }

            photoListPreviewAdapter.onRemove { imageInfo ->
                viewModel.removeImage(imageInfo, photoListPreviewAdapter.selectedPos)
            }

            onItemClick { _, pos, v ->
                val snapView = snapHelper.findSnapView(launchView.rvPanel.layoutManager)
                if (snapView != v) {
                    smoothScrollToPosition(pos)
                }
            }

            onSnapViewPreview { snapView, _ ->
                vibrateHelper.doVibrate(snapView)
            }

            onSnapViewSelected { snapView, pos ->
                photoListPreviewAdapter.selectedPos = pos
                val uri = photoListPreviewAdapter.getItem(pos)?.uri ?: return@onSnapViewSelected
                viewModel.selectImage(uri)
                vibrateHelper.doVibrate(snapView)
            }
        }

        launchView.tabLayout.apply {
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    if (tab == null) {
                        return
                    }
                    hideDetailPanel()
                    vibrateHelper.doVibrate(this@apply)
                    val adapter = (launchView.rvPanel.adapter as? FuncPanelAdapter)
                    when (tab.position) {
                        0 -> {
                            val curPos =
                                if (launchView.ivPhoto.config?.markMode == WaterMarkRepository.MarkMode.Image) 1 else 0
                            if (curPos == 0) {
                                launchView.rvPanel.smoothScrollToPosition(0)
                                adapter?.also {
                                    it.seNewData(contentFunList, 0)
                                    post { handleFuncItem(it.dataSet[0]) }
                                }
                            } else {
                                hideDetailPanel()
                                adapter?.seNewData(contentFunList, curPos)
                                manuallySelectedItem(curPos)
                            }
                        }

                        2 -> {
                            launchView.rvPanel.smoothScrollToPosition(0)
                            adapter?.also {
                                it.seNewData(layoutFunList, 0)
                                post { handleFuncItem(it.dataSet[0]) }
                            }
                        }

                        else -> {
                            launchView.rvPanel.smoothScrollToPosition(0)
                            adapter?.also {
                                it.seNewData(styleFunList, 0)
                                post { handleFuncItem(it.dataSet[0]) }
                            }
                        }
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}

                override fun onTabReselected(tab: TabLayout.Tab?) {
                    if (tab?.position == 0) {
                        handleFuncItem(contentFunList[0])
                    }
                }
            })
        }
    }

    private fun hideDetailPanel() {
        commitWithAnimation {
            supportFragmentManager.fragments.forEach {
                remove(it)
            }
        }
    }

    private fun handleFuncItem(item: FuncTitleModel) {
//        Log.i("handleFuncItem", "item = $item")
        when (item.type) {
            FuncTitleModel.FuncType.Text -> {
                TextContentDisplayFragment.replaceShow(this, launchView.fcFunctionDetail.id)
            }

            FuncTitleModel.FuncType.Icon -> {
                preCheckStoragePermission {
                    performFileSearch(REQ_PICK_ICON)
                }
            }

            FuncTitleModel.FuncType.Color -> {
                ColorFragment.replaceShow(this, launchView.fcFunctionDetail.id)
            }

            FuncTitleModel.FuncType.Signature -> {
                hideDetailPanel()
                val intent = android.content.Intent(this@MainActivity, SignatureActivity::class.java)
                signatureLauncher.launch(intent)
            }

            FuncTitleModel.FuncType.QRCode -> {
                hideDetailPanel()
                com.mckimquyen.watermark.ui.dlg.QrCodeBottomSheetFragment.safetyShow(supportFragmentManager)
            }

            FuncTitleModel.FuncType.ExifBorder -> {
                hideDetailPanel()
                com.mckimquyen.watermark.ui.dlg.ExifPbFragment.safetyShow(supportFragmentManager)
            }

            FuncTitleModel.FuncType.Alpha -> {
                AlphaPbFragment.replaceShow(this, launchView.fcFunctionDetail.id)
            }

            FuncTitleModel.FuncType.Degree -> {
                DegreePbFragment.replaceShow(this, launchView.fcFunctionDetail.id)
            }

            FuncTitleModel.FuncType.TextStyle -> {
                TextStyleFragment.replaceShow(this, launchView.fcFunctionDetail.id)
            }

            FuncTitleModel.FuncType.Vertical -> {
                VerticalPbFragment.replaceShow(this, launchView.fcFunctionDetail.id)
            }

            FuncTitleModel.FuncType.Horizon -> {
                HorizonPbFragment.replaceShow(this, launchView.fcFunctionDetail.id)
            }

            FuncTitleModel.FuncType.TextSize -> {
                TextSizePbFragment.replaceShow(this, launchView.fcFunctionDetail.id)
            }

            FuncTitleModel.FuncType.TileMode -> {
                TileModeFragment.replaceShow(this, launchView.fcFunctionDetail.id)
            }
        }
    }

    private fun setStatusBarColor(color: Int, isInEditMode: Boolean) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val systemUiAppearance = if (isInEditMode && this.isNight()) {
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            } else {
                0
            }
            window.insetsController?.setSystemBarsAppearance(
                systemUiAppearance,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            val systemUiVisibilityFlags = if (!isInEditMode && !this.isNight()) {
                window.decorView.systemUiVisibility or SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                window.decorView.systemUiVisibility and SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
            window.decorView.systemUiVisibility = systemUiVisibilityFlags
        }
        window.statusBarColor = color
        window.findViewById<View>(android.R.id.content)?.foreground = null
    }

    /** Scale the (very wide) WATERMARK logo down to a small toolbar logo (~28dp tall). */
    private fun buildSmallWatermarkLogo(): android.graphics.drawable.Drawable? {
        val src = ContextCompat.getDrawable(this, R.drawable.ic_watermark)
        val targetH = (28 * resources.displayMetrics.density).toInt()
        if (src is android.graphics.drawable.BitmapDrawable && src.intrinsicHeight > 0) {
            val ratio = src.intrinsicWidth.toFloat() / src.intrinsicHeight
            val targetW = (targetH * ratio).toInt().coerceAtLeast(1)
            val scaled = android.graphics.Bitmap.createScaledBitmap(src.bitmap, targetW, targetH, true)
            return android.graphics.drawable.BitmapDrawable(resources, scaled)
        }
        return src
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.actionSettings -> {
            startActivity(Intent(this, AboutActivity::class.java))
            true
        }

        R.id.actionPick -> {
            preCheckStoragePermission {
                performFileSearch(REQ_CODE_PICK_IMAGE)
            }
            true
        }

        R.id.actionSave -> {
            SaveImageBSDialogFragment.safetyShow(supportFragmentManager)
            rateAppInApp(BuildConfig.DEBUG)
            true
        }

        else -> {
            super.onOptionsItemSelected(item)
        }
    }

    /**
     * Fires an intent to spin up the "file chooser" UI and select an image.
     */
    private fun performFileSearch(requestCode: Int) {
        if (requestCode == REQ_PICK_ICON) {
            val mime = "image/*"
            val result = kotlin.runCatching {
                when (requestCode) {
                    REQ_PICK_ICON -> {
                        pickIconLauncher.launch(mime)
                    }
                }
            }

            if (result.isFailure) {
                Toast.makeText(
                    this,
                    getString(R.string.tips_not_app_can_open_images),
                    Toast.LENGTH_LONG
                ).show()
                Log.i("performFileSearch", result.exceptionOrNull()?.message ?: "No msg provided")
            }
        } else {
            GalleryFragment().apply {
//                launchView.logoView.stop()
//                doOnDismiss {
//                    launchView.logoView.start()
//                }
                show(supportFragmentManager, "GalleryFragment")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(LOG_TAG, "onRequestPermissionsResult requestCode $requestCode")
        Log.d(LOG_TAG, "onRequestPermissionsResult permissions $permissions")
        Log.d(LOG_TAG, "onRequestPermissionsResult grantResults $grantResults")
        when (requestCode) {
            REQ_CODE_REQ_WRITE_PERMISSION -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(
                        this,
                        getString(R.string.request_permission_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Log.d(LOG_TAG, "onRequestPermissionsResult REQ_CODE_REQ_WRITE_PERMISSION")
                    launchView.ivSelectedPhotoTips.performClick()
                }
            }

            REQ_CODE_PICK_IMAGE -> {
                Log.d(LOG_TAG, "onRequestPermissionsResult REQ_CODE_PICK_IMAGE")
            }
        }
    }

    private fun dealWithImage(uri: List<Uri>) {
        if (FileUtils.isImage(this.contentResolver, uri.first())) {
            viewModel.updateImageList(uri)
        } else {
            Toast.makeText(
                this,
                getString(R.string.tips_choose_other_file_type),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleActivityResult(requestCode: Int, list: List<Uri?>?) {
        val finalList = list?.filterNotNull()?.filter {
            FileUtils.isImage(this.contentResolver, it)
        } ?: emptyList()
        if (finalList.isEmpty()) {
            Toast.makeText(
                this,
                getString(R.string.tips_do_not_choose_image),
                Toast.LENGTH_SHORT
            ).show()
            if (requestCode == REQ_PICK_ICON && viewModel.waterMark.value?.markMode == WaterMarkRepository.MarkMode.Text) {
                manuallySelectedItem(0)
            }
            return
        }
        when (requestCode) {
            REQ_CODE_PICK_IMAGE -> {
                Log.d(LOG_TAG, "requestCode REQ_CODE_PICK_IMAGE")
                Log.d(LOG_TAG, finalList.toTypedArray().contentToString())
                dealWithImage(finalList)
            }

            REQ_PICK_ICON -> {
                Log.d(LOG_TAG, "requestCode REQ_CODE_PICK_IMAGE")
                viewModel.updateIcon(finalList.first())
            }
        }
    }

    private fun manuallySelectedItem(pos: Int) {
        launchView.rvPanel.canAutoSelected = false
        funcAdapter.selectedPos = pos
        launchView.rvPanel.scrollToPosition(pos)
        launchView.rvPanel.canAutoSelected = true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (MyApplication.recoveryMode) {
            super.onBackPressed()
            return
        }
        if (launchView.mode == LaunchView.ViewMode.LaunchMode) {
            super.onBackPressed()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_exist_confirm)
            .setMessage(R.string.dialog_content_exist_confirm)
            .setNegativeButton(
                R.string.tips_confirm_dialog
            ) { _, _ ->
                resetView()
            }
            .setPositiveButton(
                R.string.dialog_cancel_exist_confirm
            ) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun resetView() {
        launchView.toLaunchMode()
        viewModel.resetJobStatus()
        viewModel.clearData()
        launchView.ivPhoto.reset()
        bgTransformAnimator?.cancel()
        TextContentDisplayFragment.remove(this)
        doApplyBgChanged()
        hideDetailPanel()
    }

    private fun doApplyBgChanged(
        color: Int = ContextCompat.getColor(
            this,
            R.color.md_theme_dark_background
        )
    ) {
        (launchView.parent as? View?)?.setBackgroundColor(color)
        window?.navigationBarColor = Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window?.navigationBarDividerColor = Color.TRANSPARENT
        }
//        setStatusBarColor(Color.BLACK, true)
    }

    private fun selectTab(index: Int) {
        launchView.tabLayout.getTabAt(index).let {
            launchView.tabLayout.selectTab(it)
        }
    }

    fun getImageList(): List<ImageInfo> {
        return photoListPreviewAdapter.data
    }

    fun getImageViewInfo(): ViewInfo {
        return ViewInfo.from(launchView.ivPhoto)
    }

    companion object {
        private const val REQ_CODE_PICK_IMAGE: Int = 42
        const val REQ_CODE_REQ_WRITE_PERMISSION: Int = 43
        const val REQ_PICK_ICON: Int = 44
    }
}
