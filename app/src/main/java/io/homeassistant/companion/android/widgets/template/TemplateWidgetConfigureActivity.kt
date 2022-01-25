package io.homeassistant.companion.android.widgets.template

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Html.fromHtml
import android.view.View
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.widget.doAfterTextChanged
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.databinding.WidgetTemplateConfigureBinding
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class TemplateWidgetConfigureActivity : BaseActivity() {
    companion object {
        private const val TAG: String = "TemplateWidgetConfigAct"
        private const val PIN_WIDGET_CALLBACK = "io.homeassistant.companion.android.widgets.template.TemplateWidgetConfigureActivity.PIN_WIDGET_CALLBACK"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private lateinit var binding: WidgetTemplateConfigureBinding

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var requestLauncherSetup = false

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = WidgetTemplateConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
            requestLauncherSetup = extras.getBoolean(
                ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, false
            )
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !requestLauncherSetup) {
            finish()
            return
        }

        val templateWidgetDao = AppDatabase.getInstance(applicationContext).templateWidgetDao()
        val templateWidget = templateWidgetDao.get(appWidgetId)
        if (templateWidget != null) {
            binding.templateText.setText(templateWidget.template)
            binding.addButton.setText(commonR.string.update_widget)
            if (templateWidget.template.isNotEmpty())
                renderTemplateText(templateWidget.template)
            else {
                binding.renderedTemplate.text = getString(commonR.string.empty_template)
                binding.addButton.isEnabled = false
            }
            binding.deleteButton.visibility = VISIBLE
            binding.deleteButton.setOnClickListener(onDeleteWidget)
        }

        binding.templateText.doAfterTextChanged { editableText ->
            if (editableText == null)
                return@doAfterTextChanged
            if (editableText.isNotEmpty()) {
                binding.addButton.isEnabled = true
                renderTemplateText(editableText.toString())
            } else {
                binding.renderedTemplate.text = getString(commonR.string.empty_template)
                binding.addButton.isEnabled = false
            }
        }

        binding.addButton.setOnClickListener {
            if (requestLauncherSetup) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    getSystemService<AppWidgetManager>()?.requestPinAppWidget(
                        ComponentName(this, TemplateWidget::class.java),
                        null,
                        PendingIntent.getActivity(
                            this,
                            System.currentTimeMillis().toInt(),
                            Intent(this, TemplateWidgetConfigureActivity::class.java).putExtra(PIN_WIDGET_CALLBACK, true).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        )
                    )
                } else showAddWidgetError() // this shouldn't be possible
            } else {
                onAddWidget()
            }
        }
    }

    private fun onAddWidget() {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            showAddWidgetError()
            return
        }

        val createIntent = Intent().apply {
            action = BaseWidgetProvider.RECEIVE_DATA
            component = ComponentName(applicationContext, TemplateWidget::class.java)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(TemplateWidget.EXTRA_TEMPLATE, binding.templateText.text.toString())
        }
        applicationContext.sendBroadcast(createIntent)

        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )
        finish()
    }

    private fun showAddWidgetError() {
        Toast.makeText(applicationContext, commonR.string.widget_creation_error, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        mainScope.cancel()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null && intent.extras != null && intent.hasExtra(PIN_WIDGET_CALLBACK)) {
            appWidgetId = intent.extras!!.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
            onAddWidget()
        }
    }

    private fun renderTemplateText(template: String) {
        ioScope.launch {
            var templateText: String?
            var enabled: Boolean
            try {
                templateText = integrationUseCase.renderTemplate(template, mapOf())
                enabled = true
            } catch (e: Exception) {
                templateText = "Error in template"
                enabled = false
            }
            runOnUiThread {
                binding.renderedTemplate.text = fromHtml(templateText)
                binding.addButton.isEnabled = enabled
            }
        }
    }

    private var onDeleteWidget = View.OnClickListener {
        val context = this@TemplateWidgetConfigureActivity
        deleteConfirmation(context)
    }

    private fun deleteConfirmation(context: Context) {
        val templateWidgetDao = AppDatabase.getInstance(context).templateWidgetDao()

        val builder: android.app.AlertDialog.Builder = android.app.AlertDialog.Builder(context)

        builder.setTitle(commonR.string.confirm_delete_this_widget_title)
        builder.setMessage(commonR.string.confirm_delete_this_widget_message)

        builder.setPositiveButton(
            commonR.string.confirm_positive
        ) { dialog, _ ->
            templateWidgetDao.delete(appWidgetId)
            dialog.dismiss()
            finish()
        }

        builder.setNegativeButton(
            commonR.string.confirm_negative
        ) { dialog, _ -> // Do nothing
            dialog.dismiss()
        }

        val alert: android.app.AlertDialog? = builder.create()
        alert?.show()
    }
}
