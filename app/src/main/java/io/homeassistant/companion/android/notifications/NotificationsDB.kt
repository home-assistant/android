package io.homeassistant.companion.android.notifications

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class NotificationsDB(private val c: Context) {

    private var d: SQLiteDatabase? = null

    @Throws(SQLException::class)
    fun open() {

        val mDbHelper = DatabaseHelper(c)
        d = mDbHelper.writableDatabase
    }

    @Throws(SQLException::class)
    fun addMessage(
        tag: String?,
        title: String?,
        message: String?,
        image: String?,
        timestamp: String,
        read: Boolean,
        direction: String
    ) {

        val content = ContentValues()

        content.put(KEY_TAG, tag)
        content.put(KEY_TITLE, title)
        content.put(KEY_MESSAGE, message)
        content.put(KEY_IMAGE_URI, image)
        content.put(KEY_TIMESTAMP, timestamp)
        content.put(KEY_READ, read)
        content.put(KEY_DIRECTION, direction)

        d?.insert(SQLITE_TABLE_MESSAGE_STREAM, null, content)
    }

    @Throws(SQLException::class)
    fun fetchMessages(): Cursor {

        return d!!.rawQuery(
            "SELECT * FROM " + SQLITE_TABLE_MESSAGE_STREAM + " ORDER BY " +
                    KEY_ENTRY_UID, null
        )
    }

    @Throws(SQLException::class)
    fun deleteMessage(s: String) {

        d!!.delete(SQLITE_TABLE_MESSAGE_STREAM, "$KEY_ENTRY_UID = ? ", arrayOf(s))
    }

    @Throws(SQLException::class)
    fun clearMessage(s: String) {

        d!!.delete(SQLITE_TABLE_MESSAGE_STREAM, "$KEY_TAG = ? ", arrayOf(s))
    }

    fun close() {
        d!!.close()
    }

    private class DatabaseHelper internal constructor(c: Context) :
        SQLiteOpenHelper(c, DATABASE_NAME, null, DATABASE_VERSION) {

        override fun onCreate(d: SQLiteDatabase) {

            d.execSQL(DATABASE_CREATE_MESSAGE_STREAM)
        }

        override fun onUpgrade(d: SQLiteDatabase, iOld: Int, iNew: Int) {

            // If table needs to be updated do it here and change DATABASE_VERSION below
            onCreate(d)
        }
    }

    companion object {

        const val KEY_ENTRY_UID = "_id"
        const val KEY_TAG = "tag"
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val KEY_IMAGE_URI = "image_uri"
        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_READ = "read_boolean"
        const val KEY_DIRECTION = "direction"

        private const val DATABASE_VERSION = 1 // To upgrade DB increase this number
        private const val DATABASE_NAME = "Local.db"
        private const val SQLITE_TABLE_MESSAGE_STREAM = "class_list"
        private const val DATABASE_CREATE_MESSAGE_STREAM = (
                "CREATE TABLE if not exists " + SQLITE_TABLE_MESSAGE_STREAM + " (" +
                        KEY_ENTRY_UID + " integer PRIMARY KEY autoincrement," +
                        KEY_TAG + "," + KEY_TITLE + "," + KEY_MESSAGE + "," +
                        KEY_IMAGE_URI + "," + KEY_TIMESTAMP + "," + KEY_READ + "," +
                        KEY_DIRECTION + "," + " UNIQUE (" + KEY_ENTRY_UID + "));")
    }
}
