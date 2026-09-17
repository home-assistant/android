package io.homeassistant.companion.android.database.migration

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.CHANNEL_DATABASE
import io.homeassistant.companion.android.common.util.SdkVersion

private const val NOTIFICATION_ID = 45
private const val TAG = "AppDatabase"

/**
 * Runs [sql] and maps every result row with [map], reading the whole result into memory and
 * closing the statement before returning. Reading fully first lets callers rebuild the queried
 * table afterwards without holding an open cursor over it.
 */
internal inline fun <T> SQLiteConnection.query(sql: String, map: (SQLiteStatement) -> T): List<T> =
    prepare(sql).use { statement ->
        buildList { while (statement.step()) add(map(statement)) }
    }

/** 0-based index of [name] in the current row, or `-1` when the column is absent. */
internal fun SQLiteStatement.columnIndex(name: String): Int {
    for (index in 0 until getColumnCount()) {
        if (getColumnName(index) == name) return index
    }
    return -1
}

internal fun SQLiteStatement.getString(name: String): String = getText(columnIndex(name))

internal fun SQLiteStatement.getStringOrNull(name: String): String? =
    columnIndex(name).let { if (it < 0 || isNull(it)) null else getText(it) }

internal fun SQLiteStatement.getIntByName(name: String): Int = getInt(columnIndex(name))

internal fun SQLiteStatement.getLongByName(name: String): Long = getLong(columnIndex(name))

internal fun SQLiteStatement.getFloatOrZero(name: String): Float =
    columnIndex(name).let { if (it < 0 || isNull(it)) 0f else getFloat(it) }

/**
 * Inserts [values] into [table] with `INSERT OR REPLACE`. Keys are column names; supported value
 * types are [String], [Int], [Long], [Boolean], [Float], [Double] and `null`. Returns the row id.
 */
internal fun SQLiteConnection.insertOrReplace(table: String, values: Map<String, Any?>): Long {
    val columns = values.keys.toList()
    val sql = buildString {
        append("INSERT OR REPLACE INTO `").append(table).append("` (")
        append(columns.joinToString(",") { "`$it`" })
        append(") VALUES (")
        append(columns.joinToString(",") { "?" })
        append(")")
    }
    prepare(sql).use { statement ->
        columns.forEachIndexed { i, column ->
            val index = i + 1
            when (val value = values[column]) {
                null -> statement.bindNull(index)
                is String -> statement.bindText(index, value)
                is Boolean -> statement.bindBoolean(index, value)
                is Int -> statement.bindInt(index, value)
                is Long -> statement.bindLong(index, value)
                is Float -> statement.bindFloat(index, value)
                is Double -> statement.bindDouble(index, value)
                else -> error("Unsupported value type for column `$column`: ${value::class}")
            }
        }
        statement.step()
    }
    return prepare("SELECT last_insert_rowid()").use {
        it.step()
        it.getLong(0)
    }
}

private fun createNotificationChannel(context: Context) {
    if (SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
        val notificationManager = context.getSystemService<NotificationManager>()!!

        var notificationChannel =
            notificationManager.getNotificationChannel(CHANNEL_DATABASE)
        if (notificationChannel == null) {
            notificationChannel = NotificationChannel(
                CHANNEL_DATABASE,
                TAG,
                NotificationManager.IMPORTANCE_HIGH,
            )
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }
}
internal fun notifyMigrationFailed(context: Context) {
    createNotificationChannel(context)
    val notification = NotificationCompat.Builder(context, CHANNEL_DATABASE)
        .setSmallIcon(commonR.drawable.ic_stat_ic_notification)
        .setContentTitle(context.getString(commonR.string.database_migration_failed))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()
    with(NotificationManagerCompat.from(context)) {
        notify(NOTIFICATION_ID, notification)
    }
}
