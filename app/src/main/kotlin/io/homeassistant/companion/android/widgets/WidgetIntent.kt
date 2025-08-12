package io.homeassistant.companion.android.widgets

/**
 * Sent when the system has created a widget.
 *
 * The intent will contain the following extras:
 * - [android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID] The appWidgetIds of the created widget
 * - [EXTRA_WIDGET_ENTITY] The WidgetEntity to create inside the application database.
 *
 * This action needs to be used when invoking [androidx.glance.appwidget.GlanceAppWidgetManager.requestPinGlanceAppWidget].
 */
const val ACTION_APPWIDGET_CREATED = "io.homeassistant.companion.android.widgets.APPWIDGET_CREATED"

/**
 * Should be a subtype of [io.homeassistant.companion.android.database.widget.WidgetEntity] to inject in the DAO with the
 * ID of the widget. It will be retrieved using [java.io.Serializable].
 */
const val EXTRA_WIDGET_ENTITY = "widget_entity_id_to_update"
