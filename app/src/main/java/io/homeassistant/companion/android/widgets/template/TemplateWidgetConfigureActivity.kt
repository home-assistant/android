package io.homeassistant.companion.android.widgets.template

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Html.fromHtml
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.widget.TemplateWidgetDao
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.databinding.WidgetTemplateConfigureBinding
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.getHexForColor
import io.homeassistant.companion.android.widgets.BaseWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class TemplateWidgetConfigureActivity : BaseWidgetConfigureActivity() {
    companion object {
        private const val TAG: String = "TemplateWidgetConfigAct"
        private const val PIN_WIDGET_CALLBACK = "io.homeassistant.companion.android.widgets.template.TemplateWidgetConfigureActivity.PIN_WIDGET_CALLBACK"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    @Inject
    lateinit var templateWidgetDao: TemplateWidgetDao
    override val dao get() = templateWidgetDao

    private lateinit var binding: WidgetTemplateConfigureBinding

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

        val templateWidget = templateWidgetDao.get(appWidgetId)

        val backgroundTypeValues = mutableListOf(
            getString(commonR.string.widget_background_type_daynight),
            getString(commonR.string.widget_background_type_transparent)
        )
        if (DynamicColors.isDynamicColorAvailable())
            backgroundTypeValues.add(0, getString(commonR.string.widget_background_type_dynamiccolor))
        binding.backgroundType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, backgroundTypeValues)

        if (templateWidget != null) {
            binding.templateText.setText(templateWidget.template)
            binding.textSize.setText(templateWidget.textSize.toInt().toString())
            binding.addButton.setText(commonR.string.update_widget)
            if (templateWidget.template.isNotEmpty())
                renderTemplateText(templateWidget.template)
            else {
                binding.renderedTemplate.text = getString(commonR.string.empty_template)
                binding.addButton.isEnabled = false
            }

            binding.backgroundType.setSelection(
                when {
                    templateWidget.backgroundType == WidgetBackgroundType.DYNAMICCOLOR && DynamicColors.isDynamicColorAvailable() ->
                        backgroundTypeValues.indexOf(getString(commonR.string.widget_background_type_dynamiccolor))
                    templateWidget.backgroundType == WidgetBackgroundType.TRANSPARENT ->
                        backgroundTypeValues.indexOf(getString(commonR.string.widget_background_type_transparent))
                    else ->
                        backgroundTypeValues.indexOf(getString(commonR.string.widget_background_type_daynight))
                }
            )
            binding.textColor.isVisible = templateWidget.backgroundType == WidgetBackgroundType.TRANSPARENT
            binding.textColorWhite.isChecked =
                templateWidget.textColor?.let { it.toColorInt() == ContextCompat.getColor(this, android.R.color.white) } ?: true
            binding.textColorBlack.isChecked =
                templateWidget.textColor?.let { it.toColorInt() == ContextCompat.getColor(this, commonR.color.colorWidgetButtonLabelBlack) } ?: false

            binding.deleteButton.visibility = View.VISIBLE
            binding.deleteButton.setOnClickListener(onDeleteWidget)
        } else {
            binding.backgroundType.setSelection(0)
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

        binding.backgroundType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.textColor.isVisible =
                    parent?.adapter?.getItem(position) == getString(commonR.string.widget_background_type_transparent)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                binding.textColor.visibility = View.GONE
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
            putExtra(TemplateWidget.EXTRA_TEXT_SIZE, binding.textSize.text.toString().toFloat())
            putExtra(
                TemplateWidget.EXTRA_BACKGROUND_TYPE,
                when (binding.backgroundType.selectedItem as String?) {
                    getString(commonR.string.widget_background_type_dynamiccolor) -> WidgetBackgroundType.DYNAMICCOLOR
                    getString(commonR.string.widget_background_type_transparent) -> WidgetBackgroundType.TRANSPARENT
                    else -> WidgetBackgroundType.DAYNIGHT
                }
            )
            putExtra(
                TemplateWidget.EXTRA_TEXT_COLOR,
                if (binding.backgroundType.selectedItem as String? == getString(commonR.string.widget_background_type_transparent))
                    getHexForColor(if (binding.textColorWhite.isChecked) android.R.color.white else commonR.color.colorWidgetButtonLabelBlack)
                else null
            )
        }
        applicationContext.sendBroadcast(createIntent)

        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )
        finish()
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
        lifecycleScope.launch {
            var templateText: String?
            var enabled: Boolean
            withContext(Dispatchers.IO) {
                try {
                    templateText = integrationUseCase.renderTemplate(template, mapOf()) ?: getString(commonR.string.template_error)
                    enabled = true
                } catch (e: Exception) {
                    templateText = getString(commonR.string.template_render_error)
                    enabled = false
                }
            }
            binding.renderedTemplate.text = fromHtml(templateText)
            binding.addButton.isEnabled = enabled
        }
    }
}
