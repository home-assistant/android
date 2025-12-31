package io.homeassistant.companion.android.settings.wear

import io.homeassistant.companion.android.common.data.authentication.AuthorizationException
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService.Companion.SEGMENT_AUTH_TOKEN
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationException
import io.homeassistant.companion.android.common.data.integration.impl.IntegrationService
import io.homeassistant.companion.android.common.data.integration.impl.entities.RenderTemplateIntegrationRequest
import io.homeassistant.companion.android.common.data.integration.impl.entities.Template
import io.homeassistant.companion.android.common.data.servers.tryOnUrls
import io.homeassistant.companion.android.common.util.FailFast
import javax.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

data class WearServer(
    val externalUrl: String,
    val cloudUrl: String?,
    val webhookId: String,
    val cloudhookUrl: String?,
    val accessToken: String?,
) {
    fun getBaseUrls(): List<HttpUrl> = buildList {
        cloudUrl?.toHttpUrlOrNull()?.let(::add)
        externalUrl.toHttpUrlOrNull()?.let(::add)
    }
    fun getWebhookUrls(): List<HttpUrl> = buildList {
        cloudhookUrl?.toHttpUrlOrNull()?.let(::add)
        externalUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegments("/api/webhook/$webhookId")?.build()?.let(::add)
    }
}

class SettingsWearUseCase @Inject constructor(
    private val authenticationService: AuthenticationService,
    private val integrationService: IntegrationService,
) {
    suspend fun registerRefreshToken(server: WearServer, refreshToken: String): WearServer {
        return tryOnUrls(server.getBaseUrls(), "refresh_token") {
            val response = authenticationService.refreshToken(
                it.newBuilder().addPathSegments(SEGMENT_AUTH_TOKEN).build(),
                AuthenticationService.GRANT_TYPE_REFRESH,
                refreshToken,
                AuthenticationService.CLIENT_ID,
            )
            if (response.isSuccessful) {
                val refreshedToken = response.body() ?: throw AuthorizationException()
                server.copy(accessToken = refreshedToken.accessToken)
            } else {
                throw IntegrationException(
                    "Error calling refresh token",
                    response.code(),
                    response.errorBody(),
                )
            }
        }
    }

    suspend fun renderTemplate(wearServer: WearServer, template: String): String? {
        val templateResult = tryOnUrls(
            wearServer.getWebhookUrls(),
            "render_template",
        ) { url ->
            integrationService.getTemplate(
                url,
                RenderTemplateIntegrationRequest(
                    mapOf("template" to Template(template, emptyMap())),
                ),
            )["template"]
        }
        // We check if the result is a JsonPrimitive instead of a simple global toString to avoid rendering " around the string
        return if (templateResult is JsonPrimitive) templateResult.contentOrNull else templateResult.toString()
    }

    suspend fun getEntities(wearServer: WearServer): List<Entity> {
        if (wearServer.accessToken == null) {
            FailFast.fail { "Missing access token you should invoke registerRefreshToken first" }
            return emptyList()
        }

        return try {
            tryOnUrls(wearServer.getWebhookUrls(), "get_entities") { url ->
                integrationService.getStates(
                    url.newBuilder().addPathSegments("api/states").build(),
                    "Bearer ${wearServer.accessToken}",
                )
            }.map {
                Entity(
                    it.entityId,
                    it.state,
                    it.attributes,
                    it.lastChanged,
                    it.lastUpdated,
                )
            }
        } catch (e: IntegrationException) {
            Timber.e(e, "Fail to get entities")
            emptyList()
        }
    }
}
