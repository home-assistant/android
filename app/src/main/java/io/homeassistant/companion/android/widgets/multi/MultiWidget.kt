package io.homeassistant.companion.android.widgets.multi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.maltaisn.icondialog.pack.IconPack
import com.maltaisn.icondialog.pack.IconPackLoader
import com.maltaisn.iconpack.mdi.createMaterialDesignIconPack
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.widget.MultiWidgetDao
import io.homeassistant.companion.android.database.widget.MultiWidgetEntity
import io.homeassistant.companion.android.widgets.DaggerProviderComponent
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetButtonEntity
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetElement
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetElementEntity
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetPlaintextEntity
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetTemplateEntity
import java.util.regex.Pattern
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MultiWidget : AppWidgetProvider() {
    companion object {
        private const val TAG = "MultiWidget"
        private const val CALL_SERVICE =
            "io.homeassistant.companion.android.widgets.multi.MultiWidget.CALL_SERVICE"
        internal const val RECEIVE_DATA =
            "io.homeassistant.companion.android.widgets.multi.MultiWidget.RECEIVE_DATA"
        private const val UPDATE_WIDGET =
            "io.homeassistant.companion.android.widgets.multi.MultiWidget.UPDATE_WIDGET"

        // Const for identifying which button is being pressed
        private const val ELEMENT_ID = "INTENT_ELEMENT_ID"

        // Const for passing the number and types of elements from config
        internal const val EXTRA_ELEMENT_TYPES = "EXTRA_ELEMENT_TYPES"

        // Button element constants
        internal const val EXTRA_DOMAIN = "EXTRA_DOMAIN_"
        internal const val EXTRA_SERVICE = "EXTRA_SERVICE_"
        internal const val EXTRA_SERVICE_DATA = "EXTRA_SERVICE_DATA_"
        internal const val EXTRA_ICON_ID = "EXTRA_ICON_ID_"

        // Plaintext element constants
        internal const val EXTRA_LABEL = "EXTRA_LABEL_"
        internal const val EXTRA_LABEL_TEXT_SIZE = "EXTRA_LABEL_TEXT_SIZE_"
        internal const val EXTRA_LABEL_MAX_LINES = "EXTRA_LABEL_MAX_LINES_"

        // Template element constants
        internal const val EXTRA_TEMPLATE = "EXTRA_TEMPLATE_"
        internal const val EXTRA_TEMPLATE_TEXT_SIZE = "EXTRA_TEMPLATE_TEXT_SIZE_"
        internal const val EXTRA_TEMPLATE_MAX_LINES = "EXTRA_TEMPLATE_MAX_LINES_"

        // Label text size units are in SP
        internal const val LABEL_TEXT_SMALL = 12
        internal const val LABEL_TEXT_MED = 16
        internal const val LABEL_TEXT_LARGE = 24
    }

    enum class Orientation {
        // Currently only vertical is supported
        // Orientation is included for future-proofing
        HORIZONTAL, VERTICAL
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    lateinit var multiWidgetDao: MultiWidgetDao

    private var iconPack: IconPack? = null

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        multiWidgetDao = AppDatabase.getInstance(context).multiWidgetDao()
        // There may be multiple widgets active, so update all of them
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(
                context,
                appWidgetId,
                appWidgetManager
            )
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    ) {
        mainScope.launch {
            val views = getWidgetRemoteViews(context, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

        Log.d(
            TAG, "Broadcast received: " + System.lineSeparator() +
                    "Broadcast action: " + action + System.lineSeparator() +
                    "AppWidgetId: " + appWidgetId
        )

        ensureInjected(context)

        multiWidgetDao = AppDatabase.getInstance(context).multiWidgetDao()

        super.onReceive(context, intent)
        when (action) {
            UPDATE_WIDGET -> updateAppWidget(context, appWidgetId)
            RECEIVE_DATA -> saveConfiguration(context, intent.extras, appWidgetId)
            CALL_SERVICE -> callService(context, appWidgetId, intent.extras?.getInt(ELEMENT_ID))
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        multiWidgetDao = AppDatabase.getInstance(context).multiWidgetDao()
        // When the user deletes the widget, delete the data associated with it.
        for (appWidgetId in appWidgetIds) {
            multiWidgetDao.delete(appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    private suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        // Every time AppWidgetManager.updateAppWidget(...) is called, the click listeners
        // and label/template need to be re-assigned, or  the widget will fall back on its
        // default layout without any click listeners being applied

        // Fetch widget and create basic RemoteViews to populate with dynamic elements
        val widget = multiWidgetDao.get(appWidgetId)
        val widgetView = RemoteViews(context.packageName, R.layout.widget_multi)

        // Clear the widget before re-adding elements
        widgetView.removeAllViews(R.id.widget_multi_element_layout)

        // Analyze each element in the widget and add it to the view
        widget?.elements?.forEachIndexed { index, element ->
            val elementView: RemoteViews = when (element.type) {
                MultiWidgetElement.Type.BUTTON ->
                    getWidgetButtonRemoteViews(context, appWidgetId, element, index)
                MultiWidgetElement.Type.PLAINTEXT ->
                    getWidgetPlaintextRemoteViews(context, appWidgetId, element)
                MultiWidgetElement.Type.TEMPLATE ->
                    getWidgetTemplateRemoteViews(context, appWidgetId, element)
            }

            // Add the element view to the main widget remote view
            widgetView.addView(R.id.widget_multi_element_layout, elementView)
        }

        return widgetView
    }

    private fun getWidgetButtonRemoteViews(
        context: Context,
        appWidgetId: Int,
        element: MultiWidgetElementEntity,
        index: Int
    ): RemoteViews {
        val buttonElement = element as MultiWidgetButtonEntity

        // Create an icon pack and load all drawables if not already loaded
        if (iconPack == null) {
            val loader = IconPackLoader(context)
            iconPack = createMaterialDesignIconPack(loader)
            iconPack!!.loadDrawables(loader.drawableLoader)
        }

        // Create an intent for the button press
        val serviceIntent = Intent(context, MultiWidget::class.java).apply {
            action = CALL_SERVICE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            // Data must be different for each intent or only one intent is created
            data = Uri.parse("hasssvc:element$index")
            putExtra(ELEMENT_ID, index)
        }
        Log.d(TAG, "Service call intent created for element $index.")

        // Create new button view
        return RemoteViews(
            context.packageName,
            R.layout.widget_multi_element_button
        ).apply {
            // Tie the service call intent and set button icon
            setOnClickPendingIntent(
                R.id.widget_multi_element_button,
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    serviceIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            val iconDrawable = iconPack?.icons?.get(buttonElement.iconId)?.drawable
            if (iconDrawable != null) {
                val icon = DrawableCompat.wrap(iconDrawable)
                setImageViewBitmap(R.id.widget_multi_element_button, icon.toBitmap())
            }
        }
    }

    private fun getWidgetPlaintextRemoteViews(
        context: Context,
        appWidgetId: Int,
        element: MultiWidgetElementEntity
    ): RemoteViews {
        val plaintextElement = element as MultiWidgetPlaintextEntity

        // Create an intent to update the widget on tap
        val updateIntent = Intent(context, MultiWidget::class.java).apply {
            action = UPDATE_WIDGET
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        // Create new label view
        return RemoteViews(
            context.packageName,
            R.layout.widget_multi_element_label
        ).apply {
            // Tie the update intent and set the text of the label
            setOnClickPendingIntent(
                R.id.widget_multi_element_label,
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    updateIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            setTextViewText(R.id.widget_multi_element_label, plaintextElement.text)
            setTextViewTextSize(
                R.id.widget_multi_element_label,
                TypedValue.COMPLEX_UNIT_SP,
                plaintextElement.textSize.toFloat()
            )
            setInt(
                R.id.widget_multi_element_label,
                "setMaxLines",
                plaintextElement.maxLines
            )
        }
    }

    private suspend fun getWidgetTemplateRemoteViews(
        context: Context,
        appWidgetId: Int,
        element: MultiWidgetElementEntity
    ): RemoteViews {
        val templateElement = element as MultiWidgetTemplateEntity

        var renderedTemplate = "Loading..."
        try {
            renderedTemplate = integrationUseCase.renderTemplate(
                templateElement.templateData,
                mapOf()
            )
            Log.d(TAG, "Template rendering returned: '$renderedTemplate'")
        } catch (e: Exception) {
            Log.e(TAG, "Cannot render template: ${templateElement.templateData}", e)
        }

        // Create an intent to update the widget on tap
        val updateIntent = Intent(context, MultiWidget::class.java).apply {
            action = UPDATE_WIDGET
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        // Create new label view
        return RemoteViews(
            context.packageName,
            R.layout.widget_multi_element_label
        ).apply {
            // Tie the update intent and set the text of the label
            setOnClickPendingIntent(
                R.id.widget_multi_element_label,
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    updateIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )

            setTextViewText(R.id.widget_multi_element_label, renderedTemplate)
            setTextViewTextSize(
                R.id.widget_multi_element_label,
                TypedValue.COMPLEX_UNIT_SP,
                templateElement.textSize.toFloat()
            )
            setInt(
                R.id.widget_multi_element_label,
                "setMaxLines",
                templateElement.maxLines
            )
        }
    }

    private fun saveConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        // Retrieve element type array from extras bundle
        @Suppress("UNCHECKED_CAST")
        val elementTypes: Array<MultiWidgetElement.Type> =
            extras.getSerializable(EXTRA_ELEMENT_TYPES) as Array<MultiWidgetElement.Type>

        // Set up variables for elements from extras
        val elements = ArrayList<MultiWidgetElementEntity>()

        Log.d(TAG, "Saving multi widget config data:")
        elementTypes.forEachIndexed { index, elementType ->
            when (elementType) {
                MultiWidgetElement.Type.BUTTON -> {
                    elements.add(
                        MultiWidgetButtonEntity(
                            appWidgetId,
                            index,
                            extras.getString(EXTRA_DOMAIN + index)!!,
                            extras.getString(EXTRA_SERVICE + index)!!,
                            extras.getString(EXTRA_SERVICE_DATA + index)!!,
                            extras.getInt(EXTRA_ICON_ID + index)
                        )
                    )
                    Log.d(
                        TAG, "Element $index, $elementType:" + System.lineSeparator() +
                                extras.getString(EXTRA_DOMAIN + index)!! + System.lineSeparator() +
                                extras.getString(EXTRA_SERVICE + index)!! + System.lineSeparator() +
                                extras.getString(EXTRA_SERVICE_DATA + index)!! + System.lineSeparator() +
                                extras.getInt(EXTRA_ICON_ID + index)
                    )
                }
                MultiWidgetElement.Type.PLAINTEXT -> {
                    elements.add(
                        MultiWidgetPlaintextEntity(
                            appWidgetId,
                            index,
                            extras.getString(EXTRA_LABEL + index)!!,
                            extras.getInt(EXTRA_LABEL_TEXT_SIZE + index),
                            extras.getInt(EXTRA_LABEL_MAX_LINES + index)
                        )
                    )
                    Log.d(
                        TAG, "Element $index, $elementType:" + System.lineSeparator() +
                                extras.getString(EXTRA_LABEL + index)!! + System.lineSeparator() +
                                extras.getInt(EXTRA_LABEL_TEXT_SIZE + index) + System.lineSeparator() +
                                extras.getInt(EXTRA_LABEL_MAX_LINES + index)
                    )
                }
                MultiWidgetElement.Type.TEMPLATE -> {
                    elements.add(
                        MultiWidgetTemplateEntity(
                            appWidgetId,
                            index,
                            extras.getString(EXTRA_TEMPLATE + index)!!,
                            extras.getInt(EXTRA_TEMPLATE_TEXT_SIZE + index),
                            extras.getInt(EXTRA_TEMPLATE_MAX_LINES + index)
                        )
                    )
                    Log.d(
                        TAG,
                        "Element $index, $elementType:" + System.lineSeparator() +
                                extras.getString(EXTRA_TEMPLATE + index)!! + System.lineSeparator() +
                                extras.getInt(EXTRA_TEMPLATE_TEXT_SIZE + index) + System.lineSeparator() +
                                extras.getInt(EXTRA_TEMPLATE_MAX_LINES + index)
                    )
                }
            }
        }

        mainScope.launch {
            multiWidgetDao.add(MultiWidgetEntity(appWidgetId, elements, Orientation.VERTICAL))

            Log.d(TAG, "Saved multi widget config data.")

            // It is the responsibility of the configuration activity to update the app widget
            // This method is only called during the initial setup of the widget,
            // so rather than duplicating code in the ButtonWidgetConfigurationActivity,
            // it is just calling onUpdate manually here.
            updateAppWidget(context, appWidgetId)
        }
    }

    private fun callService(context: Context, appWidgetId: Int, elementId: Int?) {
        val widget = multiWidgetDao.get(appWidgetId)

        if (widget == null) {
            Log.e(TAG, "Could not retrieve widget from database; aborting.")
            return
        }

        val buttonEntity: MultiWidgetButtonEntity
        try {
            buttonEntity = widget.elements[elementId!!] as MultiWidgetButtonEntity
        } catch (e: IndexOutOfBoundsException) {
            Log.e(TAG, "Could not find correct widget element; aborting")
            return
        }

        mainScope.launch {
            // Load the service call data from database
            val domain = buttonEntity.domain
            val service = buttonEntity.service
            val serviceDataJson = buttonEntity.serviceData

            Log.d(
                TAG, "Service Call Data loaded for element $elementId:" + System.lineSeparator() +
                        "domain: " + domain + System.lineSeparator() +
                        "service: " + service + System.lineSeparator() +
                        "service_data: " + serviceDataJson
            )

            // Package the service data and attempt the call
            try {
                // Convert JSON to HashMap
                val serviceDataMap: HashMap<String, Any> =
                    jacksonObjectMapper().readValue(serviceDataJson)

                if (serviceDataMap["entity_id"] != null) {
                    val entityIdWithoutBrackets = Pattern.compile("\\[(.*?)\\]")
                        .matcher(serviceDataMap["entity_id"].toString())
                    if (entityIdWithoutBrackets.find()) {
                        val value = entityIdWithoutBrackets.group(1)
                        if (value != null) {
                            if (value == "all" || value.split(",").contains("all")) {
                                serviceDataMap["entity_id"] = "all"
                            }
                        }
                    }
                }

                integrationUseCase.callService(domain, service, serviceDataMap)
            } catch (e: Exception) {
                Log.e(TAG, "Could not send service call.", e)
                Toast.makeText(context, R.string.widget_service_error, Toast.LENGTH_SHORT)
                    .show()
            }

            // Update app widget once service call has been made
            // in case template needs to display a state change
            updateAppWidget(context, appWidgetId)
        }
    }

    private fun ensureInjected(context: Context) {
        if (context.applicationContext is GraphComponentAccessor) {
            DaggerProviderComponent.builder()
                .appComponent((context.applicationContext as GraphComponentAccessor).appComponent)
                .build()
                .inject(this)
        } else {
            throw Exception("Application Context passed is not of our application!")
        }
    }
}
