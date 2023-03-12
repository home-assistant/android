package io.homeassistant.companion.android.update

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatDialog
import io.homeassistant.companion.android.R


class HintDialog(context: Context) : AppCompatDialog(context, R.style.Update_Dialog) {
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        window!!.decorView.setPadding(0, 0, 0, 0)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.update_dialog)
        findViewById<View>(R.id.hint)?.visibility = View.GONE
        findViewById<View>(R.id.progress)?.visibility = View.GONE
        findViewById<View>(R.id.layoutBtn)?.visibility = View.GONE
        findViewById<View>(R.id.gzh)?.setOnClickListener {
            val clipboard: ClipboardManager? =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
            val clip = ClipData.newPlainText("UnknownExceptions", "UnknownExceptions")
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, "已复制公众号名称到剪切板，点击弹窗外部关闭弹窗！", Toast.LENGTH_SHORT).show()
        }
    }
}
