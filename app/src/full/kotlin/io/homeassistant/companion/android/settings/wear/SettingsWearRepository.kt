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
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

/**
 * A lightweight server representation used exclusively for Wear OS onboarding operations.
 *
 * This class exists to avoid persisting a server in the app's database when onboarding a Wear
 * device to a Home Assistant instance that may not be registered on the phone. By keeping this
 * as a transient data structure, the server management logic remains simpler and doesn't need
 * to handle temporary or Wear-only servers.
 *
 * The tradeoff is some code duplication that already exists in
 * the integration layer, but this is acceptable to maintain clear separation of concerns.
 */
data class WearServer(
    val serverId: Int,
    val externalUrl: String,
    val cloudUrl: String?,
    val webhookId: String,
    val cloudhookUrl: String?,
    val accessToken: String?,
) {
    /**
     * Returns available base URLs for API calls, prioritizing cloud URL over external URL.
     */
    fun getBaseUrls(): List<HttpUrl> = buildList {
        cloudUrl?.toHttpUrlOrNull()?.let(::add)
        externalUrl.toHttpUrlOrNull()?.let(::add)
    }

    /**
     * Returns available webhook URLs, prioritizing cloudhook URL over the external webhook path.
     */
    fun getWebhookUrls(): List<HttpUrl> = buildList {
        cloudhookUrl?.toHttpUrlOrNull()?.let(::add)
        externalUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegments("api/webhook/$webhookId")?.build()?.let(::add)
    }
}

/**
 * Handles Home Assistant server operations specifically for Wear OS device onboarding.
 *
 * This repository is communicating directly with the Home Assistant server using [WearServer]
 * credentials without requiring a persisted server in the app's database.
 */
class SettingsWearRepository @Inject constructor(
    private val authenticationService: AuthenticationService,
    private val integrationService: IntegrationService,
) {

    /**
     * Exchanges a refresh token for a new access token and returns an updated [WearServer].
     *
     * @param server The server configuration containing the URLs to try.
     * @param refreshToken The OAuth refresh token to exchange.
     * @return A copy of [server] with the new access token populated.
     * @throws AuthorizationException If the token response is empty.
     * @throws IntegrationException If the refresh request fails.
     */
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

    /**
     * Renders a Home Assistant template string on the server.
     *
     * @param wearServer The server configuration containing the webhook URLs.
     * @param template The Jinja2 template string to render.
     * @return The rendered template result as a string, or `null` if the result is null.
     */
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

    /**
     * Fetches all entities from the Home Assistant server.
     *
     * Requires [WearServer.accessToken] to be set. Call [registerRefreshToken] first
     * to obtain an access token.
     *
     * @param wearServer The server configuration with a valid access token.
     * @return A list of all entities, or an empty list if the request fails or no access token is set.
     */
    suspend fun getEntities(wearServer: WearServer): List<Entity> {
        if (wearServer.accessToken == null) {
            FailFast.fail { "Missing access token, you should invoke registerRefreshToken first" }
            return emptyList()
        }

        return try {
            tryOnUrls(wearServer.getBaseUrls(), "get_entities") { url ->
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: IntegrationException) {
            Timber.e(e, "Failed to get entities")
            emptyList()
        }
    }
}
