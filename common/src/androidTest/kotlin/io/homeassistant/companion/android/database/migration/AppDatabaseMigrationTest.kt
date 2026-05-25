package io.homeassistant.companion.android.database.migration

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.homeassistant.companion.android.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
                assertEquals("sensors table should exist at version 24", 1, cursor.count)
            }
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='Authentication_List'").use { cursor ->
                assertEquals("Authentication_List table should exist at version 24", 1, cursor.count)
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
                assertTrue("active notification setting should exist", cursor.moveToFirst())
                assertEquals(
                    "active notification content attributes should default to disabled after migration",
                    "false",
                    cursor.getString(0),
                )
            }
            db.query(
                """
                SELECT value FROM sensor_settings
                WHERE sensor_id = 'other_sensor'
                    AND name = 'active_notification_count_content_attrs'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue("unrelated sensor setting should exist", cursor.moveToFirst())
                assertEquals("migration should not change unrelated sensor settings", "true", cursor.getString(0))
            }
            db.query(
                """
                SELECT COUNT(*) FROM sensor_attributes
                WHERE sensor_id = 'active_notification_count'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue("active notification attribute count should be readable", cursor.moveToFirst())
                assertEquals(
                    "migration should remove previously cached notification content attributes",
                    0,
                    cursor.getInt(0),
                )
            }
            db.query(
                """
                SELECT value FROM sensor_attributes
                WHERE sensor_id = 'other_sensor'
                    AND name = 'kept'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue("unrelated sensor attribute should exist", cursor.moveToFirst())
                assertEquals("migration should not remove unrelated sensor attributes", "value", cursor.getString(0))
            }
            db.query(
                """
                SELECT last_sent_state, last_sent_icon FROM sensors
                WHERE id = 'active_notification_count'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue("active notification sensor should exist", cursor.moveToFirst())
                assertNull("migration should force the state to resend", cursor.getString(0))
                assertNull("migration should force the icon to resend", cursor.getString(1))
            }
        } finally {
            database.close()
        }
    }
}
