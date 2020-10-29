package io.homeassistant.companion.android.controls

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.actions.ControlAction
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.os.postDelayed
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import java.util.concurrent.Flow
import java.util.function.Consumer
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@RequiresApi(Build.VERSION_CODES.R)
class HaControlsProviderService : ControlsProviderService() {

    companion object {
        private const val TAG = "HaConProService"
    }

    @Inject
    lateinit var integrationRepository: IntegrationRepository

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    private val monitoredEntities = mutableListOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    // This is the poor mans way to do this.  We should really connect via websocket and update
    // on events.  But now we get updates every 5 seconds while on power menu.
    private val refresh = object : Runnable {
        override fun run() {
            monitoredEntities.forEach { entityId ->
                ioScope.launch {
                    val entity = integrationRepository.getEntity(entityId)
                    val domain = entity.entityId.split(".")[0]
                    val control = domainToHaControl[domain]?.createControl(applicationContext, entity)
                    updateSubscriber?.onNext(control)
                }
            }
            handler.postDelayed(this, 5000)
        }
    }

    private var updateSubscriber: Flow.Subscriber<in Control>? = null

    private val domainToHaControl = mapOf(
        "automation" to DefaultSwitchControl,
        "camera" to null,
        "climate" to ClimateControl,
        "cover" to CoverControl,
        "fan" to FanControl,
        "light" to LightControl,
        "lock" to LockControl,
        "media_player" to null,
        "remote" to null,
        "scene" to SceneControl,
        "script" to SceneControl,
        "switch" to DefaultSwitchControl,
        "input_boolean" to DefaultSwitchControl,
        "input_number" to DefaultSliderControl
    )

    override fun onCreate() {
        super.onCreate()

        DaggerControlsComponent.builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)
    }

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> {
        return Flow.Publisher { subscriber ->
            ioScope.launch {
                integrationRepository
                    .getEntities()
                    .mapNotNull {
                        val domain = it.entityId.split(".")[0]
                        domainToHaControl[domain]?.createControl(applicationContext, it as Entity<Map<String, Any>>)
                    }
                    .forEach {
                        subscriber.onNext(it)
                    }
                subscriber.onComplete()
            }
        }
    }

    override fun createPublisherFor(controlIds: MutableList<String>): Flow.Publisher<Control> {
        Log.d(TAG, "publisherFor $controlIds")
        return Flow.Publisher { subscriber ->
            subscriber.onSubscribe(object : Flow.Subscription {
                override fun request(n: Long) {
                    Log.d(TAG, "request $n")
                    updateSubscriber = subscriber
                }

                override fun cancel() {
                    Log.d(TAG, "cancel")
                    updateSubscriber = null
                    handler.removeCallbacks(refresh)
                }
            })
            monitoredEntities.addAll(controlIds)
            handler.post(refresh)
        }
    }

    override fun performControlAction(
        controlId: String,
        action: ControlAction,
        consumer: Consumer<Int>
    ) {
        Log.d(TAG, "Control: $controlId, action: $action")
        val domain = controlId.split(".")[0]
        val haControl = domainToHaControl[domain]

        var actionSuccess = false
        if (haControl != null) {
            runBlocking {
                actionSuccess = haControl.performAction(integrationRepository, action)

                val entity = integrationRepository.getEntity(controlId)
                updateSubscriber?.onNext(haControl.createControl(applicationContext, entity))
                handler.postDelayed(750) {
                    // This is here because the state isn't aways instantly updated.  This should
                    // cause us to update a second time rapidly to ensure we display the correct state
                    updateSubscriber?.onNext(haControl.createControl(applicationContext, entity))
                }
            }
        }
        if (actionSuccess) {
            consumer.accept(ControlAction.RESPONSE_OK)
        } else {
            consumer.accept(ControlAction.RESPONSE_UNKNOWN)
        }
    }
}
