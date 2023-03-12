package io.homeassistant.companion.android.update

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDialog
import io.homeassistant.companion.android.R

class UpdateDialog(context: Context, private val updateInfo: UpdateInfo) :
    AppCompatDialog(context, R.style.Update_Dialog) {
    private var layoutBtn: View? = null
    private var progress: ProgressBar? = null
    private var clean: View? = null
    private var hint: TextView? = null
    private var updateInterface: ((type: Int) -> Unit)? = null
    private var isUpdateing = false

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        window!!.decorView.setPadding(0, 0, 0, 0)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.update_dialog)
        findViewById<View>(R.id.go)?.setOnClickListener {
            extracted()
            updateInterface?.let { it(1) }
        }
        hint = findViewById(R.id.hint)
        hint?.text = "发现新版本(${updateInfo.version})\n${updateInfo.updateMsg}"
        clean = findViewById(R.id.clean)
        progress = findViewById(R.id.progress)
        layoutBtn = findViewById(R.id.layoutBtn)
        if (updateInfo.updateType != 2) {
            clean?.visibility = View.VISIBLE
            clean?.setOnClickListener { dismiss() }
        }
        if (isUpdateing) {
            extracted()
        } else {
            progress?.visibility = View.GONE
            layoutBtn?.visibility = View.VISIBLE
        }

        findViewById<View>(R.id.gzh)?.setOnClickListener {
            val clipboard: ClipboardManager? =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
            val clip = ClipData.newPlainText("UnknownExceptions", "UnknownExceptions")
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, "已复制公众号名称到剪切板，点击弹窗外部关闭弹窗！", Toast.LENGTH_SHORT).show()
        }
    }

    override fun dismiss() {
        super.dismiss()
        updateInterface?.let { it(0) }
    }

    private fun extracted() {
        hint!!.text = "正在下载(0%)"
        progress?.visibility = View.VISIBLE
        layoutBtn?.visibility = View.GONE
    }

    fun setOnClickListener(l: ((type: Int) -> Unit)?) {
        updateInterface = l
    }

    @SuppressLint("SetTextI18n")
    fun setProgress(arg1: Int, arg2: Int) {
        progress?.max = arg2
        progress?.progress = arg1
        val p = (arg1 * 100L) / arg2
        hint!!.text = "正在下载($p%)"
        if (p == 100L && updateInfo.updateType != 2) {
            dismiss()
        }
    }

    fun isUpdateing() {
        isUpdateing = true
    }
}
