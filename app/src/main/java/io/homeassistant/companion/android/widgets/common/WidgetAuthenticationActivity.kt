package io.homeassistant.companion.android.widgets.common

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.companion.android.authenticator.Authenticator
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.widgets.button.ButtonWidget

class WidgetAuthenticationActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "WidgetAuthenticationA"
        const val EXTRA_TARGET = "io.homeassistant.companion.android.widgets.common.WidgetAuthenticationActivity.EXTRA_TARGET"
        const val EXTRA_ACTION = "io.homeassistant.companion.android.widgets.common.WidgetAuthenticationActivity.EXTRA_ACTION"
        const val EXTRA_EXTRAS = "io.homeassistant.companion.android.widgets.common.WidgetAuthenticationActivity.EXTRA_EXTRAS"
    }

    private var authenticating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate in WidgetAuthenticationActivity called")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isFinishing && !authenticating) {
            val authenticator = Authenticator(this, this, ::authenticationResult)
            authenticator.authenticate(getString(R.string.biometric_set_title))
            authenticating = true
        }
    }

    private fun authenticationResult(result: Int) {
        when (result) {
            Authenticator.SUCCESS -> {
                Log.d(TAG, "Authentication successful, calling requested service")
                val target = intent.getSerializableExtra(EXTRA_TARGET) ?: ButtonWidget::class.java
                val targetAction = intent.getStringExtra(EXTRA_ACTION) ?: ButtonWidget.CALL_SERVICE
                val extras = intent.getBundleExtra(EXTRA_EXTRAS) ?: Bundle()
                val intent = Intent(applicationContext, target as Class<*>).apply {
                    action = targetAction
                    putExtras(extras)
                }
                sendBroadcast(intent)
                finishAffinity()
            }
            Authenticator.CANCELED -> {
                Log.d(TAG, "Authentication canceled by user, closing activity")
                finishAffinity()
            }
            else -> {
                Log.d(TAG, "Authentication failed, retry attempts allowed")
                Toast.makeText(applicationContext, getString(R.string.widget_error_authenticating), Toast.LENGTH_LONG).show()
                finishAffinity()
            }
        }
    }
}
