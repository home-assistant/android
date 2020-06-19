package io.homeassistant.companion.android.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.lang.Exception

private const val DATABASE_VERSION = 1
private const val DATABASE_NAME = "HOME_ASSISTANT.db"
private const val TABLE_NAME = "Authentication_List"
private const val COLUMN_HOST = "host"
private const val COLUMN_USERNAME = "username"
private const val COLUMN_PASSWORD = "password"

class DataBaseHandler(context: Context, factory: SQLiteDatabase.CursorFactory?) :
    SQLiteOpenHelper(context, DATABASE_NAME, factory, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db?.execSQL(
            "CREATE TABLE $TABLE_NAME " +
                    "($COLUMN_HOST TEXT, $COLUMN_USERNAME TEXT, $COLUMN_PASSWORD TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun addAuth(auth: Authentication) {
        val database = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(COLUMN_HOST, auth.host)
        contentValues.put(COLUMN_USERNAME, auth.username)
        contentValues.put(COLUMN_PASSWORD, auth.password)
        database.insert(TABLE_NAME, null, contentValues)
        database.close()
    }

    fun getAuth(host: String, context: Context): Authentication {
        val auth: Authentication = Authentication()
        val database = this.readableDatabase
        try {
            val result = database.rawQuery("Select * from $TABLE_NAME where $COLUMN_HOST = ?", arrayOf(host))
            result.moveToFirst()
            auth.host = result.getString(result.getColumnIndex(COLUMN_HOST))
            auth.username = result.getString(result.getColumnIndex(COLUMN_USERNAME))
            auth.password = result.getString(result.getColumnIndex(COLUMN_PASSWORD))
            } catch (e: Exception) {
            Log.e("SQL DATABASE", "getAuth: '$host' not in '$COLUMN_HOST' column")
        }
        database.close()
        return auth
    }

    fun removeAuth(host: String) {
        val database = this.writableDatabase
        try {
        database.delete(TABLE_NAME, "$COLUMN_HOST = ?", arrayOf(host))
        } catch (e: Exception) {
            Log.e("SQL DATABASE", "removeAuth: '$host' don't exist")
        }
        database.close()
    }
}

class Authentication {
    var host: String? = null
    var username: String? = null
    var password: String? = null

    constructor(host: String, userName: String, password: String) {
        this.host = host
        this.username = userName
        this.password = password
    }

    constructor() {
    }

    fun isNotEmpty(): Boolean {
        var isNotEmpty = false
        if (!this.host.isNullOrEmpty() && !this.username.isNullOrEmpty() && !this.password.isNullOrEmpty())
            isNotEmpty = true
        return isNotEmpty
    }
}
