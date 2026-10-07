package io.homeassistant.companion.android.database.settings

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.backup.BackupSettingsChanges
import io.homeassistant.companion.android.common.data.backup.RestoredSensorSelection
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SettingsBackupDaoTest {
    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("settings-backup-test")
        database = Room.databaseBuilder(context, AppDatabase::class.java, "settings-backup-test").build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `Given an existing sensor when restoring then registration and current readings are retained`() = runTest {
        database.serverDao().add(Server(42, "Home", connection = ServerConnectionInfo("https://example.invalid"), session = ServerSessionInfo(), user = ServerUserInfo()))
        val sensor = Sensor("battery", 42, true, registered = true, state = "85", lastSentState = "85", coreRegistration = "registered")
        database.sensorDao().upsert(sensor)
        database.settingsBackupDao().apply(
            BackupSettingsChanges(setOf(42), listOf(RestoredSensorSelection("battery", 42, false)), emptyList(), mapOf(42 to WebsocketSetting.ALWAYS), SensorUpdateFrequencySetting.FAST_ALWAYS),
        )
        val restored = database.sensorDao().get("battery", 42)!!
        assertFalse(restored.enabled)
        assertEquals(sensor.registered, restored.registered)
        assertEquals(sensor.state, restored.state)
        assertEquals(sensor.coreRegistration, restored.coreRegistration)
        assertNull(restored.lastSentState)
        assertEquals(WebsocketSetting.ALWAYS, database.settingsDao().get(42)?.websocketSetting)
        assertEquals(SensorUpdateFrequencySetting.FAST_ALWAYS, database.settingsDao().get(0)?.sensorUpdateFrequency)
    }

    @Test
    fun `Given a removed destination when restoring then no database changes are applied`() = runTest {
        val original = Setting(0, WebsocketSetting.NEVER, SensorUpdateFrequencySetting.NORMAL)
        database.settingsDao().insert(original)
        try {
            database.settingsBackupDao().apply(BackupSettingsChanges(setOf(42), emptyList(), emptyList(), emptyMap(), SensorUpdateFrequencySetting.FAST_ALWAYS))
            throw AssertionError("Restore should fail")
        } catch (expected: IllegalStateException) {
            assertEquals(original, database.settingsDao().get(0))
        }
    }
}
