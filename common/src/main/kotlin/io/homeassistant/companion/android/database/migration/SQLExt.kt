package io.homeassistant.companion.android.database.migration

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement

/**
 * Runs [sql] and maps every result row with [map], reading the whole result into memory and
 * closing the statement before returning.
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

internal fun SQLiteStatement.getStringByColumnName(name: String): String = getText(columnIndex(name))

internal fun SQLiteStatement.getStringByColumnNameOrNull(name: String): String? =
    columnIndex(name).let { if (it < 0 || isNull(it)) null else getText(it) }

internal fun SQLiteStatement.getIntByColumnName(name: String): Int = getInt(columnIndex(name))

internal fun SQLiteStatement.getFloatByColumnNameOrZero(name: String): Float =
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
