package io.homeassistant.companion.android.database.migration

import android.content.Context
import androidx.room3.migration.Migration
import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.DATABASE_VERSION
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration tests for [AppDatabase].
 *
 * These tests verify that database migrations correctly transform the schema
 * and preserve existing data.
 *
 * @see <a href="https://developer.android.com/training/data-storage/room/migrating-db-versions">Room Migration Testing</a>
 */

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = context.getDatabasePath("migration-test"),
        driver = AndroidSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )

    private val directDbFile: File = context.getDatabasePath("migration-direct-test")

    @After
    fun deleteDirectDb() {
        listOf("", "-wal", "-shm", "-journal").forEach { File(directDbFile.path + it).delete() }
    }

    /** Opens a fresh driver connection on a real (non-helper) database file. */
    private fun openDirectDb(): SQLiteConnection {
        listOf("", "-wal", "-shm", "-journal").forEach { File(directDbFile.path + it).delete() }
        directDbFile.parentFile?.mkdirs()
        return AndroidSQLiteDriver().open(directDbFile.path)
    }

    private fun migration(startVersion: Int): Migration =
        migrationPath(context).first { it.startVersion == startVersion }

    @Test
    fun migrateFromVersion24ToLatest() = runTest {
        helper.createDatabase(24).close()
        helper.runMigrationsAndValidate(DATABASE_VERSION, migrationPath(context).toList()).close()
    }

    @Test
    fun migration5to6_restructuresStaticWidgetAndKeepsData() = runTest {
        val connection = openDirectDb()
        connection.use { connection ->
            connection.execSQL(
                "CREATE TABLE `static_widget` (`id` INTEGER NOT NULL, `entity_id` TEXT NOT NULL, " +
                    "`attribute_id` TEXT, `label` TEXT, `text_size` FLOAT NOT NULL DEFAULT '30', " +
                    "`separator` TEXT NOT NULL DEFAULT ' ', PRIMARY KEY(`id`))",
            )
            connection.execSQL(
                "INSERT INTO `static_widget` (`id`,`entity_id`,`attribute_id`,`label`,`text_size`,`separator`) " +
                    "VALUES (1,'light.kitchen','brightness','Kitchen',24.0,' - ')",
            )

            migration(5).migrate(connection)

            connection.prepare(
                "SELECT `entity_id`,`attribute_ids`,`label`,`state_separator`,`attribute_separator` " +
                    "FROM `static_widget` WHERE `id` = 1",
            ).use {
                assertTrue(it.step())
                assertEquals("light.kitchen", it.getText(0))
                assertEquals("brightness", it.getText(1)) // attribute_id copied into attribute_ids
                assertEquals("Kitchen", it.getText(2))
                assertEquals(" - ", it.getText(3)) // separator copied into state_separator
                assertEquals(" ", it.getText(4)) // attribute_separator defaulted
            }
        }
    }

    @Test
    fun migration16to17_renamesSettingsAndPreservesFirstRowSkip() = runTest {
        val connection = openDirectDb()
        connection.use { connection ->
            connection.execSQL(
                "CREATE TABLE `sensor_settings` (`sensor_id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`value` TEXT NOT NULL, `value_type` TEXT NOT NULL DEFAULT 'string', " +
                    "`enabled` INTEGER NOT NULL DEFAULT '1', PRIMARY KEY(`sensor_id`, `name`))",
            )
            // First row is intentionally skipped by the original algorithm (moveToFirst + moveToNext);
            // the rewrite preserves that, so this row must NOT appear after migration.
            connection.execSQL(
                "INSERT INTO `sensor_settings` VALUES ('next_alarm','Allow List','a','string',1)",
            )
            // Second row is migrated and its name is renamed.
            connection.execSQL(
                "INSERT INTO `sensor_settings` VALUES ('geocoded_location','Minimum Accuracy','50','string',1)",
            )

            migration(16).migrate(connection)

            val names = buildList {
                connection.prepare("SELECT `sensor_id`,`name` FROM `sensor_settings`").use {
                    while (it.step()) add(it.getText(0) to it.getText(1))
                }
            }
            assertEquals(listOf("geocoded_location" to "geocode_minimum_accuracy"), names)
        }
    }

    // Note: Migration40to41 maps stored icon ids to MDI names via `mdi_id_map.json`, which is an
    // :app asset not present in the :common instrumentation context, so its icon-mapping-with-data
    // path cannot be exercised here. The migration still runs (with empty widget/tile tables) as
    // part of [migrateFromVersion24ToLatest], which validates its structure end to end.
}
