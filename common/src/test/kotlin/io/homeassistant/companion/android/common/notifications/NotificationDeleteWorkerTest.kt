package io.homeassistant.companion.android.common.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoints
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.notifications.NotificationDeleteWorker.Companion.NotificationDeleteWorkerEntryPoint
import io.homeassistant.companion.android.database.notification.NotificationDao
import io.homeassistant.companion.android.database.notification.NotificationItem
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ConsoleLogExtension::class)
class NotificationDeleteWorkerTest {

    private val serverManager: ServerManager = mockk()
    private val notificationDao: NotificationDao = mockk()
    private val integrationRepository: IntegrationRepository = mockk(relaxed = true)
    private val context: Context = mockk()
    private val workerParams: WorkerParameters = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        every { context.applicationContext } returns context
        coEvery { serverManager.integrationRepository(any()) } returns integrationRepository

        mockkStatic(EntryPoints::class)
        every {
            EntryPoints.get(any(), NotificationDeleteWorkerEntryPoint::class.java)
        } returns mockk {
            every { serverManager() } returns serverManager
            every { notificationDao() } returns notificationDao
        }
    }

    @Test
    fun `Given valid input when doWork then fire event and return success`() = runTest {
        val eventData = mapOf("action" to "cleared", "tag" to "test-tag")
        val databaseId = 42L
        val serverId = 5
        setupWorkerInput(databaseId = databaseId, eventData = eventData)
        coEvery { notificationDao.get(databaseId.toInt()) } returns notificationItem(serverId = serverId)

        val worker = NotificationDeleteWorker(context, workerParams)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) {
            serverManager.integrationRepository(serverId)
            integrationRepository.fireEvent("mobile_app_notification_cleared", eventData)
        }
    }

    @Test
    fun `Given notification not in database when doWork then use active server and return success`() = runTest {
        val eventData = mapOf("action" to "cleared")
        val databaseId = 99L
        setupWorkerInput(databaseId = databaseId, eventData = eventData)
        coEvery { notificationDao.get(databaseId.toInt()) } returns null

        val worker = NotificationDeleteWorker(context, workerParams)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) {
            serverManager.integrationRepository(ServerManager.SERVER_ID_ACTIVE)
            integrationRepository.fireEvent("mobile_app_notification_cleared", eventData)
        }
    }

    @Test
    fun `Given missing event data when doWork then return failure`() = runTest {
        every { workerParams.inputData } returns Data.Builder()
            .putLong("database_id", 1L)
            .build()

        val worker = NotificationDeleteWorker(context, workerParams)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { integrationRepository.fireEvent(any(), any()) }
    }

    @Test
    fun `Given server throws when doWork then return failure`() = runTest {
        val eventData = mapOf("action" to "cleared")
        val databaseId = 42L
        setupWorkerInput(databaseId = databaseId, eventData = eventData)
        coEvery { notificationDao.get(databaseId.toInt()) } returns notificationItem(serverId = 1)
        coEvery { integrationRepository.fireEvent(any(), any()) } throws IllegalStateException("Server unavailable")

        val worker = NotificationDeleteWorker(context, workerParams)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    private fun setupWorkerInput(databaseId: Long, eventData: Map<String, String>) {
        every { workerParams.inputData } returns Data.Builder()
            .putLong("database_id", databaseId)
            .putStringArray("event_data_keys", eventData.keys.toTypedArray())
            .putStringArray("event_data_values", eventData.values.toTypedArray())
            .build()
    }

    private fun notificationItem(serverId: Int): NotificationItem =
        NotificationItem(
            id = 1,
            received = 0L,
            message = "test",
            data = "{}",
            source = "test",
            serverId = serverId,
        )
}