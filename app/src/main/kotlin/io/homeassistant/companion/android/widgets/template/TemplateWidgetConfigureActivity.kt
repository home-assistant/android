package io.homeassistant.companion.android.widgets.template

import android.appwidget.AppWidgetManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.database.widget.TemplateWidgetDao
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.databinding.WidgetTemplateConfigureBinding
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.applySafeDrawingInsets
import io.homeassistant.companion.android.util.getHexForColor
import io.homeassistant.companion.android.widgets.BaseWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.common.WidgetUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import timber.log.Timber

@AndroidEntryPoint
class TemplateWidgetConfigureActivity : BaseWidgetConfigureActivity<TemplateWidgetEntity, TemplateWidgetDao>() {
    private lateinit var binding: WidgetTemplateConfigureBinding

    override val serverSelect: View
        get() = binding.serverSelect

    override val serverSelectList: Spinner
        get() = binding.serverSelectList

    private var requestLauncherSetup = false

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = WidgetTemplateConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySafeDrawingInsets()

        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
            requestLauncherSetup = extras.getBoolean(
                ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER,
                false,
            )
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !requestLauncherSetup) {
            finish()
            return
        }

        val backgroundTypeValues = WidgetUtils.getBackgroundOptionList(this)
        binding.backgroundType.adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                backgroundTypeValues,
            )

        lifecycleScope.launch {
            val templateWidget = dao.get(appWidgetId)

            if (templateWidget?.serverId != null) {
                // Set server ID early for template rendering
                selectedServerId = templateWidget.serverId
            }
            setupServerSelect(templateWidget?.serverId)

            if (templateWidget != null) {
                binding.templateText.setText(templateWidget.template)
                binding.textSize.setText(templateWidget.textSize.toInt().toString())
                binding.addButton.setText(commonR.string.update_widget)
                if (templateWidget.template.isNotEmpty()) {
                    renderTemplateText(templateWidget.template)
                } else {
                    binding.renderedTemplate.text = getString(commonR.string.empty_template)
                    binding.addButton.isEnabled = false
                }
                binding.backgroundType.setSelection(
                    WidgetUtils.getSelectedBackgroundOption(
                        this@TemplateWidgetConfigureActivity,
                        templateWidget.backgroundType,
                        backgroundTypeValues,
                    ),
                )
                binding.textColor.isVisible = templateWidget.backgroundType == WidgetBackgroundType.TRANSPARENT
                binding.textColorWhite.isChecked =
                    templateWidget.textColor?.let {
                        it.toColorInt() == ContextCompat.getColor(
                            this@TemplateWidgetConfigureActivity,
                            android.R.color.white,
                        )
                    }
                        ?: true
                binding.textColorBlack.isChecked =
                    templateWidget.textColor?.let {
                        it.toColorInt() ==
                            ContextCompat.getColor(
                                this@TemplateWidgetConfigureActivity,
                                commonR.color.colorWidgetButtonLabelBlack,
                            )
                    }
                        ?: false
            } else {
                binding.backgroundType.setSelection(0)
            }
        }

        binding.templateText.doAfterTextChanged { renderTemplateText() }

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
                    lifecycleScope.launch {
                        requestWidgetCreation()
                    }
                } else {
                    showAddWidgetError() // this shouldn't be possible
                }
            } else {
                lifecycleScope.launch {
                    updateWidget()
                }
            }
        }
    }

    override fun onServerSelected(serverId: Int) = renderTemplateText()

    override suspend fun getPendingDaoEntity(): TemplateWidgetEntity {
        val serverId = checkNotNull(selectedServerId) { "Selected server ID is null" }
        val template = checkNotNull(binding.templateText.text?.toString()) { "Template text is null" }

        return TemplateWidgetEntity(
            id = appWidgetId,
            serverId = serverId,
            template = template,
            textSize = binding.textSize.text.toString().toFloat(),
            backgroundType = when (binding.backgroundType.selectedItem as String?) {
                getString(commonR.string.widget_background_type_dynamiccolor) -> WidgetBackgroundType.DYNAMICCOLOR
                getString(commonR.string.widget_background_type_transparent) -> WidgetBackgroundType.TRANSPARENT
                else -> WidgetBackgroundType.DAYNIGHT
            },
            textColor = if (binding.backgroundType.selectedItem as String? ==
                getString(commonR.string.widget_background_type_transparent)
            ) {
                getHexForColor(
                    if (binding.textColorWhite.isChecked) {
                        android.R.color.white
                    } else {
                        commonR.color.colorWidgetButtonLabelBlack
                    },
                )
            } else {
                null
            },
            lastUpdate = dao.get(appWidgetId)?.lastUpdate ?: "Loading",
        )
    }

    override val widgetClass: Class<*> = TemplateWidget::class.java

    private fun renderTemplateText() {
        val editableText = binding.templateText.text ?: return
        if (editableText.isNotEmpty()) {
            renderTemplateText(editableText.toString())
        } else {
            binding.renderedTemplate.text = getString(commonR.string.empty_template)
            binding.addButton.isEnabled = false
        }
    }

    private fun renderTemplateText(template: String) {
        val serverId = selectedServerId
        if (serverId == null) {
            Timber.w("Not rendering template because server is not set")
            return
        }

        lifecycleScope.launch {
            var templateText: String?
            var enabled: Boolean
            withContext(Dispatchers.IO) {
                try {
                    templateText =
                        serverManager.integrationRepository(serverId)
                            .renderTemplate(template, mapOf())
                            .toString()
                    enabled = true
                } catch (e: Exception) {
                    Timber.e(e, "Exception while rendering template")
                    // SerializationException suggests that template is not a String (= error)
                    templateText = getString(
                        if (e.cause is SerializationException) {
                            commonR.string.template_error
                        } else {
                            commonR.string.template_render_error
                        },
                    )
                    enabled = false
                }
            }
            binding.renderedTemplate.text =
                templateText?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY) }
            binding.addButton.isEnabled = enabled && isValidServerId()
        }
    }
}
