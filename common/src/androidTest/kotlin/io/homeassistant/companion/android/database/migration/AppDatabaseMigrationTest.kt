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
}
