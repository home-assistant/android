package io.homeassistant.companion.android.share

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class ShareActivity : BaseActivity() {

    companion object {
        private const val TAG = "ShareActivity"
    }

    @Inject
    lateinit var serverManager: ServerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = mutableMapOf(
            "caller" to ActivityCompat.getReferrer(this).toString()
        )

        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_SUBJECT)?.let {
                data["subject"] = it
            }
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                if (it.toHttpUrlOrNull() == null) {
                    data["text"] = it
                } else {
                    data["url"] = it
                }
            }
        }
        runBlocking {
            try {
                serverManager.integrationRepository().fireEvent("mobile_app.share", data)
                Log.d(TAG, "Share successful!")
                Toast.makeText(
                    applicationContext,
                    commonR.string.share_success,
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "Issue sharing with Home Assistant", e)
                Toast.makeText(
                    applicationContext,
                    commonR.string.share_failed,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        finish()
    }
}
