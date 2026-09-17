# Server Session Extraction and Re-registration Implementation Plan

> **For agentic workers:** Steps use checkbox (`- [ ]`) syntax for tracking. Execute task-by-task, running the verification command in each step before moving on.

**Goal:** Stop Android backup/restore from silently deleting the user's servers, by moving session secrets into storage that is never backed up and replacing the destructive `cleanupServers()` with a prefilled re-registration flow.

**Architecture:** Session secrets (`access_token`, `refresh_token`, `token_expiration`, `token_type`, `install_id`) move out of the backed-up `servers` Room table into a per-server encrypted DataStore under `files/datastore/`, which the backup rules do not cover. A restored install then has full server *configuration* (URL, name, SSIDs, cloud settings) and *no* session, which is an unambiguous signal. The launch path stops deleting those servers and instead offers a re-registration flow with every previously chosen value prefilled.

**Tech Stack:** Kotlin, Room (schema v54 → v55), androidx DataStore Preferences, AndroidKeyStore AES-256-GCM, Hilt, Jetpack Compose Navigation, JUnit 5 + Robolectric + Turbine.

**Spec:** This document. The behaviour it fixes was traced from a logcat capture on 2026-09-17; see "Background" below.

---

## Background: the bug being fixed

Verified from logs and source on 2026-09-17:

1. `backup_rules.xml` / `backup_rules_android12.xml` back up `domain="database"` (which holds the `servers` table including tokens and `install_id`) but exclude `sharedpref/session_0.xml`.
2. The app-wide install id lives in exactly that excluded file (`DataModule.kt:148-158`, `getSharedPreferencesSuspend("session_0")`), so after a restore the app mints a fresh UUID.
3. `AuthenticationRepositoryImpl.kt:89-99` returns `SessionState.ANONYMOUS` whenever `server.session.installId != installId`.
4. `ServerManagerImpl.kt:96-106` therefore reports `isRegistered() == false`, so the user lands on the login screen even though the server row is present.
5. `LaunchViewModel.kt:292-305` (`cleanupServers()`, called from init at line 151) deletes **every** server whose state is `ANONYMOUS`. The restored server is destroyed during the *first* launch; the second launch merely observes an empty database.

## Global Constraints

- Kotlin only. All code and comments in English.
- All displayed strings go in `:common` value files, English only, accessed via `stringResource`.
- Named constants instead of magic numbers or strings; sealed interfaces instead of strings/enums for logic control.
- Immutable data classes; changes via `copy()`.
- Functions under 50 lines. Default to `private`/`internal` visibility.
- Never use `System.currentTimeMillis`; inject `kotlin.time.Clock` via Hilt.
- No `org.json`; use Kotlinx.serialization.
- Dispatchers are not injected via DI. Use a `@VisibleForTesting` constructor taking the dispatcher plus an `@Inject` delegating constructor defaulting to `Dispatchers.Default` / `Dispatchers.IO`.
- Run before every commit: `./gradlew ktlintFormat` then `./gradlew detektMain --continue` and the module's tests.
- Update `./gradlew alldependencies --write-locks` after any dependency change.
- Do not run `git commit`. Each task ends by listing what to stage; the repository owner commits.

## Key simplification discovered during research

`full-backup-content` with explicit `<include>` elements backs up **only** the included domains. The current rules include `database` and `sharedpref` only, so anything under `files/` is *already* outside the backup. A DataStore at `files/datastore/` is therefore excluded by construction. Task 1.5 still adds an explicit `<exclude>` so the intent survives a future rules edit, but the security of the move does not depend on it.

## Sequencing

Three independently mergeable PRs. The app works at every point.

- **PR 1 (Tasks 1.1 - 1.6):** move session secrets to the encrypted store. No user-visible change.
- **PR 2 (Tasks 2.1 - 2.3):** stop deleting restored servers. User-visible change: server survives, app shows it as needing re-connection.
- **PR 3 (Tasks 3.1 - 3.4):** prefilled, multi-server re-registration flow.

---

# PR 1: Move session secrets out of the backed-up database

> **STATUS: IMPLEMENTED, and it diverged from the tasks below.** Read this box, not Tasks 1.1-1.5,
> for what the code does. The tasks are kept for the reasoning, not as instructions.
>
> | Planned | Built |
> |---|---|
> | Hand-rolled AES-GCM `SessionSecretsSerializer` | `androidx.datastore:datastore-tink` `AeadSerializer` over `AndroidKeysetManager` |
> | One encrypted file per server | One `ServerSessions` map keyed by server id, in `session.datastore` |
> | Schema 54 → 55 | **53 → 54**; the database was on 53, not 54 |
> | `installId` moves to the store with the tokens | `installId` stays on the `servers` row as a flat column, next to `webhook_id` |
> | Migration builds its own store | Migration takes `Provider<SessionDatastore>` from Hilt, via `DatabaseModule` |
>
> Two things the implementation had to discover:
>
> - **Task 1.3's instantiation bug was real.** Building a second `SessionDatastore` throws
>   `IllegalStateException: There are multiple DataStores active for the same file`. It surfaced in
>   the migration tests, because JUnit builds a fresh test instance per method. `SessionDatastore`
>   now has a `@VisibleForTesting` constructor taking `produceFile` so tests point at their own file.
> - **`kotlin.time.Clock` had no binding in `:common`.** It was provided in `:app` and `:wear`
>   separately. The provider moved to `common/di/DataModule.kt` and both duplicates were removed.
>
> **Correction on Keystore invalidation.** Earlier notes in this plan and in discussion claimed a
> screen-lock change can invalidate the master key and lose every session. That is wrong.
> `AndroidKeystore.generateNewAes256GcmKey` builds the key with no
> `setUserAuthenticationRequired(true)` and no `setInvalidatedByBiometricEnrollment`, so Keystore
> does not invalidate it when the lock screen or biometrics change. The realistic ways to lose it
> are a restore onto another device, a wipe, or Keystore corruption.
>
> **Still open:** `LongParameterList` on `AuthenticationRepositoryImpl` (7 parameters, limit 6).
> Dropping the now-unused `Server.installId` fixes it for free; keeping it needs
> `sessionDatastore` + `clock` folded into one injectable. See the note under Task 2.1.

### Task 1.1: Add DataStore dependency and the encrypted serializer

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `common/build.gradle.kts`
- Create: `common/src/main/kotlin/io/homeassistant/companion/android/common/data/servers/session/SessionSecrets.kt`
- Create: `common/src/main/kotlin/io/homeassistant/companion/android/common/data/servers/session/SessionSecretsSerializer.kt`
- Test: `common/src/test/kotlin/io/homeassistant/companion/android/common/data/servers/session/SessionSecretsSerializerTest.kt`

**Interfaces:**
- Produces: `SessionSecrets` data class; `SessionSecretsSerializer : Serializer<SessionSecrets>`.

- [ ] **Step 1: Add the dependency**

In `gradle/libs.versions.toml`, under `[versions]`:

```toml
datastore = "1.1.7"
```

Under `[libraries]`:

```toml
androidx-datastore = { module = "androidx.datastore:datastore", version.ref = "datastore" }
```

In `common/build.gradle.kts`, in `dependencies`:

```kotlin
implementation(libs.androidx.datastore)
```

- [ ] **Step 2: Refresh dependency locks**

Run: `./gradlew alldependencies --write-locks`
Expected: lock files under `gradle/dependency-locks/` updated, build succeeds.

- [ ] **Step 3: Write the failing serializer test**

`common/src/test/kotlin/io/homeassistant/companion/android/common/data/servers/session/SessionSecretsSerializerTest.kt`:

```kotlin
package io.homeassistant.companion.android.common.data.servers.session

import dagger.hilt.android.testing.HiltTestApplication
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SessionSecretsSerializerTest {

    private val serializer = SessionSecretsSerializer()

    private val secrets = SessionSecrets(
        accessToken = "access",
        refreshToken = "refresh",
        tokenExpiration = 1789634869L,
        tokenType = "Bearer",
        installId = "79c2a568-d86b-401e-a211-ed3a995043a3",
    )

    @Test
    fun `Given secrets when written and read back then the value round-trips`() = runTest {
        val out = ByteArrayOutputStream()

        serializer.writeTo(secrets, out)
        val restored = serializer.readFrom(ByteArrayInputStream(out.toByteArray()))

        assertEquals(secrets, restored)
    }

    @Test
    fun `Given secrets when written then the tokens are not readable in the raw bytes`() = runTest {
        val out = ByteArrayOutputStream()

        serializer.writeTo(secrets, out)

        assertNotEquals(-1, out.toByteArray().size)
        assert(!String(out.toByteArray(), Charsets.ISO_8859_1).contains("refresh"))
    }

    @Test
    fun `Given an empty stream when reading then the default value is returned`() = runTest {
        val restored = serializer.readFrom(ByteArrayInputStream(ByteArray(0)))

        assertEquals(SessionSecrets(), restored)
    }

    @Test
    fun `Given corrupted bytes when reading then the default value is returned`() = runTest {
        val restored = serializer.readFrom(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)))

        assertEquals(SessionSecrets(), restored)
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew :common:testDebugUnitTest --tests "*SessionSecretsSerializerTest*"`
Expected: FAIL, unresolved reference `SessionSecrets`.

- [ ] **Step 5: Write `SessionSecrets`**

```kotlin
package io.homeassistant.companion.android.common.data.servers.session

import kotlinx.serialization.Serializable

/**
 * Authentication secrets for one server. Stored outside the database so that they are never part
 * of an Android backup: a restored install must re-register rather than reuse another install's
 * tokens and webhook.
 */
@Serializable
data class SessionSecrets(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenExpiration: Long? = null,
    val tokenType: String? = null,
    val installId: String? = null,
) {
    /** `true` when every field needed to authenticate against Home Assistant is present. */
    fun isComplete() = accessToken != null &&
        refreshToken != null &&
        tokenExpiration != null &&
        tokenType != null &&
        installId != null
}
```

- [ ] **Step 6: Write `SessionSecretsSerializer`**

```kotlin
package io.homeassistant.companion.android.common.data.servers.session

import androidx.datastore.core.Serializer
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.encodeToString
import timber.log.Timber

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS = "ha_session_secrets"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val IV_LENGTH_BYTES = 12
private const val TAG_LENGTH_BITS = 128

/**
 * Reads and writes [SessionSecrets] as JSON encrypted with an AndroidKeyStore AES-256-GCM key.
 *
 * The key never leaves the device, so even a file copied off the device is useless. Any failure to
 * read yields [SessionSecrets] defaults rather than throwing, so a missing or unreadable store is
 * treated as "no session" and drives re-registration instead of crashing the app.
 */
internal class SessionSecretsSerializer : Serializer<SessionSecrets> {

    override val defaultValue = SessionSecrets()

    override suspend fun readFrom(input: InputStream): SessionSecrets {
        val bytes = input.readBytes()
        if (bytes.size <= IV_LENGTH_BYTES) return defaultValue

        return try {
            val iv = bytes.copyOfRange(0, IV_LENGTH_BYTES)
            val payload = bytes.copyOfRange(IV_LENGTH_BYTES, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            }
            kotlinJsonMapper.decodeFromString<SessionSecrets>(String(cipher.doFinal(payload)))
        } catch (e: Exception) {
            Timber.w(e, "Unable to read session secrets, treating the session as absent")
            defaultValue
        }
    }

    override suspend fun writeTo(t: SessionSecrets, output: OutputStream) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        output.write(cipher.iv)
        output.write(cipher.doFinal(kotlinJsonMapper.encodeToString(t).toByteArray()))
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./gradlew :common:testDebugUnitTest --tests "*SessionSecretsSerializerTest*"`
Expected: PASS, 4 tests.

- [ ] **Step 8: Format, lint, stage**

Run: `./gradlew ktlintFormat && ./gradlew :common:detektMain --continue`

Stage: `gradle/libs.versions.toml`, `gradle/dependency-locks/`, `common/build.gradle.kts`, the two new source files, the new test file.

---

### Task 1.2: The `ServerSessionStore`

**Files:**
- Create: `common/src/main/kotlin/io/homeassistant/companion/android/common/data/servers/session/ServerSessionStore.kt`
- Modify: `common/src/main/kotlin/io/homeassistant/companion/android/di/DataModule.kt`
- Test: `common/src/test/kotlin/io/homeassistant/companion/android/common/data/servers/session/ServerSessionStoreTest.kt`

**Interfaces:**
- Consumes: `SessionSecrets`, `SessionSecretsSerializer` from Task 1.1.
- Produces:
  - `ServerSessionStore.get(serverId: Int): SessionSecrets?` — `null` when there is no stored session for that server.
  - `ServerSessionStore.set(serverId: Int, secrets: SessionSecrets)`
  - `ServerSessionStore.remove(serverId: Int)`
  - `ServerSessionStore.serverIdsWithSession(): Set<Int>`

- [ ] **Step 1: Write the failing test**

`common/src/test/kotlin/io/homeassistant/companion/android/common/data/servers/session/ServerSessionStoreTest.kt`:

```kotlin
package io.homeassistant.companion.android.common.data.servers.session

import androidx.test.core.app.ApplicationProvider
import android.app.Application
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ServerSessionStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = ServerSessionStore(context)

    private val secrets = SessionSecrets(
        accessToken = "access",
        refreshToken = "refresh",
        tokenExpiration = 1789634869L,
        tokenType = "Bearer",
        installId = "install-1",
    )

    @After
    fun tearDown() {
        context.filesDir.resolve("datastore").deleteRecursively()
    }

    @Test
    fun `Given no stored session when getting then null is returned`() = runTest {
        assertNull(store.get(serverId = 1))
    }

    @Test
    fun `Given a stored session when getting then the secrets are returned`() = runTest {
        store.set(serverId = 1, secrets = secrets)

        assertEquals(secrets, store.get(serverId = 1))
    }

    @Test
    fun `Given sessions for two servers when getting then each server sees only its own`() = runTest {
        store.set(serverId = 1, secrets = secrets)
        store.set(serverId = 2, secrets = secrets.copy(accessToken = "other"))

        assertEquals("access", store.get(serverId = 1)?.accessToken)
        assertEquals("other", store.get(serverId = 2)?.accessToken)
    }

    @Test
    fun `Given a stored session when removed then null is returned`() = runTest {
        store.set(serverId = 1, secrets = secrets)

        store.remove(serverId = 1)

        assertNull(store.get(serverId = 1))
    }

    @Test
    fun `Given sessions for two servers when listing ids then both are returned`() = runTest {
        store.set(serverId = 1, secrets = secrets)
        store.set(serverId = 2, secrets = secrets)

        assertEquals(setOf(1, 2), store.serverIdsWithSession())
    }

    @Test
    fun `Given an incomplete session when getting then null is returned`() = runTest {
        store.set(serverId = 1, secrets = SessionSecrets(accessToken = "only-access"))

        assertNull(store.get(serverId = 1))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :common:testDebugUnitTest --tests "*ServerSessionStoreTest*"`
Expected: FAIL, unresolved reference `ServerSessionStore`.

- [ ] **Step 3: Write `ServerSessionStore`**

```kotlin
package io.homeassistant.companion.android.common.data.servers.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@VisibleForTesting
internal const val SESSION_DIRECTORY = "datastore"

private const val FILE_PREFIX = "server_session_"
private const val FILE_SUFFIX = ".pb"

/**
 * Per-server authentication secrets, held in `files/datastore/` which the backup rules do not
 * cover. A restored install therefore finds no session and must re-register, instead of reusing
 * the tokens and webhook of the install the backup came from.
 */
@Singleton
class ServerSessionStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val stores = mutableMapOf<Int, DataStore<SessionSecrets>>()
    private val mutex = Mutex()

    /** Secrets for [serverId], or `null` when there is no usable session. */
    suspend fun get(serverId: Int): SessionSecrets? =
        store(serverId).data.first().takeIf { it.isComplete() }

    /** Replaces the stored secrets for [serverId]. */
    suspend fun set(serverId: Int, secrets: SessionSecrets) {
        store(serverId).updateData { secrets }
    }

    /** Clears the stored secrets for [serverId] and deletes its backing file. */
    suspend fun remove(serverId: Int) {
        mutex.withLock {
            stores.remove(serverId)
        }
        file(serverId).delete()
    }

    /** Ids of every server that currently has a usable session. */
    suspend fun serverIdsWithSession(): Set<Int> = directory().listFiles()
        .orEmpty()
        .mapNotNull { it.name.removeSurrounding(FILE_PREFIX, FILE_SUFFIX).toIntOrNull() }
        .filter { get(it) != null }
        .toSet()

    private suspend fun store(serverId: Int): DataStore<SessionSecrets> = mutex.withLock {
        stores.getOrPut(serverId) {
            DataStoreFactory.create(serializer = SessionSecretsSerializer()) { file(serverId) }
        }
    }

    private fun directory(): File = File(context.filesDir, SESSION_DIRECTORY).apply { mkdirs() }

    private fun file(serverId: Int) = File(directory(), "$FILE_PREFIX$serverId$FILE_SUFFIX")
}
```

Add the import `androidx.annotation.VisibleForTesting`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :common:testDebugUnitTest --tests "*ServerSessionStoreTest*"`
Expected: PASS, 6 tests.

- [ ] **Step 5: Format, lint, stage**

Run: `./gradlew ktlintFormat && ./gradlew :common:detektMain --continue`

Stage: the new source and test file.

---

### Task 1.3: Room migration 54 → 55

**Files:**
- Modify: `common/src/main/kotlin/io/homeassistant/companion/android/database/AppDatabase.kt:81` (version 54 → 55)
- Modify: `common/src/main/kotlin/io/homeassistant/companion/android/database/migration/DatabaseMigration.kt` (add `Migration54to55(context)` to `migrationPath`)
- Modify: `common/src/main/kotlin/io/homeassistant/companion/android/database/server/Server.kt` (drop `@Embedded val session`)
- Modify: `common/src/main/kotlin/io/homeassistant/companion/android/database/server/ServerSessionInfo.kt` (delete the file)
- Create: `common/schemas/io.homeassistant.companion.android.database.AppDatabase/55.json` (generated)
- Test: `common/src/test/kotlin/io/homeassistant/companion/android/database/migration/Migration54to55Test.kt`

**Interfaces:**
- Consumes: `ServerSessionStore`, `SessionSecrets` from Tasks 1.1-1.2.
- Produces: `servers` table without the columns `access_token`, `refresh_token`, `token_expiration`, `token_type`, `install_id`. `Server` no longer has a `session` property.

- [ ] **Step 1: Write the failing migration test**

`common/src/test/kotlin/io/homeassistant/companion/android/database/migration/Migration54to55Test.kt`:

```kotlin
package io.homeassistant.companion.android.database.migration

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.servers.session.ServerSessionStore
import io.homeassistant.companion.android.database.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TEST_DB = "migration-test.db"

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class Migration54to55Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = ServerSessionStore(context)

    @After
    fun tearDown() {
        context.filesDir.resolve("datastore").deleteRecursively()
    }

    @Test
    fun `Given a server with a session when migrating then the secrets move to the store`() = runTest {
        helper.createDatabase(TEST_DB, 54).use { db ->
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
                    1, 'Home', NULL, '2026.10.0', 'reg-1', -1, 'Pixel 6',
                    'http://192.168.1.10:8123', NULL, NULL, 'webhook-1', NULL, NULL,
                    0, '[]', NULL, NULL, 0,
                    1,
                    'access', 'refresh', 1789634869, 'Bearer', 'install-1',
                    'user-1', 'timo', 1, 1
                )
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 55, true, Migration54to55(context))

        val secrets = store.get(serverId = 1)
        assertEquals("access", secrets?.accessToken)
        assertEquals("refresh", secrets?.refreshToken)
        assertEquals(1789634869L, secrets?.tokenExpiration)
        assertEquals("Bearer", secrets?.tokenType)
        assertEquals("install-1", secrets?.installId)
    }

    @Test
    fun `Given a server with a session when migrating then the configuration is preserved`() = runTest {
        helper.createDatabase(TEST_DB, 54).use { db ->
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
                    1, 'Home', 'My House', '2026.10.0', 'reg-1', -1, 'Pixel 6',
                    'http://192.168.1.10:8123', 'http://10.0.0.2:8123', NULL, 'webhook-1', NULL, NULL,
                    0, '["HelloWorld"]', NULL, NULL, 1,
                    1,
                    'access', 'refresh', 1789634869, 'Bearer', 'install-1',
                    'user-1', 'timo', 1, 1
                )
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 55, true, Migration54to55(context))

        db.query("SELECT _name, name_override, external_url, internal_url, internal_ssids, prioritize_internal, device_name FROM servers").use {
            it.moveToFirst()
            assertEquals("Home", it.getString(0))
            assertEquals("My House", it.getString(1))
            assertEquals("http://192.168.1.10:8123", it.getString(2))
            assertEquals("http://10.0.0.2:8123", it.getString(3))
            assertEquals("[\"HelloWorld\"]", it.getString(4))
            assertEquals(1, it.getInt(5))
            assertEquals("Pixel 6", it.getString(6))
        }
    }

    @Test
    fun `Given a server with no session when migrating then the store stays empty`() = runTest {
        helper.createDatabase(TEST_DB, 54).use { db ->
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
                    1, 'Home', NULL, NULL, NULL, -1, NULL,
                    'http://192.168.1.10:8123', NULL, NULL, NULL, NULL, NULL,
                    0, '[]', NULL, NULL, 0,
                    1,
                    NULL, NULL, NULL, NULL, NULL,
                    NULL, NULL, 0, 0
                )
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 55, true, Migration54to55(context))

        assertNull(store.get(serverId = 1))
    }

    @Test
    fun `Given two servers with sessions when migrating then both move to the store`() = runTest {
        helper.createDatabase(TEST_DB, 54).use { db ->
            listOf(
                Triple(1, "http://192.168.1.10:8123", "install-1"),
                Triple(2, "http://192.168.1.11:8123", "install-2"),
            ).forEach { (id, url, installId) ->
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
                        $id, 'Server $id', NULL, NULL, NULL, -1, NULL,
                        '$url', NULL, NULL, 'webhook-$id', NULL, NULL,
                        0, '[]', NULL, NULL, 0,
                        1,
                        'access-$id', 'refresh-$id', 1789634869, 'Bearer', '$installId',
                        NULL, NULL, 0, 0
                    )
                    """.trimIndent(),
                )
            }
        }

        helper.runMigrationsAndValidate(TEST_DB, 55, true, Migration54to55(context))

        assertEquals("install-1", store.get(serverId = 1)?.installId)
        assertEquals("install-2", store.get(serverId = 2)?.installId)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :common:testDebugUnitTest --tests "*Migration54to55Test*"`
Expected: FAIL, unresolved reference `Migration54to55`.

- [ ] **Step 3: Write the migration**

Append to `common/src/main/kotlin/io/homeassistant/companion/android/database/migration/DatabaseMigration.kt`:

```kotlin
/**
 * Moves the session columns out of `servers` and into [ServerSessionStore].
 *
 * The `servers` table is part of the Android backup, so keeping tokens there let a restored
 * install adopt another install's registration. The store lives in `files/`, which the backup
 * rules do not cover, so a restored install sees a fully configured server with no session and
 * can offer re-registration instead of losing the server.
 */
internal class Migration54to55(private val context: Context) : Migration(54, 55) {

    override fun migrate(connection: SQLiteConnection) {
        if (connection is SupportSQLiteConnection) {
            copySessionsToStore(connection.db)
        }

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_servers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `_name` TEXT NOT NULL, `name_override` TEXT, `_version` TEXT, `device_registry_id` TEXT, `list_order` INTEGER NOT NULL, `device_name` TEXT, `external_url` TEXT NOT NULL, `internal_url` TEXT, `cloud_url` TEXT, `webhook_id` TEXT, `secret` TEXT, `cloudhook_url` TEXT, `use_cloud` INTEGER NOT NULL, `internal_ssids` TEXT NOT NULL, `internal_ethernet` INTEGER, `internal_vpn` INTEGER, `prioritize_internal` INTEGER NOT NULL, `allow_insecure_connection` INTEGER NOT NULL, `user_id` TEXT, `user_name` TEXT, `user_is_owner` INTEGER, `user_is_admin` INTEGER)",
        )
        connection.execSQL(
            "INSERT INTO `_new_servers` (`id`,`_name`,`name_override`,`_version`,`device_registry_id`,`list_order`,`device_name`,`external_url`,`internal_url`,`cloud_url`,`webhook_id`,`secret`,`cloudhook_url`,`use_cloud`,`internal_ssids`,`internal_ethernet`,`internal_vpn`,`prioritize_internal`,`allow_insecure_connection`,`user_id`,`user_name`,`user_is_owner`,`user_is_admin`) SELECT `id`,`_name`,`name_override`,`_version`,`device_registry_id`,`list_order`,`device_name`,`external_url`,`internal_url`,`cloud_url`,`webhook_id`,`secret`,`cloudhook_url`,`use_cloud`,`internal_ssids`,`internal_ethernet`,`internal_vpn`,`prioritize_internal`,`allow_insecure_connection`,`user_id`,`user_name`,`user_is_owner`,`user_is_admin` FROM `servers`",
        )
        connection.execSQL("DROP TABLE `servers`")
        connection.execSQL("ALTER TABLE `_new_servers` RENAME TO `servers`")
    }

    private fun copySessionsToStore(db: SupportSQLiteDatabase) {
        val store = ServerSessionStore(context)
        db.query(
            "SELECT `id`, `access_token`, `refresh_token`, `token_expiration`, `token_type`, `install_id` FROM `servers`",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val secrets = SessionSecrets(
                    accessToken = cursor.getStringOrNull(1),
                    refreshToken = cursor.getStringOrNull(2),
                    tokenExpiration = if (cursor.isNull(3)) null else cursor.getLong(3),
                    tokenType = cursor.getStringOrNull(4),
                    installId = cursor.getStringOrNull(5),
                )
                if (!secrets.isComplete()) continue
                runBlocking { store.set(serverId = cursor.getInt(0), secrets = secrets) }
            }
        }
    }
}
```

Add imports: `io.homeassistant.companion.android.common.data.servers.session.ServerSessionStore`, `io.homeassistant.companion.android.common.data.servers.session.SessionSecrets`, `kotlinx.coroutines.runBlocking`.

> The exact `CREATE TABLE` text must match what Room expects for schema 55. After Step 5 generates `55.json`, copy the `createSql` for `servers` from that file verbatim into the migration if it differs, then re-run the test.

- [ ] **Step 4: Register the migration and bump the version**

In `DatabaseMigration.kt`, add `Migration54to55(context),` as the last entry of `migrationPath`.

In `AppDatabase.kt:81`, change `version = 54` to `version = 55`.

- [ ] **Step 5: Remove the embedded session and generate the schema**

In `Server.kt`, delete the line `@Embedded val session: ServerSessionInfo,` and remove `session = temporaryServer.session,` from `fromTemporaryServer`. Delete `ServerSessionInfo.kt`.

`TemporaryServer` keeps a session for the onboarding hand-off, so change its type:

```kotlin
@Parcelize
data class TemporaryServer(
    val externalUrl: String,
    val session: SessionSecrets,
    val allowInsecureConnection: Boolean?,
) : Parcelable
```

Add `@Serializable` is already on `SessionSecrets`; add `@Parcelize` support by making it implement `Parcelable` as well.

Run: `./gradlew :common:kspDebugKotlin`
Expected: `common/schemas/io.homeassistant.companion.android.database.AppDatabase/55.json` is created.

- [ ] **Step 6: Run the migration test to verify it passes**

Run: `./gradlew :common:testDebugUnitTest --tests "*Migration54to55Test*"`
Expected: PASS, 4 tests.

- [ ] **Step 7: Format, lint, stage**

Run: `./gradlew ktlintFormat && ./gradlew :common:detektMain --continue`

Stage: `AppDatabase.kt`, `DatabaseMigration.kt`, `Server.kt`, deletion of `ServerSessionInfo.kt`, `common/schemas/.../55.json`, the new test.

---

### Task 1.4: Route `AuthenticationRepositoryImpl` through the store

**Files:**
- Modify: `common/src/main/kotlin/io/homeassistant/companion/android/common/data/authentication/impl/AuthenticationRepositoryImpl.kt` (13 call sites)
- Modify: `common/src/main/kotlin/io/homeassistant/companion/android/common/data/authentication/AuthenticationRepository.kt:39-47` (factory)
- Modify: `common/src/main/kotlin/io/homeassistant/companion/android/common/data/authentication/ServerRegistrationRepository.kt:51`
- Modify: `common/src/main/kotlin/io/homeassistant/companion/android/common/data/servers/ServerManagerImpl.kt:135-149` (`removeServer`)
- Modify: `app/src/main/kotlin/io/homeassistant/companion/android/util/PreviewData.kt:30,39`
- Test: `common/src/test/kotlin/io/homeassistant/companion/android/common/data/authentication/impl/AuthenticationRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `ServerSessionStore` from Task 1.2.
- Produces: `getSessionState()` returns `CONNECTED` only when the store holds a complete session for the server whose `installId` matches the app-wide install id.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `Given no stored session when getting session state then it is anonymous`() = runTest {
    coEvery { sessionStore.get(SERVER_ID) } returns null

    assertEquals(SessionState.ANONYMOUS, repository.getSessionState())
}

@Test
fun `Given a stored session for another install when getting session state then it is anonymous`() = runTest {
    coEvery { sessionStore.get(SERVER_ID) } returns completeSecrets.copy(installId = "other-install")

    assertEquals(SessionState.ANONYMOUS, repository.getSessionState())
}

@Test
fun `Given a stored session for this install when getting session state then it is connected`() = runTest {
    coEvery { sessionStore.get(SERVER_ID) } returns completeSecrets

    assertEquals(SessionState.CONNECTED, repository.getSessionState())
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :common:testDebugUnitTest --tests "*AuthenticationRepositoryImplTest*"`
Expected: FAIL, `sessionStore` is not a constructor parameter.

- [ ] **Step 3: Replace `server.session` with store reads**

`getSessionState()` becomes:

```kotlin
override suspend fun getSessionState(): SessionState {
    val secrets = sessionStore.get(serverId)
    return if (secrets != null &&
        secrets.installId == installId &&
        server().connection.hasAtLeastOneUrl
    ) {
        SessionState.CONNECTED
    } else {
        SessionState.ANONYMOUS
    }
}
```

Apply the same substitution at every other site: read `sessionStore.get(serverId)` instead of `server.session`, and write with `sessionStore.set(serverId, secrets)` instead of `serverManager.updateServer(server.copy(session = ...))`.

- [ ] **Step 4: Clear the session on server removal**

In `ServerManagerImpl.removeServer`, add `sessionStore.remove(id)` next to the existing `authenticationRepository(id).deletePreferences()` call.

- [ ] **Step 5: Run the full common test suite**

Run: `./gradlew :common:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Build every module that touched `Server.session`**

Run: `./gradlew :app:compileFullDebugKotlin :wear:compileDebugKotlin :automotive:compileFullDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Format, lint, stage**

Run: `./gradlew ktlintFormat && ./gradlew detektMain --continue`

---

### Task 1.5: Make the backup exclusion explicit — DONE, and it mattered more than expected

**Files:**
- Modify: `app/src/main/res/xml/backup_rules.xml`
- Modify: `app/src/main/res/xml/backup_rules_android12.xml`

- [ ] **Step 1: Add the exclusions**

One exclusion went into `backup_rules.xml`, and into both `<cloud-backup>` and
`<device-transfer>` in `backup_rules_android12.xml`:

```xml
<exclude domain="sharedpref" path="keyset_prefs.xml" />
```

An explicit `<exclude domain="file" path="datastore/" />` was tried first and **Android Lint
rejects it**: `datastore/ is not in an included path [FullBackupContent]`. That is lint confirming
the reasoning rather than contradicting it, since only the `database` and `sharedpref` domains are
included and excluding a domain that is not included means nothing. A comment in both files records
the reliance on include-only semantics and warns against adding a `file` include.

The keyset exclusion is a genuine bug fix, not hygiene. `AndroidKeysetManager.withSharedPref(context,
"keyset", "keyset_prefs")` writes to `shared_prefs/keyset_prefs.xml`, which the blanket
`<include domain="sharedpref" path="."/>` was sweeping into the backup. On a restored device Tink
then finds a keyset it cannot unwrap, because the Keystore master key was never backed up, and
`readMasterkeyDecryptAndParseKeyset` deliberately throws rather than replacing it:

```java
// Throw the exception if the key exists but is unusable. We can't recover by generating a
// new key because there might be existing encrypted data under the unusable key.
```

That exception escapes `SessionDatastore`'s keyset initialiser and is *not* caught by
`ReplaceFileCorruptionHandler`, which only wraps serializer failures inside DataStore. Excluding
the file means a restored install finds no keyset, generates a fresh one, reads an absent
datastore as empty, and lands on re-authentication as intended.

- [ ] **Step 2: Verify**

Run: `./gradlew :app:lintFullDebug --continue`
Expected: no new findings.

---

### Task 1.6: End-to-end restore check on a device

- [ ] **Step 1: Install and onboard**

```bash
export ANDROID_SERIAL=<device-serial>
./gradlew :app:installFullDebug
```

Onboard at least two servers so the multi-server path is exercised.

- [ ] **Step 2: Back up, wipe, restore**

```bash
adb shell bmgr backupnow io.homeassistant.companion.android.debug
adb shell pm clear io.homeassistant.companion.android.debug
adb shell bmgr restore <token> io.homeassistant.companion.android.debug
```

- [ ] **Step 3: Confirm the expected post-restore state**

Launch the app and check with:

```bash
adb shell run-as io.homeassistant.companion.android.debug \
  sqlite3 databases/homeassistant.db "SELECT id, _name, external_url, webhook_id FROM servers"
adb shell run-as io.homeassistant.companion.android.debug ls files/datastore/
```

Expected: both server rows present with URL and webhook intact; `files/datastore/` empty or missing.

At this point PR 1 is complete. The app still sends the user to onboarding, and `cleanupServers()` still deletes the servers. PR 2 fixes that.

---

# PR 2: Stop deleting restored servers

### Task 2.1: Distinguish "incomplete" from "needs re-registration"

**Files:**
- Create: `common/src/main/kotlin/io/homeassistant/companion/android/common/data/servers/ServerRegistrationState.kt`
- Modify: `common/src/main/kotlin/io/homeassistant/companion/android/common/data/servers/ServerManager.kt`
- Modify: `common/src/main/kotlin/io/homeassistant/companion/android/common/data/servers/ServerManagerImpl.kt`
- Test: `common/src/test/kotlin/io/homeassistant/companion/android/common/data/servers/ServerManagerImplTest.kt`

**Interfaces:**
- Produces: `ServerManager.registrationState(serverId: Int): ServerRegistrationState`, with the sealed hierarchy below.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `Given a server with url webhook and session when getting state then it is registered`() = runTest { /* ... */ }

@Test
fun `Given a server with url and webhook but no session when getting state then it needs re-registration`() = runTest { /* ... */ }

@Test
fun `Given a server with no webhook when getting state then it is incomplete`() = runTest { /* ... */ }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :common:testDebugUnitTest --tests "*ServerManagerImplTest*"`

- [ ] **Step 3: Add the sealed interface**

```kotlin
package io.homeassistant.companion.android.common.data.servers

/** How usable a stored server is, which decides what the launch flow does with it. */
sealed interface ServerRegistrationState {

    /** Fully usable: connection details and a session for this install. */
    data object Registered : ServerRegistrationState

    /**
     * Connection details are complete but there is no session for this install, which is what a
     * restored backup looks like. The server must be re-registered and must not be deleted.
     */
    data object NeedsReRegistration : ServerRegistrationState

    /** Onboarding never finished: no webhook or no URL. Safe to delete. */
    data object Incomplete : ServerRegistrationState
}
```

- [ ] **Step 4: Implement `registrationState`**

```kotlin
override suspend fun registrationState(serverId: Int): ServerRegistrationState {
    val server = getServer(serverId) ?: return ServerRegistrationState.Incomplete
    if (!server.connection.isRegistered) return ServerRegistrationState.Incomplete

    val connected = FailFast.failOnCatchSuspend(
        message = { "Failed to get authenticationRepository for ${server.id}." },
        fallback = false,
    ) { authenticationRepository(server.id).getSessionState() == SessionState.CONNECTED }

    return if (connected) ServerRegistrationState.Registered else ServerRegistrationState.NeedsReRegistration
}
```

- [ ] **Step 5: Run to verify pass, then format, lint, stage**

Run: `./gradlew :common:testDebugUnitTest --tests "*ServerManagerImplTest*" && ./gradlew ktlintFormat && ./gradlew :common:detektMain --continue`

---

### Task 2.2: Make `cleanupServers()` non-destructive for restored servers

**Files:**
- Modify: `app/src/main/kotlin/io/homeassistant/companion/android/launch/LaunchViewModel.kt:292-305`
- Test: `app/src/test/kotlin/io/homeassistant/companion/android/launch/LaunchViewModelTest.kt`

**Interfaces:**
- Consumes: `ServerManager.registrationState` from Task 2.1.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `Given a server needing re-registration when launching then it is not removed`() = runTest { /* ... */ }

@Test
fun `Given an incomplete server when launching then it is removed`() = runTest { /* ... */ }

@Test
fun `Given two servers needing re-registration when launching then neither is removed`() = runTest { /* ... */ }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testFullDebugUnitTest --tests "*LaunchViewModelTest*"`

- [ ] **Step 3: Narrow the deletion**

```kotlin
private suspend fun cleanupServers() {
    // Only servers whose onboarding never finished are removed. A server with complete connection
    // details but no session is a restored backup: it keeps its configuration and is offered for
    // re-registration instead.
    serverManager.servers()
        .filter { serverManager.registrationState(it.id) == ServerRegistrationState.Incomplete }
        .forEach { serverManager.removeServer(it.id) }
}
```

- [ ] **Step 4: Apply the same change on Wear**

`wear/src/main/kotlin/io/homeassistant/companion/android/home/HomePresenterImpl.kt:68` uses the identical filter-and-remove pattern. Narrow it the same way.

- [ ] **Step 5: Run, format, lint, stage**

Run: `./gradlew :app:testFullDebugUnitTest :wear:testDebugUnitTest && ./gradlew ktlintFormat && ./gradlew detektMain --continue`

---

### Task 2.3: Surface the state at launch

**Files:**
- Modify: `app/src/main/kotlin/io/homeassistant/companion/android/launch/LaunchViewModel.kt`

- [ ] **Step 1: Add a UI state for pending servers**

```kotlin
/** One or more stored servers have configuration but no session, typically after a restore. */
data class ReRegistrationRequired(val serverIds: List<Int>) : LaunchUiState
```

- [ ] **Step 2: Emit it from `handleInitialState`**

When no server is `Registered` but at least one is `NeedsReRegistration`, emit `ReRegistrationRequired` with those ids instead of navigating to onboarding.

- [ ] **Step 3: Temporarily route it to existing onboarding**

Until PR 3 lands, map `ReRegistrationRequired` to `OnboardingRoute(urlToOnboard = <first server's externalUrl>, skipWelcome = true)`. The server is no longer deleted, which is the user-visible win of PR 2.

- [ ] **Step 4: Run, format, lint, stage**

Run: `./gradlew :app:testFullDebugUnitTest && ./gradlew ktlintFormat && ./gradlew detektMain --continue`

---

# PR 3: Prefilled, multi-server re-registration

> **Decision needed before starting PR 3.** You did not pick an abandon behaviour, and flagged multi-server. Those are the same question: with N servers pending, the natural answer is a list screen that makes both "re-connect" and "remove" explicit per server, so abandoning is simply leaving the list. Tasks 3.1-3.4 assume that shape. If you would rather auto-advance through servers one at a time, 3.1 changes and 3.2-3.4 stay.

> **Build for two modes from the start.** PR 4 generalises this screen into the app's single re-auth
> flow, used whenever a session is missing *or* dead. Rather than build a restore-only screen and
> refactor it immediately, Tasks 3.1-3.4 take `ReAuthMode` (defined in Task 4.1) as a parameter now.
> Read PR 4 before starting PR 3.

### Task 3.1: Re-registration list screen

**Files:**
- Create: `app/src/main/kotlin/io/homeassistant/companion/android/onboarding/reregistration/ReRegistrationScreen.kt`
- Create: `app/src/main/kotlin/io/homeassistant/companion/android/onboarding/reregistration/ReRegistrationViewModel.kt`
- Create: `app/src/main/kotlin/io/homeassistant/companion/android/onboarding/reregistration/navigation/ReRegistrationNavigation.kt`
- Modify: `common/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/io/homeassistant/companion/android/onboarding/reregistration/ReRegistrationScreenTest.kt`

Screen lists one row per pending server showing `friendlyName` and `externalUrl`, with a primary "Reconnect" action and a secondary "Remove" action per row. Reconnect navigates to `OnboardingRoute` with the prefill payload from Task 3.2. Remove calls `serverManager.removeServer(id)` behind a confirmation dialog.

### Task 3.2: Carry the prefill through onboarding

**Files:**
- Modify: `app/src/main/kotlin/io/homeassistant/companion/android/onboarding/OnboardingNavigation.kt`
- Modify: `app/src/main/kotlin/io/homeassistant/companion/android/onboarding/nameyourdevice/navigation/NameYourDeviceNavigation.kt:18`

Add a `@Serializable data class ServerPrefill` carrying everything you selected: `deviceName`, `nameOverride`, `internalUrl`, `cloudUrl`, `useCloud`, `allowInsecureConnection`, `internalSsids`, `internalEthernet`, `internalVpn`, `prioritizeInternal`. Thread it through `OnboardingRoute` and into `NameYourDeviceRoute(url, authCode, prefill)`.

### Task 3.3: Apply the prefill in each screen's ViewModel

Device name field starts at `prefill.deviceName`; the home-network screen starts with the stored SSIDs selected; the connection screen starts with the stored internal URL and cloud settings; the server keeps its `nameOverride`.

### Task 3.4: Reuse the existing server row on completion

**Files:**
- Modify: `common/src/main/kotlin/io/homeassistant/companion/android/common/data/authentication/ServerRegistrationRepository.kt`

Registration must `updateServer` the existing row and `sessionStore.set` its id, rather than `addServer` creating a duplicate. Keeps widgets, sensors, and settings that reference the old server id working.

---

---

# PR 4: One re-auth flow for every broken session

## Why this exists

Research for this PR found that "delete the server" is the app's answer to a broken session in
**three** separate places, not one:

| Where | Today |
|---|---|
| `LaunchViewModel.cleanupServers()` (`LaunchViewModel.kt:292-305`) | Deletes silently at launch. This is the restore bug PR 2 fixes. |
| `HomePresenterImpl.kt:68` (Wear) | Same filter-and-remove pattern. PR 2 fixes it too. |
| `errorActions(AuthRevoked)` (`ErrorAction.kt:52`) | `listOf(removeServer(), settings)` — deleting the server is the *primary* offered recovery. |

The third one is the multi-server case you asked about, and the plumbing for it already exists:

```
FrontendUrlManager.kt:62-66     !sessionManager.isSessionConnected(id)
                                  -> emit(UrlLoadResult.SessionNotConnected(id))
FrontendViewModel.kt:1036-1044  -> FrontendConnectionError.AuthRevoked(...)
ErrorAction.kt:52               -> listOf(removeServer(), settings)
```

`SessionNotConnected` is emitted *before* `activateServer`, so it is exactly the right interception
point: switching to a server with no usable session already produces a typed signal carrying the
server id. It just needs somewhere better to go than deletion.

## The two modes

A dead session and a missing session need different work, and conflating them would either
re-register devices unnecessarily or leave a restored install sharing another device's webhook.

| Mode | Situation | Work needed |
|---|---|---|
| `ReAuthMode.TokenOnly` | A session record exists for this install, but the tokens are dead: refresh rejected, or auth revoked server-side. | OAuth only. The `webhook_id` and `install_id` still belong to this install, so the device registration stays valid and must be left alone. |
| `ReAuthMode.Full` | No session record at all. After PR 1 this means a restored backup. | OAuth **and** device re-registration. The `webhook_id` in the restored row was created by the install the backup came from; reusing it would make two devices share one registration. |

The discriminator falls straight out of PR 1 and needs no new state: `ServerSessionStore.get(serverId)`
returns non-null for `TokenOnly` and null for `Full`.

One nuance worth knowing before you start: `NeedsReRegistration` is detectable statically at launch
(the store has no record), but `TokenOnly` is only ever discovered at runtime, when a refresh is
rejected. `ServerSessionStore.get()` filters on `isComplete()`, not on expiry, and a refresh token's
validity cannot be known without asking the server. So PR 4 has one static entry point and three
dynamic ones.

---

### Task 4.1: `ReAuthMode` and the request model

**Files:**
- Create: `app/src/main/kotlin/io/homeassistant/companion/android/onboarding/reauth/ReAuthMode.kt`
- Modify: `common/src/main/kotlin/io/homeassistant/companion/android/common/data/servers/ServerRegistrationState.kt`
- Test: `app/src/test/kotlin/io/homeassistant/companion/android/onboarding/reauth/ReAuthModeTest.kt`

**Interfaces:**
- Consumes: `ServerSessionStore.get` (Task 1.2), `ServerRegistrationState` (Task 2.1).
- Produces: `ReAuthMode` sealed interface; `ReAuthRequest` data class; `reAuthModeFor(serverId): ReAuthMode`.

- [ ] **Step 1: Write the failing test**

```kotlin
package io.homeassistant.companion.android.onboarding.reauth

import io.homeassistant.companion.android.common.data.servers.session.ServerSessionStore
import io.homeassistant.companion.android.common.data.servers.session.SessionSecrets
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test

class ReAuthModeTest {

    private val sessionStore: ServerSessionStore = mockk()

    private val secrets = SessionSecrets(
        accessToken = "access",
        refreshToken = "refresh",
        tokenExpiration = 1789634869L,
        tokenType = "Bearer",
        installId = "install-1",
    )

    @Test
    fun `Given a stored session when resolving mode then only the tokens are renewed`() = runTest {
        coEvery { sessionStore.get(1) } returns secrets

        assertEquals(ReAuthMode.TokenOnly, reAuthModeFor(sessionStore, serverId = 1))
    }

    @Test
    fun `Given no stored session when resolving mode then the device is re-registered too`() = runTest {
        coEvery { sessionStore.get(1) } returns null

        assertEquals(ReAuthMode.Full, reAuthModeFor(sessionStore, serverId = 1))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testFullDebugUnitTest --tests "*ReAuthModeTest*"`
Expected: FAIL, unresolved reference `ReAuthMode`.

- [ ] **Step 3: Write the model**

```kotlin
package io.homeassistant.companion.android.onboarding.reauth

import io.homeassistant.companion.android.common.data.servers.session.ServerSessionStore
import kotlinx.serialization.Serializable

/** How much of the registration has to be redone to make a server usable again. */
@Serializable
sealed interface ReAuthMode {

    /**
     * The tokens are dead but the device registration is still this install's. Renew the tokens
     * and leave `webhook_id` and `install_id` alone.
     */
    @Serializable
    data object TokenOnly : ReAuthMode

    /**
     * There is no session for this install, which after the session move means a restored backup.
     * The stored `webhook_id` belongs to the install the backup came from, so the device has to be
     * registered again to get its own webhook.
     */
    @Serializable
    data object Full : ReAuthMode
}

/** What the re-auth screen needs to run: which server, how much to redo, and why. */
@Serializable
data class ReAuthRequest(val serverId: Int, val mode: ReAuthMode, val trigger: ReAuthTrigger)

/** What sent the user here. Drives the explanatory copy only, never the work performed. */
@Serializable
enum class ReAuthTrigger { RESTORED_BACKUP, SERVER_SWITCH, TOKEN_REFRESH_FAILED, AUTH_REVOKED }

/**
 * Resolves the mode from stored state: a session record means the device registration survived,
 * its absence means it did not.
 */
suspend fun reAuthModeFor(sessionStore: ServerSessionStore, serverId: Int): ReAuthMode =
    if (sessionStore.get(serverId) != null) ReAuthMode.TokenOnly else ReAuthMode.Full
```

- [ ] **Step 4: Add the state case**

In `ServerRegistrationState.kt`, add alongside the existing cases:

```kotlin
    /**
     * A session exists for this install but the server rejected it. Only discoverable at runtime,
     * when a token refresh or a frontend load fails.
     */
    data object NeedsReAuth : ServerRegistrationState
```

- [ ] **Step 5: Run to verify it passes, then format, lint, stage**

Run: `./gradlew :app:testFullDebugUnitTest --tests "*ReAuthModeTest*" && ./gradlew ktlintFormat && ./gradlew detektMain --continue`

---

### Task 4.2: Skip device registration in `TokenOnly` mode

**Files:**
- Modify: `app/src/main/kotlin/io/homeassistant/companion/android/onboarding/reregistration/ReRegistrationViewModel.kt` (from Task 3.1)
- Modify: `common/src/main/kotlin/io/homeassistant/companion/android/common/data/authentication/ServerRegistrationRepository.kt`
- Test: `app/src/test/kotlin/io/homeassistant/companion/android/onboarding/reauth/ReAuthViewModelTest.kt`

**Interfaces:**
- Consumes: `ReAuthMode`, `ReAuthRequest` (Task 4.1); `ServerSessionStore.set` (Task 1.2).
- Produces: completion writes new secrets for the existing server id and calls `IntegrationRepository.registerDevice` only in `Full` mode.

`ServerRegistrationRepository.registerAuthorizationCode()` already does the OAuth exchange and
nothing else — it returns a `TemporaryServer` carrying only a session. Device registration is a
separate call (`IntegrationRepository.registerDevice`, `IntegrationRepositoryImpl.kt:122`). The two
steps are already separable, so `TokenOnly` simply omits the second.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `Given token only mode when re-auth completes then the device is not registered again`() = runTest {
    // finish re-auth with ReAuthMode.TokenOnly
    coVerify(exactly = 0) { integrationRepository.registerDevice(any()) }
    coVerify(exactly = 1) { sessionStore.set(serverId = 1, secrets = any()) }
}

@Test
fun `Given token only mode when re-auth completes then the webhook is unchanged`() = runTest {
    // assert the persisted Server still has the original webhookId
}

@Test
fun `Given full mode when re-auth completes then the device is registered again`() = runTest {
    coVerify(exactly = 1) { integrationRepository.registerDevice(any()) }
}

@Test
fun `Given full mode when re-auth completes then no duplicate server row is created`() = runTest {
    coVerify(exactly = 0) { serverManager.addServer(any()) }
    coVerify(exactly = 1) { serverManager.updateServer(any()) }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testFullDebugUnitTest --tests "*ReAuthViewModelTest*"`

- [ ] **Step 3: Branch on the mode at completion**

```kotlin
private suspend fun completeReAuth(request: ReAuthRequest, authorizationCode: String) {
    val server = serverManager.getServer(request.serverId) ?: return
    val temporary = serverRegistrationRepository.registerAuthorizationCode(
        url = server.connection.externalUrl,
        authorizationCode = authorizationCode,
        allowInsecureConnection = server.connection.allowInsecureConnection,
    ) ?: return

    sessionStore.set(request.serverId, temporary.session)

    // Full mode only: the stored webhook belongs to the install the backup came from, so this
    // device needs its own. TokenOnly keeps the existing registration.
    if (request.mode == ReAuthMode.Full) {
        serverManager.integrationRepository(request.serverId)
            .registerDevice(deviceRegistration(server))
    }
}
```

- [ ] **Step 4: Run, format, lint, stage**

Run: `./gradlew :app:testFullDebugUnitTest && ./gradlew ktlintFormat && ./gradlew detektMain --continue`

---

### Task 4.3: Multi-server switching enters re-auth instead of an error screen

**Files:**
- Modify: `app/src/main/kotlin/io/homeassistant/companion/android/frontend/FrontendViewModel.kt:1036-1044`
- Modify: `app/src/main/kotlin/io/homeassistant/companion/android/frontend/error/ErrorAction.kt:52`
- Modify: `app/src/main/kotlin/io/homeassistant/companion/android/frontend/error/ErrorActionIntent.kt`
- Modify: `common/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/io/homeassistant/companion/android/frontend/FrontendViewModelTest.kt`

**Interfaces:**
- Consumes: `ReAuthRequest`, `reAuthModeFor` (Task 4.1).
- Produces: `ErrorActionIntent.ReAuth(request: ReAuthRequest)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `Given switching to a server with no session when loading then re-auth is offered`() = runTest {
    // emit UrlLoadResult.SessionNotConnected(serverId = 2)
    // assert the error actions start with a ReAuth intent for server 2 in Full mode
}

@Test
fun `Given switching to a server with dead tokens when loading then token-only re-auth is offered`() = runTest {
    // sessionStore.get(2) returns complete secrets
    // assert the ReAuth intent carries ReAuthMode.TokenOnly
}

@Test
fun `Given an auth error when listing actions then removing the server is not the first option`() = runTest {
    val actions = errorActions(authRevoked, isInternalConnection = false)

    assert(actions.first().intent is ErrorActionIntent.ReAuth)
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testFullDebugUnitTest --tests "*FrontendViewModelTest*"`

- [ ] **Step 3: Carry the server id and mode into the error**

In `FrontendViewModel.kt:1036`, replace the `SessionNotConnected` branch so the resulting
`AuthRevoked` carries a `ReAuthRequest` built from `reAuthModeFor(sessionStore, result.serverId)`
with `trigger = ReAuthTrigger.SERVER_SWITCH`.

- [ ] **Step 4: Offer re-auth ahead of deletion**

In `ErrorAction.kt`, replace:

```kotlin
is FrontendConnectionError.AuthRevoked -> listOf(removeServer(), settings)
```

with:

```kotlin
is FrontendConnectionError.AuthRevoked -> listOf(
    ErrorAction(
        labelRes = commonR.string.error_action_sign_in_again,
        style = ErrorAction.Style.Primary,
        intent = ErrorActionIntent.ReAuth(error.reAuthRequest),
    ),
    removeServer(style = ErrorAction.Style.Secondary),
    settings,
)
```

Add to `common/src/main/res/values/strings.xml`:

```xml
<string name="error_action_sign_in_again">Sign in again</string>
```

Removing the server stays available, but stops being the only way out.

- [ ] **Step 5: Run, format, lint, stage**

Run: `./gradlew :app:testFullDebugUnitTest && ./gradlew ktlintFormat && ./gradlew detektMain --continue`

---

### Task 4.4: A failed token refresh enters re-auth

**Files:**
- Modify: `common/src/main/kotlin/io/homeassistant/companion/android/common/data/authentication/impl/AuthenticationRepositoryImpl.kt:109-120`
- Test: `common/src/test/kotlin/io/homeassistant/companion/android/common/data/authentication/impl/AuthenticationRepositoryImplTest.kt`

**Interfaces:**
- Produces: `ensureValidSession` clears the stored secrets when the server rejects the refresh token, so the next `registrationState` call reports `NeedsReAuth` rather than looping on a dead token.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `Given the server rejects the refresh token when ensuring a session then the stored secrets are cleared`() = runTest {
    coEvery { authenticationService.refreshToken(any(), any(), any(), any()) } throws HttpException(unauthorizedResponse)

    runCatching { repository.ensureValidSession(forceRefresh = true) }

    coVerify { sessionStore.remove(SERVER_ID) }
}

@Test
fun `Given a transient network failure when ensuring a session then the stored secrets are kept`() = runTest {
    coEvery { authenticationService.refreshToken(any(), any(), any(), any()) } throws IOException("offline")

    runCatching { repository.ensureValidSession(forceRefresh = true) }

    coVerify(exactly = 0) { sessionStore.remove(any()) }
}
```

The second test is the one that matters. Clearing on any failure would sign users out every time
they open the app on a flaky network, so only an explicit rejection by the server counts.

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :common:testDebugUnitTest --tests "*AuthenticationRepositoryImplTest*"`

- [ ] **Step 3: Clear only on explicit rejection**

In `refreshSessionWithToken`, catch `HttpException` with code 400 or 401 and call
`sessionStore.remove(serverId)` before rethrowing. Let `IOException` and every other throwable
propagate untouched.

- [ ] **Step 4: Run, format, lint, stage**

Run: `./gradlew :common:testDebugUnitTest && ./gradlew ktlintFormat && ./gradlew detektMain --continue`

---

### Task 4.5: End-to-end multi-server check

- [ ] **Step 1: Two servers, one broken**

Onboard two servers. Then break exactly one of them from a shell, leaving the other alone:

```bash
adb shell run-as io.homeassistant.companion.android.debug rm files/datastore/server_session_2.pb
```

- [ ] **Step 2: Confirm the switch offers re-auth**

Open the app on server 1, confirm it loads normally, then switch to server 2. Expected: the
re-auth screen for server 2 in `Full` mode, with its name, URL, internal URL, SSIDs and cloud
settings prefilled. Server 1 keeps working throughout.

- [ ] **Step 3: Confirm `TokenOnly` does not re-register**

Revoke the token for server 1 from Home Assistant (Profile → Security → refresh tokens → delete
the companion app token), then reload the frontend. Expected: "Sign in again" offered as the
primary action, and after signing in:

```bash
adb shell run-as io.homeassistant.companion.android.debug \
  sqlite3 databases/homeassistant.db "SELECT id, webhook_id FROM servers"
```

`webhook_id` for server 1 is unchanged, and Home Assistant shows no duplicate device.

---

---

# PR 5: Reconcile server-scoped config that needs a human decision

## What survives a restore, and what breaks

Eleven tables carry a `server_id` and all of them come back in the backup:

```
sensors                        button_widgets        camera_widgets
media_player_controls_widgets  static_widget         todo_widget
template_widgets               notification_history  location_history
qs_tiles                       media_controls_entity_config
```

After PR 2 their `server_id` values still resolve, so nothing dangles at the database level. The
problem is that several of these rows describe objects that live **outside** the app, on the host,
and those did not necessarily come back with them.

| Config | What actually happens on the new phone | Needs a human? |
|---|---|---|
| Widgets | The launcher may restore widget instances, but Android assigns **new** widget ids and notifies the provider through `AppWidgetProvider.onRestored(context, oldWidgetIds, newWidgetIds)`. The app implements no such callback anywhere, so restored widgets get ids matching no row, and the old rows keep ids matching no widget. | Only for the leftovers |
| Quick settings tiles | Tile placement in the QS panel is per-device and user-controlled. Rows survive, tiles are not placed. | Yes |
| Sensors | Re-register automatically once re-auth completes, but rows keep `registered` state tied to the **old** webhook. After `ReAuthMode.Full` the webhook is different. | No, but needs a reset |
| `notification_history`, `location_history` | Historical data with no host-side object. Arguably should never have been in the backup. | No |
| `media_controls_entity_config` | Pure config, keyed by entity. Survives correctly. | No |

Task 5.1 is worth landing regardless of the session work: widget id remapping is missing today, so
widgets are already lost on every device transfer.

---

### Task 5.1: Remap widget ids on restore

**Files:**
- Modify: `app/src/main/kotlin/io/homeassistant/companion/android/widgets/BaseWidgetProvider.kt`
- Modify: `app/src/main/kotlin/io/homeassistant/companion/android/widgets/BaseGlanceEntityWidgetReceiver.kt`
- Create: `app/src/main/kotlin/io/homeassistant/companion/android/widgets/restore/WidgetIdRemapper.kt`
- Test: `app/src/test/kotlin/io/homeassistant/companion/android/widgets/restore/WidgetIdRemapperTest.kt`

**Interfaces:**
- Produces: `WidgetIdRemapper.remap(oldWidgetIds: IntArray, newWidgetIds: IntArray)` — rewrites the
  primary key of every widget table so restored widgets keep their configuration.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `Given a restored widget when remapping then its configuration moves to the new id`() = runTest {
    buttonWidgetDao.add(buttonWidget(id = 11, serverId = 1, label = "Kitchen"))

    remapper.remap(oldWidgetIds = intArrayOf(11), newWidgetIds = intArrayOf(42))

    assertNull(buttonWidgetDao.get(11))
    assertEquals("Kitchen", buttonWidgetDao.get(42)?.label)
}

@Test
fun `Given several restored widgets when remapping then each keeps its own configuration`() = runTest {
    buttonWidgetDao.add(buttonWidget(id = 11, serverId = 1, label = "Kitchen"))
    buttonWidgetDao.add(buttonWidget(id = 12, serverId = 1, label = "Garage"))

    remapper.remap(oldWidgetIds = intArrayOf(11, 12), newWidgetIds = intArrayOf(42, 43))

    assertEquals("Kitchen", buttonWidgetDao.get(42)?.label)
    assertEquals("Garage", buttonWidgetDao.get(43)?.label)
}

@Test
fun `Given a new id already in use when remapping then the stale row is replaced`() = runTest {
    buttonWidgetDao.add(buttonWidget(id = 11, serverId = 1, label = "Kitchen"))
    buttonWidgetDao.add(buttonWidget(id = 42, serverId = 1, label = "Stale"))

    remapper.remap(oldWidgetIds = intArrayOf(11), newWidgetIds = intArrayOf(42))

    assertEquals("Kitchen", buttonWidgetDao.get(42)?.label)
}

@Test
fun `Given mismatched array lengths when remapping then nothing is changed`() = runTest {
    buttonWidgetDao.add(buttonWidget(id = 11, serverId = 1, label = "Kitchen"))

    remapper.remap(oldWidgetIds = intArrayOf(11, 12), newWidgetIds = intArrayOf(42))

    assertEquals("Kitchen", buttonWidgetDao.get(11)?.label)
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testFullDebugUnitTest --tests "*WidgetIdRemapperTest*"`

- [ ] **Step 3: Write the remapper**

Pair the arrays index by index, and for each widget table move the row from the old id to the new
one inside a single transaction so a crash mid-restore cannot leave half the widgets remapped.
Android documents the two arrays as parallel and equal length; bail out without touching anything
if they are not, rather than remapping a prefix.

- [ ] **Step 4: Call it from the providers**

```kotlin
override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
    // Android assigns fresh widget ids when the launcher restores widgets, so the stored
    // configuration has to follow the widget to its new id or the widget comes back blank.
    widgetScope.launch { widgetIdRemapper.remap(oldWidgetIds, newWidgetIds) }
}
```

`BaseWidgetProvider` is an `@AndroidEntryPoint` receiver, so inject `WidgetIdRemapper` as a field
rather than constructing it. Do the same in `BaseGlanceEntityWidgetReceiver`.

- [ ] **Step 5: Run, format, lint, stage**

Run: `./gradlew :app:testFullDebugUnitTest && ./gradlew ktlintFormat && ./gradlew detektMain --continue`

---

### Task 5.2: Classify leftover config

**Files:**
- Create: `app/src/main/kotlin/io/homeassistant/companion/android/settings/restore/RestoredConfigItem.kt`
- Create: `app/src/main/kotlin/io/homeassistant/companion/android/settings/restore/RestoredConfigScanner.kt`
- Test: `app/src/test/kotlin/io/homeassistant/companion/android/settings/restore/RestoredConfigScannerTest.kt`

**Interfaces:**
- Consumes: `AppWidgetManager.getAppWidgetIds`, the widget DAOs, `QuickSettingTilesDao`.
- Produces: `RestoredConfigScanner.scan(): List<RestoredConfigItem>`.

```kotlin
/** A piece of restored configuration whose host-side counterpart is missing on this device. */
sealed interface RestoredConfigItem {
    val serverId: Int
    val label: String

    /** A widget row whose widget id no longer exists in [AppWidgetManager]. */
    data class OrphanWidget(
        override val serverId: Int,
        override val label: String,
        val widgetId: Int,
        val type: WidgetType,
    ) : RestoredConfigItem

    /** A configured quick settings tile that is not placed in the QS panel on this device. */
    data class UnplacedTile(
        override val serverId: Int,
        override val label: String,
        val tileId: String,
    ) : RestoredConfigItem
}
```

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `Given a widget row with no live widget when scanning then it is reported as orphaned`() = runTest { /* ... */ }

@Test
fun `Given a widget row with a live widget when scanning then it is not reported`() = runTest { /* ... */ }

@Test
fun `Given a configured tile when scanning then it is reported as unplaced`() = runTest { /* ... */ }

@Test
fun `Given config for two servers when scanning then each item carries its own server id`() = runTest { /* ... */ }
```

The fourth test is the multi-server case: the review screen groups by server, so every item has to
carry the right `serverId` even when only one server was restored.

- [ ] **Step 2: Run to verify failure, implement, verify pass**

Run: `./gradlew :app:testFullDebugUnitTest --tests "*RestoredConfigScannerTest*"`

Scan by comparing each widget table's ids against `AppWidgetManager.getAppWidgetIds(provider)` for
that widget's provider component, and by listing `qs_tiles` rows. Run the scan off the main thread
using a `@VisibleForTesting` constructor taking the dispatcher, with the `@Inject` constructor
delegating and defaulting to `Dispatchers.IO`.

- [ ] **Step 3: Format, lint, stage**

---

### Task 5.3: Review screen with keep or delete per item

**Files:**
- Create: `app/src/main/kotlin/io/homeassistant/companion/android/settings/restore/RestoredConfigScreen.kt`
- Create: `app/src/main/kotlin/io/homeassistant/companion/android/settings/restore/RestoredConfigViewModel.kt`
- Create: `app/src/main/kotlin/io/homeassistant/companion/android/settings/restore/navigation/RestoredConfigNavigation.kt`
- Modify: `common/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/io/homeassistant/companion/android/settings/restore/RestoredConfigScreenTest.kt`

The screen lists items grouped by server, each with two actions:

- **Add back** — for a tile, `TileService.requestAddTileService()` on API 33+, falling back to
  instructions on older releases; for a widget, open the matching configure activity pre-filled
  from the orphan row so the user places it and the row is rebound to the new widget id.
- **Delete** — remove the row.

Plus **Delete all** per server for the common case of someone who does not want their old widgets.

Two things this screen must not do: delete anything without the user asking, and block app startup.
It is reachable from Settings and offered once via a dismissible banner after a restore, never as a
modal gate. Deleting is always explicit, which is the same principle PR 2 applied to servers.

- [ ] **Step 1: Write the failing interaction tests**

```kotlin
@Test
fun `Given orphaned config when the screen shows then each item offers add back and delete`() { /* ... */ }

@Test
fun `Given an orphaned widget when delete is tapped then the row is removed`() { /* ... */ }

@Test
fun `Given an unplaced tile when add back is tapped then the add-tile request is made`() { /* ... */ }

@Test
fun `Given items for two servers when the screen shows then they are grouped under each server name`() { /* ... */ }

@Test
fun `Given no orphaned config when the screen shows then the empty state is shown`() { /* ... */ }
```

- [ ] **Step 2: Run to verify failure, implement, verify pass, format, lint, stage**

Run: `./gradlew :app:testFullDebugUnitTest --tests "*RestoredConfigScreenTest*"`

---

### Task 5.4: Reset sensor registration after a `Full` re-auth

**Files:**
- Modify: `app/src/main/kotlin/io/homeassistant/companion/android/onboarding/reauth/ReAuthViewModel.kt` (Task 4.2)
- Modify: `common/src/main/kotlin/io/homeassistant/companion/android/common/sensors/SensorRepository.kt`
- Test: `common/src/test/kotlin/io/homeassistant/companion/android/common/sensors/SensorRepositoryTest.kt`

`ReAuthMode.Full` produces a **new** webhook, so every sensor row still marked registered is
registered against a webhook this install no longer owns. Without a reset, sensors silently stop
updating and look enabled.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `Given registered sensors when the device is re-registered then they are marked unregistered`() = runTest {
    sensorDao.add(sensor(id = "battery_level", serverId = 1, registered = true))

    sensorRepository.clearRegistration(serverId = 1)

    assertNull(sensorDao.get("battery_level", serverId = 1)?.registered)
}

@Test
fun `Given registered sensors when the device is re-registered then enabled state is preserved`() = runTest {
    sensorDao.add(sensor(id = "battery_level", serverId = 1, registered = true, enabled = true))

    sensorRepository.clearRegistration(serverId = 1)

    assertEquals(true, sensorDao.get("battery_level", serverId = 1)?.enabled)
}
```

The second test matters: the user's enable/disable choices are configuration worth keeping, only
the registration bookkeeping is stale.

- [ ] **Step 2: Run to verify failure, implement, verify pass**

Call `sensorRepository.clearRegistration(serverId)` from `completeReAuth` in the `Full` branch only,
next to `registerDevice`. `TokenOnly` keeps its webhook, so its sensors stay registered.

- [ ] **Step 3: Format, lint, stage**

---

### Task 5.5: Stop backing up history tables

**Files:**
- Modify: `app/src/main/res/xml/backup_rules.xml`
- Modify: `app/src/main/res/xml/backup_rules_android12.xml`

`notification_history` and `location_history` are device-local history with no host-side object and
no value on a new phone, and `location_history` in particular is a detailed record of where the user
has been. Backing them up is cost without benefit.

Room keeps all tables in one file, so they cannot be excluded by path. Options, in order of
preference:

1. Move both tables to a second Room database under `files/`, outside the backup, alongside the
   session store. Consistent with PR 1 and needs no new mechanism.
2. Clear them in a `BackupAgent.onFullBackup` override before the database is written.

Option 1 is the recommendation. Size this task once PR 1 has landed, since it reuses the same
pattern and the second database will be cheaper to add by then.

---

## Self-review notes

- **Spec coverage.** Session extraction (1.1-1.3), migration of existing installs (1.3), backup exclusion (1.5), stop deleting (2.1-2.2), prefilled onboarding (3.1-3.3), all four prefill categories you chose (3.2), unified re-auth for missing and dead tokens (4.1-4.2), multi-server switching (4.3), restored config needing a user decision (5.1-5.4).
- **Known gap.** The abandon behaviour is the one open decision, called out above 3.1 rather than buried in a task.
- **Risk.** Task 1.3's `CREATE TABLE` string must match Room's generated schema exactly; the step says to reconcile it against `55.json` after generation. This is the most likely place to lose time.
- **Standalone value.** Task 5.1 (widget id remapping) fixes an existing bug and does not depend on any other task. Task 5.5 is scoped rather than fully specified, deliberately, because it should reuse PR 1's second-database pattern.
- **Not covered.** `PREF_ACTIVE_SERVER` also lives in the excluded `session_0`, so the active-server choice is lost on restore regardless. With one server it self-corrects; with several the app picks a default. Worth a follow-up, out of scope here.

## Recurring theme

Four independent places answer "this session is broken" by destroying user data: `cleanupServers()`
at launch, the same pattern on Wear, `errorActions(AuthRevoked)` offering deletion as the primary
recovery, and the absence of widget remapping quietly stranding every widget on a device transfer.
The plan replaces each with a path that keeps the configuration and asks. That principle is what
makes PR 2, PR 4 and PR 5 one piece of work rather than three.
