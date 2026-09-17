package io.homeassistant.companion.android.database.migration

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.datastore.SessionDatastore
import java.io.File
import javax.inject.Provider
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // A file per test: DataStore allows only one instance per file, and JUnit builds a fresh
    // instance of this class for every test method.
    private val sessionFile = File.createTempFile("session-", ".datastore", context.cacheDir)
    private val sessionDatastore = SessionDatastore(context) { sessionFile }
    private val sessionDatastoreProvider = Provider { sessionDatastore }

    @After
    fun deleteSessionFile() {
        sessionFile.delete()
    }

    /** Inserts a v53 server row, with tokens unless [withSession] is false. */
    private fun insertServerAtV53(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        id: Int,
        installId: String,
        withSession: Boolean = true,
    ) {
        val session = if (withSession) {
            "'access-$id', 'refresh-$id', 1789634869, 'Bearer'"
        } else {
            "NULL, NULL, NULL, NULL"
        }
        db.execSQL(
            """
            INSERT INTO servers (
                id, _name, name_override, _version, device_registry_id, list_order, device_name,
                external_url, internal_url, cloud_url, webhook_id, secret, cloudhook_url,
                use_cloud, internal_ssids, internal_ethernet, internal_vpn, prioritize_internal,
                allow_insecure_connection,
                access_token, refresh_token, token_expiration, token_type, install_id,
                user_id, user_name, user_is_owner, user_is_admin
            ) VALUES (
                $id, 'Server $id', 'Override $id', '2026.10.0', 'reg-$id', -1, 'Pixel 6',
                'http://192.168.1.$id:8123', 'http://10.0.0.$id:8123', NULL, 'webhook-$id', NULL, NULL,
                0, '["HelloWorld"]', NULL, NULL, 1,
                1,
                $session, '$installId',
                'user-$id', 'timo', 1, 1
            )
            """.trimIndent(),
        )
    }

    @Test
    fun migrate53To54MovesTokensToTheDatastore() = runBlocking {
        helper.createDatabase(testDbName, 53).use { db -> insertServerAtV53(db, id = 1, installId = "install-1") }

        helper.runMigrationsAndValidate(testDbName, 54, true, Migration53to54(sessionDatastoreProvider))

        val session = sessionDatastore.getSession(1)
        assertEquals("access-1", session?.accessToken)
        assertEquals("refresh-1", session?.refreshToken)
        assertEquals("Bearer", session?.tokenType)
        assertEquals(Instant.fromEpochSeconds(1789634869), session?.tokenExpiration)
    }

    @Test
    fun migrate53To54KeepsInstallIdAndConfigurationInTheTable() = runBlocking {
        helper.createDatabase(testDbName, 53).use { db -> insertServerAtV53(db, id = 1, installId = "install-1") }

        val db = helper.runMigrationsAndValidate(testDbName, 54, true, Migration53to54(sessionDatastoreProvider))

        db.query(
            "SELECT install_id, webhook_id, name_override, external_url, internal_url, internal_ssids, device_name FROM servers",
        ).use {
            it.moveToFirst()
            assertEquals("install-1", it.getString(0))
            assertEquals("webhook-1", it.getString(1))
            assertEquals("Override 1", it.getString(2))
            assertEquals("http://192.168.1.1:8123", it.getString(3))
            assertEquals("http://10.0.0.1:8123", it.getString(4))
            assertEquals("[\"HelloWorld\"]", it.getString(5))
            assertEquals("Pixel 6", it.getString(6))
        }
    }

    @Test
    fun migrate53To54DropsTheTokenColumnsFromTheTable() = runBlocking {
        helper.createDatabase(testDbName, 53).use { db -> insertServerAtV53(db, id = 1, installId = "install-1") }

        val db = helper.runMigrationsAndValidate(testDbName, 54, true, Migration53to54(sessionDatastoreProvider))

        db.query("PRAGMA table_info(servers)").use { cursor ->
            val columns = buildList {
                while (cursor.moveToNext()) add(cursor.getString(1))
            }
            listOf("access_token", "refresh_token", "token_expiration", "token_type").forEach {
                assert(it !in columns) { "$it should no longer be a column of servers" }
            }
            assert("install_id" in columns) { "install_id should remain a column of servers" }
        }
    }

    @Test
    fun migrate53To54MovesEverySessionWhenSeveralServersExist() = runBlocking {
        helper.createDatabase(testDbName, 53).use { db ->
            insertServerAtV53(db, id = 1, installId = "install-1")
            insertServerAtV53(db, id = 2, installId = "install-2")
        }

        helper.runMigrationsAndValidate(testDbName, 54, true, Migration53to54(sessionDatastoreProvider))

        assertEquals("access-1", sessionDatastore.getSession(1)?.accessToken)
        assertEquals("access-2", sessionDatastore.getSession(2)?.accessToken)
    }

    @Test
    fun migrate53To54SkipsServersWithoutACompleteSession() = runBlocking {
        helper.createDatabase(testDbName, 53).use { db ->
            insertServerAtV53(db, id = 1, installId = "install-1", withSession = false)
        }

        helper.runMigrationsAndValidate(testDbName, 54, true, Migration53to54(sessionDatastoreProvider))

        assertNull(sessionDatastore.getSession(1))
    }

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
        ).addMigrations(*migrationPath(context, sessionDatastoreProvider)).build()

        try {
            database.openHelper.writableDatabase
            // If we get here without exception, all migrations from v24 to current succeeded
        } finally {
            database.close()
        }
    }
}
