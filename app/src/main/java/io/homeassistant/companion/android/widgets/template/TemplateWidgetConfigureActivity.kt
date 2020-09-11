package io.homeassistant.companion.android.widgets.template

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.widgets.DaggerProviderComponent
import javax.inject.Inject
import kotlinx.android.synthetic.main.widget_template_configure.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TemplateWidgetConfigureActivity : AppCompatActivity() {
    companion object {
        private const val TAG: String = "TemplateWidgetConfigAct"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        setContentView(R.layout.widget_template_configure)

        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Inject components
        DaggerProviderComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        templateText.doAfterTextChanged { editableText ->
            if (editableText == null)
                return@doAfterTextChanged
            ioScope.launch {
                var templateText: String?
                var enabled: Boolean
                try {
                    templateText = integrationUseCase.renderTemplate(editableText.toString(), mapOf())
                    enabled = true
                } catch (e: Exception) {
                    templateText = "Error in template"
                    enabled = false
                }
                runOnUiThread {
                    renderedTemplate.text = templateText
                    add_button.isEnabled = enabled
                }
            }
        }

        add_button.setOnClickListener {
            val createIntent = Intent().apply {
                action = TemplateWidget.RECEIVE_DATA
                component = ComponentName(applicationContext, TemplateWidget::class.java)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(TemplateWidget.EXTRA_TEMPLATE, templateText.text.toString())
            }
            applicationContext.sendBroadcast(createIntent)

            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
            finish()
        }
    }

    override fun onDestroy() {
        mainScope.cancel()
        super.onDestroy()
    }
}
