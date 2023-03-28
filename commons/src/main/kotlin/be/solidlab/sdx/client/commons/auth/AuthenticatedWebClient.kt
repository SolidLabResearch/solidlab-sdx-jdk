package be.solidlab.sdx.client.commons.auth

import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.auth.User
import io.vertx.ext.auth.oauth2.OAuth2Options
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.ext.web.client.impl.ClientPhase
import io.vertx.ext.web.client.impl.HttpContext
import io.vertx.ext.web.client.impl.WebClientBase
import io.vertx.ext.web.client.impl.WebClientInternal

/**
 * Extension of a Vert.x WebClient, taking care of authentication using an interceptor.
 * The authentication server and credentials are sourced from an OAuth2Options instance that must be provided when
 * creating an instance.
 */
class AuthenticatedWebClient private constructor(httpClient: HttpClient, private val oAuth2Options: OAuth2Options) :
    WebClientBase(httpClient, WebClientOptions()), Handler<HttpContext<*>> {

    private val authClient = WebClient.wrap(httpClient, WebClientOptions())
    private var user: User? = null
    private val dejaVu = mutableSetOf<HttpContext<*>>()

    companion object {

        fun create(vertx: Vertx, oAuth2Options: OAuth2Options): AuthenticatedWebClient {
            val httpClient = vertx.createHttpClient()
            val instance = AuthenticatedWebClient(httpClient, oAuth2Options)
            (instance as WebClientInternal).addInterceptor(instance)
            return instance
        }

    }

    override fun handle(ctx: HttpContext<*>) {
        when (ctx.phase()) {
            ClientPhase.CREATE_REQUEST -> createRequest(ctx).onFailure(ctx::fail).onSuccess { ctx.next() }
            ClientPhase.DISPATCH_RESPONSE -> processResponse(ctx)
            else -> ctx.next()
        }
    }

    private fun createRequest(ctx: HttpContext<*>): Future<Void> {
        val promise = Promise.promise<Void>()
        user?.let { nonNullUser ->
            ctx.requestOptions().putHeader(
                HttpHeaders.AUTHORIZATION.toString(),
                "Bearer " + nonNullUser.principal().getString("access_token")
            )
            promise.complete()
        } ?: authenticate()
            .onSuccess { userResult ->
                user = userResult
                ctx.requestOptions().putHeader(
                    HttpHeaders.AUTHORIZATION.toString(),
                    "Bearer " + userResult.principal().getString("access_token")
                )
                promise.complete()
            }
            .onFailure(promise::fail)
        return promise.future()
    }

    private fun processResponse(ctx: HttpContext<*>) {
        when (ctx.response().statusCode()) {
            401 -> if (dejaVu.contains(ctx)) {
                dejaVu.remove(ctx)
                ctx.next()
            } else {
                dejaVu.add(ctx)
                authenticate()
                    .onSuccess { userResult ->
                        user = userResult
                        ctx.createRequest(ctx.requestOptions())
                    }
                    .onFailure { err ->
                        dejaVu.remove(ctx)
                        user = null
                        ctx.fail(err)
                    }
            }

            else -> {
                dejaVu.remove(ctx)
                ctx.next()
            }
        }
    }

    private fun authenticate(): Future<User> {
        return authClient.postAbs("${oAuth2Options.site.removeSuffix("/")}/${oAuth2Options.tokenPath.removePrefix("/")}")
            .basicAuthentication(oAuth2Options.clientId, oAuth2Options.clientSecret)
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/x-www-form-urlencoded")
            .sendBuffer(Buffer.buffer("grant_type=client_credentials&scope=webid"))
            .map {
                println(it.bodyAsString())
                User.create(it.bodyAsJsonObject())
            }
    }

}

