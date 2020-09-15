package io.homeassistant.companion.android.share

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import java.lang.Exception
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

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

        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                Log.d(TAG, "Got share request for: $it")
                runBlocking {
                    try {
                        integrationRepository.fireEvent("mobile_app.share", mapOf("text" to it))
                        Log.d(TAG, "Share successful!")
                    } catch (e: Exception) {
                        Log.e(TAG, "Issue sharing with Home Assistant", e)
                    }
                }
            }
        }
        finish()
    }
}
