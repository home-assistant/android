package io.homeassistant.companion.android.controls

import android.app.PendingIntent
import android.os.Build
import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.DeviceTypes
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.templates.ControlButton
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

    override fun onCreate() {
        super.onCreate()

        DaggerControlsComponent.builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)
    }

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> {
        return Flow.Publisher { flow ->
            ioScope.launch {
                integrationRepository
                    .getEntities()
                    .mapNotNull { createControl(it) }
                    .forEach { flow.onNext(it) }
                flow.onComplete()
            }
        }
    }

    override fun createPublisherFor(controlIds: MutableList<String>): Flow.Publisher<Control> {
        Log.d(TAG, "publisherFor $controlIds")
        return Flow.Publisher { subscriber ->
            ioScope.launch {
                integrationRepository.getEntities()
                    .filter { it.entityId in controlIds }
                    .mapNotNull { createControl(it) }
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
        when (action) {
            is BooleanAction -> {
                handleToggle(controlId, action)
            }
            else -> {
                Log.e(TAG, "Not handling $controlId action!")
                consumer.accept(ControlAction.RESPONSE_OK)
            }
        }
        runBlocking {
            //TODO: Make this less awful, aka make single entity call
            integrationRepository.getEntities().firstOrNull { it.entityId == controlId }?.let {
                updateSubscriber?.onNext(createSwitchControl(it))
            }
        }
    }

    private fun handleToggle(controlId: String, booleanAction: BooleanAction) {
        runBlocking {
            integrationRepository.callService(
                controlId.split(".")[0],
                if(booleanAction.newState) "turn_on" else "turn_off",
                hashMapOf("entity_id" to controlId)
            )
        }
    }

    private fun createControl(entity: Entity<Any>): Control? {
        return when (entity.entityId.split(".")[0]) {
            "camera",
            "climate",
            "fan",
            "input_boolean",
            "light",
            "media_player",
            "remote",
            "switch" -> createSwitchControl(entity)
            else -> null
        }
    }

    private fun createSwitchControl(entity: Entity<Any>): Control {
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
        control.setDeviceType(DeviceTypes.TYPE_SWITCH)
        control.setStatus(Control.STATUS_OK)
        control.setControlTemplate(
            ToggleTemplate(
                entity.entityId,
                ControlButton(
                    entity.state == "on",
                    "Description")
            )
        )
        return control.build()
    }
}
