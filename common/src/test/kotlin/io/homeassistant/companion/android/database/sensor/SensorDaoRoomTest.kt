package io.homeassistant.companion.android.database.sensor

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.database.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests the [SensorDao] default methods that run SQL against a real database. They use an in-memory
 * Room database because [SensorDao.setSensorsEnabled] runs its work inside `coroutineScope { async }`,
 * which a mocked DAO cannot execute. The mock-friendly default methods are covered in [SensorDaoTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SensorDaoRoomTest {

    private lateinit var database: AppDatabase
    private lateinit var sensorDao: SensorDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sensorDao = database.sensorDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `Given sensor still enabled on another server when disabling then keeps attributes`() = runTest {
        sensorDao.upsert(Sensor("sensor", serverId = 1, enabled = true, state = "on"))
        sensorDao.upsert(Sensor("sensor", serverId = 2, enabled = true, state = "on"))
        sensorDao.add(Attribute("sensor", "latitude", "1.0", "float"))

        sensorDao.setSensorsEnabled(listOf("sensor"), 1, false)

        assertFalse(sensorDao.getFull("sensor").values.flatten().isEmpty())
    }

    @Test
    fun `Given sensor disabled on every server when disabling then clears attributes`() = runTest {
        sensorDao.upsert(Sensor("sensor", serverId = 1, enabled = true, state = "on"))
        sensorDao.upsert(Sensor("sensor", serverId = 2, enabled = false, state = "on"))
        sensorDao.add(Attribute("sensor", "latitude", "1.0", "float"))

        sensorDao.setSensorsEnabled(listOf("sensor"), 1, false)

        assertTrue(sensorDao.getFull("sensor").values.flatten().isEmpty())
    }
}
