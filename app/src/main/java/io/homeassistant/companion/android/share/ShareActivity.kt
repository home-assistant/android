package io.homeassistant.companion.android.share

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ShareActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ShareActivity"
    }

    @Inject
    lateinit var integrationRepository: IntegrationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DaggerShareComponent.builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        val data = mutableMapOf(
            "caller" to ActivityCompat.getReferrer(this).toString()
        )

        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_SUBJECT)?.let {
                data["subject"] = it
            }
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                if (it.toHttpUrlOrNull() == null)
                    data["text"] = it
                else
                    data["url"] = it
            }
        }
        runBlocking {
            try {
                integrationRepository.fireEvent("mobile_app.share", data)
                Log.d(TAG, "Share successful!")
                Toast.makeText(
                    applicationContext,
                    R.string.share_success,
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "Issue sharing with Home Assistant", e)
                Toast.makeText(
                    applicationContext,
                    R.string.share_failed,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        finish()
    }
}
