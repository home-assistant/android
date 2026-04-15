package io.homeassistant.companion.android.vehicle

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.info.CarInfo
import androidx.car.app.validation.HostValidator
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.assist.AssistActivity
import io.homeassistant.companion.android.assist.AssistAudioStrategyFactory
import io.homeassistant.companion.android.assist.AutomotiveAssistViewModel
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.AudioUrlPlayer
import io.homeassistant.companion.android.vehicle.AutomotiveAssistScreen.Factory as AutomotiveAssistScreenFactory
import java.util.Collections
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.O)
@AndroidEntryPoint
class HaCarAppService : CarAppService() {

    companion object {
        var carInfo: CarInfo? = null
            private set

        const val ACTION_NAVIGATE_TO_AUTOMOTIVE_ASSIST =
            "io.homeassistant.companion.android.vehicle.ACTION_NAVIGATE_TO_AUTOMOTIVE_ASSIST"
        const val EXTRA_SERVER = "server"
    }

    private var currentSession: MySession? = null

    private val navigationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_NAVIGATE_TO_AUTOMOTIVE_ASSIST) {
                val serverId = intent.getIntExtra(EXTRA_SERVER, ServerManager.SERVER_ID_ACTIVE)
                currentSession?.navigateToAssist(serverId)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(ACTION_NAVIGATE_TO_AUTOMOTIVE_ASSIST)
        registerReceiver(navigationReceiver, filter)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(navigationReceiver)
        currentSession = null
        carInfo = null
    }

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var prefsRepository: PrefsRepository

    @Inject
    lateinit var audioStrategyFactory: AssistAudioStrategyFactory

    @Inject
    lateinit var automotiveAssistViewModelFactory: AutomotiveAssistViewModel.Factory

    @Inject
    lateinit var automotiveAssistScreenFactory: AutomotiveAssistScreenFactory

    private val serverId = MutableStateFlow(0)
    private val allEntities = MutableStateFlow<Map<String, Entity>>(emptyMap())
    private var allEntitiesJob: Job? = null

    override fun createHostValidator(): HostValidator {
        return if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(R.array.hosts_allowlist)
                .build()
        }
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        val session = MySession()
        currentSession = session
        return session
    }

    inner class MySession : Session() {
        init {
            lifecycleScope.launch {
                serverManager.getServer()?.let {
                    loadEntities(this, it.id)
                }
            }
        }

        val serverIdFlow = serverId.asStateFlow()
        val entityFlow = allEntities.shareIn(
            lifecycleScope,
            SharingStarted.WhileSubscribed(10_000),
            1,
        )

        override fun onCreateScreen(intent: Intent): Screen {
            carInfo = carContext.getCarService(CarHardwareManager::class.java).carInfo

            if (intent.action == AssistActivity.ACTION_TRIGGER_AUTOMOTIVE_ASSIST) {
                val serverId = intent.getIntExtra(AssistActivity.EXTRA_SERVER, ServerManager.SERVER_ID_ACTIVE)

                val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

                val audioUrlPlayerInstance = AudioUrlPlayer(
                    audioManager,
                    { player ->
                        val exoPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(applicationContext).build()
                        exoPlayer.apply(player)
                        exoPlayer
                    },
                )

                val automotiveAssistViewModel = automotiveAssistViewModelFactory.create(
                    serverManager,
                    audioStrategyFactory.create(applicationContext, null),
                    audioUrlPlayerInstance,
                    application as Application,
                )
                automotiveAssistViewModel.onCreate(
                    hasPermission = ContextCompat.checkSelfPermission(
                        applicationContext,
                        android.Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED,
                    serverId = serverId,
                    pipelineId = null,
                    startListening = true,
                )

                return automotiveAssistScreenFactory.create(
                    carContext,
                    serverManager,
                    serverId,
                    audioStrategyFactory,
                    audioUrlPlayerInstance,
                    application as Application,
                    automotiveAssistViewModel,
                    lifecycleScope,
                )
            } else {
                carContext
                    .getCarService(ScreenManager::class.java).run {
                        push(
                            MainVehicleScreen(
                                carContext,
                                serverManager,
                                serverIdFlow,
                                entityFlow,
                                prefsRepository,
                                { loadEntities(lifecycleScope, it) },
                                { loadEntities(lifecycleScope, serverId.value) },
                            ),
                        )
                    }
                return LoginScreen(
                    carContext,
                    serverManager,
                )
            }
        }

        fun navigateToAssist(serverId: Int) {
            val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            val audioUrlPlayerInstance = AudioUrlPlayer(
                audioManager,
                { player ->
                    val exoPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(applicationContext).build()
                    exoPlayer.apply(player)
                    exoPlayer
                },
            )

            val automotiveAssistViewModel = automotiveAssistViewModelFactory.create(
                serverManager,
                audioStrategyFactory.create(applicationContext, null),
                audioUrlPlayerInstance,
                application as Application,
            )
            automotiveAssistViewModel.onCreate(
                hasPermission = ContextCompat.checkSelfPermission(
                    applicationContext,
                    android.Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED,
                serverId = serverId,
                pipelineId = null,
                startListening = true,
            )

            carContext.getCarService(ScreenManager::class.java).push(
                automotiveAssistScreenFactory.create(
                    carContext,
                    serverManager,
                    serverId,
                    audioStrategyFactory,
                    audioUrlPlayerInstance,
                    application as Application,
                    automotiveAssistViewModel,
                    lifecycleScope,
                ),
            )
        }
    }

    private fun loadEntities(scope: CoroutineScope, id: Int) {
        allEntitiesJob?.cancel()
        allEntitiesJob = scope.launch {
            serverId.value = id
            val entities: MutableMap<String, Entity>? =
                if (serverManager.getServer(id) != null) {
                    try {
                        serverManager.integrationRepository(id).getEntities()
                            ?.associate { it.entityId to it }
                            ?.toMutableMap()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to get entities")
                        null
                    }
                } else {
                    null
                }
            if (entities != null) {
                allEntities.value = entities.toImmutableMap()
                try {
                    serverManager.integrationRepository(id).getEntityUpdates()?.collect { entity ->
                        entities[entity.entityId] = entity
                        allEntities.value = entities.toImmutableMap()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to get entity updates")
                }
            } else {
                Timber.w("No entities found?")
                allEntities.value = emptyMap()
            }
        }
    }

    /** Returns an immutable copy of this. */
    private fun <K, V> Map<K, V>.toImmutableMap(): Map<K, V> {
        return if (isEmpty()) {
            emptyMap()
        } else {
            Collections.unmodifiableMap(LinkedHashMap(this))
        }
    }
}
