package io.homeassistant.companion.android.widgets.camera

import android.graphics.Bitmap
import io.homeassistant.companion.android.common.data.integration.entityPicturePath
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.database.widget.CameraWidgetDao
import io.homeassistant.companion.android.database.widget.CameraWidgetEntity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import timber.log.Timber

private val STATES_WITHOUT_IMAGE = setOf(STATE_UNAVAILABLE, STATE_UNKNOWN)

internal sealed interface CameraWidgetState {
    data object Empty : CameraWidgetState
    data object Loading : CameraWidgetState
    data class Content(val bitmap: Bitmap?, val error: Boolean, val widget: CameraWidgetEntity, val revision: Long) :
        CameraWidgetState
}

/** Loads authenticated server images and emits refreshes to active Glance compositions. */
@Singleton
internal class CameraWidgetStateUpdater @Inject constructor(
    private val dao: CameraWidgetDao,
    private val serverManager: ServerManager,
    private val imageLoader: CameraWidgetImageLoader,
) {
    private val refreshIds = MutableSharedFlow<RefreshSignal>(extraBufferCapacity = 64)
    private val activeWidgetCompositions = ConcurrentHashMap<Int, AtomicInteger>()

    /** Requests a refresh for an installed widget without keeping a job or image cache per widget. */
    fun refreshFromEntity(widgetId: Int, state: String?) {
        emitIfComposing(RefreshSignal(widgetId, observedState = state))
    }

    /** Requests refreshes for all widgets after configuration, host refresh, or screen wake. */
    fun refreshAll(widgetIds: IntArray, force: Boolean) {
        widgetIds.forEach { emitIfComposing(RefreshSignal(it, force = force)) }
    }

    /** Requests the configured manual refresh action. */
    fun refresh(widgetId: Int) {
        emitIfComposing(RefreshSignal(widgetId, force = true))
    }

    /** Marks the last publication as failed so the next equal entity event retries rendering. */
    fun compositionError(widgetId: Int) {
        emitIfComposing(RefreshSignal(widgetId, publicationError = true))
    }

    private fun emitIfComposing(signal: RefreshSignal) {
        if ((activeWidgetCompositions[signal.widgetId]?.get() ?: 0) > 0) refreshIds.tryEmit(signal)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun stateFlow(widgetId: Int): Flow<CameraWidgetState> = flow {
        val activeCount = activeWidgetCompositions.compute(widgetId) { _, current ->
            (current ?: AtomicInteger()).apply { incrementAndGet() }
        }!!
        try {
            stateFlowForWidget(widgetId).collect { emit(it) }
        } finally {
            activeWidgetCompositions.compute(widgetId) { _, current ->
                if (current !== activeCount) return@compute current
                current.decrementAndGet()
                current.takeIf { it.get() > 0 }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun stateFlowForWidget(widgetId: Int): Flow<CameraWidgetState> = dao.getAllFlow()
        .map { widgets -> widgets.firstOrNull { it.id == widgetId } }
        .distinctUntilChanged()
        .flatMapLatest { widget ->
            if (widget == null) {
                flowOf<CameraWidgetState>(CameraWidgetState.Empty)
            } else {
                flow<CameraWidgetState> {
                    var lastBitmap: Bitmap? = null
                    var lastDrawnState: String? = null
                    var presentationRevision = 0L
                    var retryPublication = false
                    emit(CameraWidgetState.Loading)
                    merge(
                        flowOf(RefreshSignal(widgetId, force = true)),
                        refreshIds.asSharedFlow().filter { it.widgetId == widgetId },
                    ).collect { signal ->
                        if (signal.publicationError) {
                            retryPublication = true
                            return@collect
                        }
                        if (signal.observedState in STATES_WITHOUT_IMAGE) return@collect
                        val isCamera = widget.entityId.substringBefore('.') == "camera"
                        if (!isCamera &&
                            signal.observedState != null &&
                            signal.observedState == lastDrawnState &&
                            lastBitmap != null
                        ) {
                            if (retryPublication) {
                                retryPublication = false
                                presentationRevision++
                                emit(
                                    CameraWidgetState.Content(
                                        bitmap = lastBitmap,
                                        error = false,
                                        widget = widget,
                                        revision = presentationRevision,
                                    ),
                                )
                            }
                            return@collect
                        }
                        val result = loadImage(widget, lastDrawnState, lastBitmap, signal.force || isCamera)
                        if (result.bitmap != null) {
                            lastBitmap = result.bitmap
                            lastDrawnState = result.state
                        }
                        retryPublication = false
                        presentationRevision++
                        emit(CameraWidgetState.Content(lastBitmap, result.error, widget, presentationRevision))
                    }
                }
            }
        }
        .onStart { emit(CameraWidgetState.Loading) }
        .catch { error ->
            if (error is CancellationException) throw error
            Timber.e(error, "Unable to load camera widget $widgetId")
            emit(CameraWidgetState.Empty)
        }

    private suspend fun loadImage(
        widget: CameraWidgetEntity,
        lastDrawnState: String?,
        lastBitmap: Bitmap?,
        force: Boolean,
    ): ImageResult {
        if (serverManager.getServer(widget.serverId) == null) return ImageResult(error = true)
        return try {
            val entity = serverManager.integrationRepository(widget.serverId).getEntity(widget.entityId)
            val state = entity?.state
            if (state == null) return ImageResult(error = true)
            if (state == STATE_UNAVAILABLE || state == STATE_UNKNOWN) return ImageResult(state = state)
            if (!force && lastBitmap != null && state == lastDrawnState) return ImageResult(state = state)
            val urlState = serverManager.connectionStateProvider(widget.serverId).urlFlow().first()
            check(urlState is UrlState.HasUrl) { "No URL available to retrieve picture" }
            val imagePath = entity.entityPicturePath() ?: return ImageResult(error = true)
            val baseUrl = urlState.url?.toString()?.removeSuffix("/").orEmpty()
            val bitmap = imageLoader.fetch("$baseUrl$imagePath", widget.id)
            ImageResult(bitmap = bitmap, error = bitmap == null, state = state)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.e(error, "Unable to fetch camera image for widget ${widget.id}")
            ImageResult(error = true)
        }
    }

    private data class ImageResult(val bitmap: Bitmap? = null, val error: Boolean = false, val state: String? = null)

    private data class RefreshSignal(
        val widgetId: Int,
        val observedState: String? = null,
        val force: Boolean = false,
        val publicationError: Boolean = false,
    )
}
