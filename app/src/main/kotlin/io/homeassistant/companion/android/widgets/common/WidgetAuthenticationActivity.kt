package io.homeassistant.companion.android.widgets.common

import android.appwidget.AppWidgetManager
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
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                if (appWidgetId > -1) {
                    val intent = Intent(applicationContext, ButtonWidget::class.java).apply {
                        action = ButtonWidget.CALL_SERVICE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                    sendBroadcast(intent)
                }
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
