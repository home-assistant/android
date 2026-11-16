package io.homeassistant.companion.android.widgets.button

import android.os.Looper
import android.widget.AutoCompleteTextView
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Action
import io.homeassistant.companion.android.common.data.integration.ActionData
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.MapAnySerializer
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.di.ServerManagerModule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val SERVER_ID = 1
private const val OTHER_SERVER_ID = 2
private const val ENTITY_FIELD = "entity_id"
private const val SERVER_RESPONSE_DELAY_MS = 1L
private val FIXED_TIME: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@UninstallModules(ServerManagerModule::class)
@HiltAndroidTest
class ButtonWidgetConfigureActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val serverManager: ServerManager = mockk(relaxed = true)

    private val integrationRepository: IntegrationRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        val server = mockServer(SERVER_ID)
        coEvery { serverManager.servers() } returns listOf(server, mockServer(OTHER_SERVER_ID))
        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { serverManager.integrationRepository(any<Int>()) } returns integrationRepository
        coEvery { integrationRepository.getEntities() } coAnswers {
            delay(SERVER_RESPONSE_DELAY_MS)
            listOf(
                entity("light.ceiling"),
                entity("scene.movie"),
                entity("script.wake_up"),
                entity("lock.front_door"),
            )
        }
    }

    @Test
    fun `Given light entity when actions load then toggle and the entity are prefilled`() = runTest {
        mockServices(
            action("light", "toggle"),
            action("light", "turn_off"),
            action("light", "turn_on"),
        )

        launchFor("light.ceiling") { activity ->
            assertEquals("light.toggle", activity.actionText())
            assertEquals(listOf("light.ceiling"), activity.pendingEntityIds())
        }
    }

    @Test
    fun `Given scene entity when actions load then turn_on is prefilled instead of the first action with a target`() = runTest {
        mockServices(
            action("scene", "apply", hasTarget = false),
            action("scene", "delete"),
            action("scene", "turn_on"),
        )

        launchFor("scene.movie") { activity ->
            assertEquals("scene.turn_on", activity.actionText())
            assertEquals(listOf("scene.movie"), activity.pendingEntityIds())
        }
    }

    @Test
    fun `Given script entity when actions load then an action without a target is not prefilled`() = runTest {
        mockServices(
            action("script", "reload", hasTarget = false),
            action("script", "toggle"),
            action("script", "turn_on"),
        )

        launchFor("script.wake_up") { activity ->
            assertEquals("script.toggle", activity.actionText())
            assertEquals(listOf("script.wake_up"), activity.pendingEntityIds())
        }
    }

    @Test
    fun `Given domain with no preferred action when actions load then the first action with a target is prefilled`() = runTest {
        mockServices(
            action("lock", "unlock"),
            action("lock", "lock"),
            action("lock", "open"),
        )

        launchFor("lock.front_door") { activity ->
            assertEquals("lock.lock", activity.actionText())
            assertEquals(listOf("lock.front_door"), activity.pendingEntityIds())
        }
    }

    @Test
    fun `Given prefilled entity when the user picks an action in the same domain then the entity is kept`() = runTest {
        mockServices(action("light", "toggle"), action("light", "turn_on"))

        launchFor("light.ceiling") { activity ->
            activity.setActionText("light.turn_on")

            assertEquals("light.turn_on", activity.actionText())
            assertEquals(listOf("light.ceiling"), activity.pendingEntityIds())
        }
    }

    @Test
    fun `Given prefilled entity when the user picks an action in another domain then the entity is not used`() = runTest {
        mockServices(action("light", "toggle"), action("switch", "toggle"))

        launchFor("light.ceiling") { activity ->
            activity.setActionText("switch.toggle")

            assertEquals("switch.toggle", activity.actionText())
            assertNull(activity.pendingEntityIds())
        }
    }

    @Test
    fun `Given prefilled action when actions load then the dropdown lists every action in the domain`() = runTest {
        mockServices(action("light", "toggle"), action("light", "turn_on"), action("switch", "toggle"))

        launchFor("light.ceiling") { activity ->
            val adapter = activity.findViewById<AutoCompleteTextView>(R.id.widget_text_config_service).adapter
            val listedActions = (0 until adapter.count).map { (adapter.getItem(it) as Action).action }

            assertEquals(listOf("toggle", "turn_on"), listedActions)
        }
    }

    @Test
    fun `Given prefilled entity when switching server then the entity is cleared`() = runTest {
        mockServices(action("light", "toggle"))

        launchFor("light.ceiling") { activity ->
            val serverList = activity.findViewById<Spinner>(R.id.server_select_list)
            serverList.onItemSelectedListener?.onItemSelected(serverList, null, 1, 1)
            idleMainLooper()
            activity.setActionText("light.toggle")

            assertNull(activity.pendingEntityIds())
        }
    }

    @Test
    fun `Given user typed an action when actions finish loading then the typed action is not overwritten`() = runTest {
        val services = CompletableDeferred<List<Action>>()
        coEvery { integrationRepository.getServices() } coAnswers { services.await() }

        launchFor("light.ceiling") { activity ->
            activity.setActionText("light.turn_off")

            services.complete(listOf(action("light", "toggle"), action("light", "turn_off")))
            idleMainLooper()

            assertEquals("light.turn_off", activity.actionText())
        }
    }

    @Test
    fun `Given no entity passed when actions load then the action stays empty`() = runTest {
        mockServices(action("light", "toggle"))

        launchFor(entityId = null) { activity ->
            assertEquals("", activity.actionText())
        }
    }

    private suspend fun launchFor(entityId: String?, assertions: suspend (ButtonWidgetConfigureActivity) -> Unit) {
        val intent = ButtonWidgetConfigureActivity.newInstance(ApplicationProvider.getApplicationContext(), entityId)
        ActivityScenario.launch<ButtonWidgetConfigureActivity>(intent).use { scenario ->
            idleMainLooper()
            lateinit var launchedActivity: ButtonWidgetConfigureActivity
            scenario.onActivity { launchedActivity = it }
            assertions(launchedActivity)
        }
    }

    private fun mockServices(vararg actions: Action) {
        coEvery { integrationRepository.getServices() } coAnswers {
            delay(SERVER_RESPONSE_DELAY_MS)
            actions.toList()
        }
    }

    private fun ButtonWidgetConfigureActivity.actionText(): String = findViewById<AutoCompleteTextView>(R.id.widget_text_config_service).text.toString()

    private fun ButtonWidgetConfigureActivity.setActionText(text: String) {
        findViewById<AutoCompleteTextView>(R.id.widget_text_config_service).setText(text)
        idleMainLooper()
    }

    // Reads the entities from the pending widget, which is what saving the widget stores.
    // The field holds a string until it's redrawn or edited, and a list after that.
    private suspend fun ButtonWidgetConfigureActivity.pendingEntityIds(): List<*>? {
        val serviceData = kotlinJsonMapper.decodeFromString(MapAnySerializer, getPendingDaoEntity().serviceData)
        return when (val value = serviceData[ENTITY_FIELD]) {
            null -> null
            is List<*> -> value
            else -> listOf(value)
        }
    }

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idleFor(SERVER_RESPONSE_DELAY_MS, TimeUnit.MILLISECONDS)

    private fun mockServer(serverId: Int): Server = mockk(relaxed = true) {
        every { id } returns serverId
        every { friendlyName } returns "Server $serverId"
    }

    private fun action(domain: String, name: String, hasTarget: Boolean = true) = Action(
        domain = domain,
        action = name,
        actionData = ActionData(target = hasTarget, fields = emptyMap()),
    )

    private fun entity(entityId: String) = Entity(entityId, "", emptyMap(), FIXED_TIME, FIXED_TIME)
}
