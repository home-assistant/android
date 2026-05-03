package io.homeassistant.companion.android.database.migration

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.homeassistant.companion.android.database.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration tests for AppDatabase.
 *
 * These tests verify that database migrations correctly transform the schema
 * and preserve existing data.
 *
 * @see <a href="https://developer.android.com/training/data-storage/room/migrating-db-versions">Room Migration Testing</a>
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val testDbName = "migration-test"
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    /**
     * Tests the full migration path from the earliest available schema (v24) to the latest.
     *
     * Note: Schema files for versions 1-23 were not exported when those versions were developed.
     * Some tables (like Authentication_List) were added as Room entities without migrations,
     * making it impossible to reconstruct earlier schemas. The earliest testable version is 24.
     */
    @Test
    fun migrateFromVersion24ToLatest() {
        // Create database at version 24 - the earliest version with an exported schema
        helper.createDatabase(testDbName, 24).use { db ->
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='sensors'").use { cursor ->
                assert(cursor.count == 1) { "sensors table should exist at version 24" }
            }
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='Authentication_List'").use { cursor ->
                assert(cursor.count == 1) { "Authentication_List table should exist at version 24" }
            }
        }

        val database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            testDbName,
        ).addMigrations(*migrationPath(context)).build()

        try {
            database.openHelper.writableDatabase
            // If we get here without exception, all migrations from v24 to current succeeded
        } finally {
            database.close()
        }
    }

    @Test
    fun migrateFromVersion51DisablesActiveNotificationContentAttributes() {
        helper.createDatabase(testDbName, 51).use { db ->
            db.execSQL(
                """
                INSERT INTO sensor_settings(sensor_id, name, value, value_type, enabled, entries)
                VALUES('active_notification_count', 'active_notification_count_content_attrs', 'true', 'toggle', 1, '')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO sensor_settings(sensor_id, name, value, value_type, enabled, entries)
                VALUES('other_sensor', 'active_notification_count_content_attrs', 'true', 'toggle', 1, '')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO sensors(
                    id,
                    server_id,
                    enabled,
                    registered,
                    state,
                    last_sent_state,
                    last_sent_icon,
                    state_type,
                    type,
                    icon,
                    name
                )
                VALUES(
                    'active_notification_count',
                    0,
                    1,
                    1,
                    '2',
                    '2',
                    'mdi:bell',
                    'int',
                    'sensor',
                    'mdi:bell',
                    'Active notification count'
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO sensor_attributes(sensor_id, name, value, value_type)
                VALUES('active_notification_count', 'com.example_1_android.text', 'Secret notification', 'string')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO sensor_attributes(sensor_id, name, value, value_type)
                VALUES('other_sensor', 'kept', 'value', 'string')
                """.trimIndent(),
            )
        }

        val database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            testDbName,
        ).addMigrations(*migrationPath(context)).build()

        try {
            val db = database.openHelper.writableDatabase
            db.query(
                """
                SELECT value FROM sensor_settings
                WHERE sensor_id = 'active_notification_count'
                    AND name = 'active_notification_count_content_attrs'
                """.trimIndent(),
            ).use { cursor ->
                assert(cursor.moveToFirst()) { "active notification setting should exist" }
                assert(cursor.getString(0) == "false") {
                    "active notification content attributes should default to disabled after migration"
                }
            }
            db.query(
                """
                SELECT value FROM sensor_settings
                WHERE sensor_id = 'other_sensor'
                    AND name = 'active_notification_count_content_attrs'
                """.trimIndent(),
            ).use { cursor ->
                assert(cursor.moveToFirst()) { "unrelated sensor setting should exist" }
                assert(cursor.getString(0) == "true") {
                    "migration should not change unrelated sensor settings"
                }
            }
            db.query(
                """
                SELECT COUNT(*) FROM sensor_attributes
                WHERE sensor_id = 'active_notification_count'
                """.trimIndent(),
            ).use { cursor ->
                assert(cursor.moveToFirst()) { "active notification attribute count should be readable" }
                assert(cursor.getInt(0) == 0) {
                    "migration should remove previously cached notification content attributes"
                }
            }
            db.query(
                """
                SELECT value FROM sensor_attributes
                WHERE sensor_id = 'other_sensor'
                    AND name = 'kept'
                """.trimIndent(),
            ).use { cursor ->
                assert(cursor.moveToFirst()) { "unrelated sensor attribute should exist" }
                assert(cursor.getString(0) == "value") {
                    "migration should not remove unrelated sensor attributes"
                }
            }
            db.query(
                """
                SELECT last_sent_state, last_sent_icon FROM sensors
                WHERE id = 'active_notification_count'
                """.trimIndent(),
            ).use { cursor ->
                assert(cursor.moveToFirst()) { "active notification sensor should exist" }
                assert(cursor.isNull(0)) { "migration should force the state to resend" }
                assert(cursor.isNull(1)) { "migration should force the icon to resend" }
            }
        } finally {
            database.close()
        }
    }
}
