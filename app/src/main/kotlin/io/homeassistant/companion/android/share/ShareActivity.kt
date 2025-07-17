package io.homeassistant.companion.android.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

@AndroidEntryPoint
class ShareActivity : BaseActivity() {

    @Inject
    lateinit var serverManager: ServerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = mutableMapOf(
            "caller" to ActivityCompat.getReferrer(this).toString(),
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
                Timber.d("Share successful!")
                Toast.makeText(
                    applicationContext,
                    commonR.string.share_success,
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: Exception) {
                Timber.e(e, "Issue sharing with Home Assistant")
                Toast.makeText(
                    applicationContext,
                    commonR.string.share_failed,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        finish()
    }
}
