package io.homeassistant.companion.android.controls

import android.app.PendingIntent
import android.os.Build
import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.DeviceTypes
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.actions.FloatAction
import android.service.controls.templates.ControlButton
import android.service.controls.templates.RangeTemplate
import android.service.controls.templates.ToggleRangeTemplate
import android.service.controls.templates.ToggleTemplate
import android.util.Log
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Flow
import java.util.function.Consumer
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.R)
class HaControlsProviderService: ControlsProviderService() {

    companion object {
        private const val TAG = "HaConProService"
    }

    @Inject
    lateinit var integrationRepository: IntegrationRepository

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    private var updateSubscriber: Flow.Subscriber<in Control>? = null

    private val domainToHaControl = mapOf<String, HaControl?>(
        "camera" to null,
        "climate" to null,
        "fan" to null,
        "light" to null,
        "media_player" to null,
        "remote" to null,
        "input_boolean" to DefaultSwitchControl,
        "switch" to DefaultSwitchControl,
        "input_number" to null
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
                        domainToHaControl[domain]?.createControl(applicationContext, it)
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
            ioScope.launch {
                integrationRepository.getEntities()
                    .filter { it.entityId in controlIds }
                    .mapNotNull {
                        val domain = it.entityId.split(".")[0]
                        domainToHaControl[domain]?.createControl(applicationContext, it)
                    }
                    .forEach {
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
                        subscriber.onNext(it)
                    }
            }
        }
    }

    override fun performControlAction(
        controlId: String,
        action: ControlAction,
        consumer: Consumer<Int>)
    {
        Log.d(TAG, "Control: $controlId, action: $action")
        val domain = controlId.split(".")[0]
        val haControl = domainToHaControl[domain]

        var actionSuccess = false
        if(haControl != null){
            runBlocking {
                actionSuccess = haControl.performAction(integrationRepository, action)

                //TODO: Make this less awful, aka make single entity call
                val entity = integrationRepository.getEntities().firstOrNull { it.entityId == controlId }
                if(entity != null) {
                    updateSubscriber?.onNext(haControl.createControl(applicationContext, entity))
                }
            }
        }
        if (actionSuccess){
            consumer.accept(ControlAction.RESPONSE_OK)
        } else {
            consumer.accept(ControlAction.RESPONSE_UNKNOWN)
        }
    }

    private fun handleFloatAction(controlId: String, floatAction: FloatAction){
        runBlocking {
            integrationRepository.callService(
                controlId.split(".")[0],
                "set_value",
                hashMapOf(
                    "entity_id" to controlId,
                    "value" to floatAction.newValue
                )
            )
        }
    }

    private fun createSliderControl(
        entity: Entity<Map<String, Any>>,
        deviceType: Int
    ): Control {
        val control = Control.StatefulBuilder(
            entity.entityId,
            PendingIntent.getActivity(
                applicationContext,
                0,
                WebViewActivity.newInstance(applicationContext),
                PendingIntent.FLAG_CANCEL_CURRENT
            )
        )
        control.setTitle(entity.entityId)
        control.setDeviceType(deviceType)
        control.setStatus(Control.STATUS_OK)
        control.setControlTemplate(
            ToggleRangeTemplate(
                entity.entityId,
                (entity.state.toFloatOrNull()?:0) == 0,
                "Description?",
                RangeTemplate(
                    entity.entityId+"_range",
                    (entity.attributes["min"] as? Number)?.toFloat() ?: 0f,
                    (entity.attributes["max"] as? Number)?.toFloat() ?: 0f,
                    entity.state.toFloatOrNull() ?: 0f,
                    (entity.attributes["step"] as? Number)?.toFloat() ?: 0f,
                    null
                )
            )
        )
        return control.build()
    }
}
