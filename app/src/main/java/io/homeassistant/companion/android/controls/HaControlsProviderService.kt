package io.homeassistant.companion.android.controls

import android.os.Build
import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.actions.ControlAction
import android.util.Log
import androidx.annotation.RequiresApi
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

    private var updateSubscriber: Flow.Subscriber<in Control>? = null

    private val domainToHaControl = mapOf(
        "camera" to null,
        "climate" to null,
        "fan" to null,
        "light" to LightControl,
        "media_player" to null,
        "remote" to null,
        "input_boolean" to DefaultSwitchControl,
        "switch" to DefaultSwitchControl,
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
            controlIds.forEach { controlId ->
                ioScope.launch {
                    val entity = integrationRepository.getEntity(controlId)
                    val domain = entity.entityId.split(".")[0]
                    val control = domainToHaControl[domain]?.createControl(applicationContext, entity)
                    subscriber.onSubscribe(object : Flow.Subscription {
                        override fun request(n: Long) {
                            Log.d(TAG, "request $n")
                            updateSubscriber = subscriber
                        }

                        override fun cancel() {
                            Log.d(TAG, "cancel")
                            updateSubscriber = null
                        }
                    })
                    subscriber.onNext(control)
                }
            }
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
            }
        }
        if (actionSuccess) {
            consumer.accept(ControlAction.RESPONSE_OK)
        } else {
            consumer.accept(ControlAction.RESPONSE_UNKNOWN)
        }
    }
}
