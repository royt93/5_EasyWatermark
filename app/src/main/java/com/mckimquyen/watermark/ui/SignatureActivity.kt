package com.mckimquyen.watermark.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import com.mckimquyen.watermark.utils.ktx.toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mckimquyen.watermark.MyApplication
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.repo.SignatureModel
import com.mckimquyen.watermark.data.repo.SignatureRepository
import com.mckimquyen.watermark.databinding.ActivitySignatureBinding
import com.mckimquyen.watermark.ui.adapter.ColorPreviewAdapter
import com.mckimquyen.watermark.ui.widget.onItemClick
import android.view.LayoutInflater
import android.view.ViewGroup
import com.mckimquyen.watermark.ui.base.BaseViewHolder
import kotlinx.coroutines.launch

class SignatureHistoryAdapter(
    val data: MutableList<SignatureModel> = mutableListOf()
) : RecyclerView.Adapter<BaseViewHolder>() {

    var onItemClick: ((Int) -> Unit)? = null
    var onDeleteClick: ((Int) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val root = LayoutInflater.from(parent.context).inflate(R.layout.item_signature_history, parent, false)
        return BaseViewHolder(root)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        val item = data[position]
        val iv = holder.itemView.findViewById<ImageView>(R.id.ivSignature)
        val btnDelete = holder.itemView.findViewById<ImageView>(R.id.ivDeleteBtn)

        iv.setImageURI(item.uri)

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onItemClick?.invoke(pos)
        }
        btnDelete.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onDeleteClick?.invoke(pos)
        }
    }
}

class SignatureActivity : com.mckimquyen.watermark.BaseActivity() {

    private lateinit var binding: ActivitySignatureBinding
    private lateinit var repo: SignatureRepository
    private val historyAdapter = SignatureHistoryAdapter()
    private val colorAdapter by lazy {
        ColorPreviewAdapter(buildColorList(Color.WHITE))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignatureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = SignatureRepository(this)

        initViews()
        loadHistory()
    }

    private fun initViews() {
        binding.ivBack.setOnClickListener { finish() }
        binding.ivClear.setOnClickListener { 
            binding.signatureView.clear() 
            binding.tvEmptyHint.visibility = View.VISIBLE
        }
        binding.ivUndo.setOnClickListener {
            val hadStroke = binding.signatureView.undo()
            if (hadStroke && !binding.signatureView.hasStrokes()) {
                binding.tvEmptyHint.visibility = View.VISIBLE
            }
        }

        binding.signatureView.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                binding.tvEmptyHint.visibility = View.GONE
            }
            false
        }

        // Brush Size
        binding.sbSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.signatureView.drawSize = (progress + 3).toFloat()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        binding.signatureView.drawSize = 13f

        // Glow
        binding.swGlow.setOnCheckedChangeListener { _, isChecked ->
            binding.signatureView.isGlowEnabled = isChecked
        }

        // Colors
        binding.rvColor.apply {
            layoutManager = LinearLayoutManager(this@SignatureActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = colorAdapter
            onItemClick { recyclerView, position, _ ->
                colorAdapter.let {
                    val color = it.previewList[position].color
                    binding.signatureView.drawColor = color
                    it.updateSelectedColor(color)
                }
            }
        }

        // History
        binding.rvHistory.adapter = historyAdapter
        historyAdapter.onItemClick = { position ->
            val model = historyAdapter.data[position]
            returnResult(model.uri)
        }
        historyAdapter.onDeleteClick = { position ->
            val model = historyAdapter.data[position]
            lifecycleScope.launch {
                if (repo.deleteSignature(model)) {
                    historyAdapter.data.removeAt(position)
                    historyAdapter.notifyItemRemoved(position)
                    if (historyAdapter.data.isEmpty()) {
                        binding.llHistorySection.visibility = View.GONE
                    }
                }
            }
        }

        // Apply
        binding.btnApply.setOnClickListener {
            Log.d("roy93~", "[SIG] btnApply clicked")
            val bitmap = binding.signatureView.getSignatureBitmap()
            if (bitmap == null) {
                Log.d("roy93~", "[SIG] bitmap is NULL → draw empty, abort")
                toast(getString(R.string.draw_here))
                return@setOnClickListener
            }
            Log.d("roy93~", "[SIG] bitmap OK: ${bitmap.width}x${bitmap.height}")
            lifecycleScope.launch {
                val model = repo.saveSignature(bitmap)
                if (model != null) {
                    Log.d("roy93~", "[SIG] saveSignature OK → uri=${model.uri}")
                    Log.d("roy93~", "[SIG] uri scheme=${model.uri.scheme} path=${model.uri.path}")
                    toast("Signature Applied!")
                    returnResult(model.uri)
                } else {
                    Log.d("roy93~", "[SIG] saveSignature FAILED → model is null")
                    toast(getString(R.string.save_failed))
                }
            }
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val list = repo.getAllSignatures()
            if (list.isNotEmpty()) {
                binding.llHistorySection.visibility = View.VISIBLE
                historyAdapter.data.clear()
                historyAdapter.data.addAll(list)
                historyAdapter.notifyDataSetChanged()
            } else {
                binding.llHistorySection.visibility = View.GONE
            }
        }
    }

    private fun returnResult(uri: Uri) {
        Log.d("roy93~", "[SIG] returnResult → uri=$uri")
        val intent = Intent()
        intent.putExtra("signature_uri", uri.toString())
        // Grant read access to the content:// URI for the calling activity
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setResult(Activity.RESULT_OK, intent)
        Log.d("roy93~", "[SIG] setResult RESULT_OK done, calling finish()")
        finish()
    }

    private fun buildColorList(savedColor: Int): ArrayList<ColorPreviewAdapter.PreViewModel> {
        val colors = listOf(
            Color.WHITE, Color.BLACK, Color.parseColor("#FFB800"),
            Color.parseColor("#FF5252"), Color.parseColor("#4CAF50"), Color.parseColor("#2196F3"),
            Color.parseColor("#9C27B0"), Color.parseColor("#00BCD4"), Color.parseColor("#FF9800"),
            Color.parseColor("#E91E63")
        )
        val ls = ArrayList<ColorPreviewAdapter.PreViewModel>()
        colors.forEach { c ->
            ls.add(ColorPreviewAdapter.PreViewModel(color = c, selected = (c == savedColor)))
        }
        return ls
    }
}
