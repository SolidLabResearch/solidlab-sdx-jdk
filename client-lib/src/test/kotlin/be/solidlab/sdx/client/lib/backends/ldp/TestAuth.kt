package be.solidlab.sdx.client.lib.backends.ldp

import io.vertx.core.Vertx
import io.vertx.core.http.HttpClientOptions
import io.vertx.ext.auth.JWTOptions
import io.vertx.ext.auth.PubSecKeyOptions
import io.vertx.ext.auth.authentication.UsernamePasswordCredentials
import io.vertx.ext.auth.oauth2.OAuth2Auth
import io.vertx.ext.auth.oauth2.OAuth2FlowType
import io.vertx.ext.auth.oauth2.OAuth2Options
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth
import io.vertx.ext.web.client.OAuth2WebClient
import io.vertx.ext.web.client.WebClient
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import java.security.KeyPairGenerator


val clientId = "test-token_a822df68-0f27-442f-bc45-f50417b1111e"
val clientSecret =
    "3c2e39a93611514b37f50434a3970c281548029e970a6d9bef00f975bb9b70e0099302ddd17bb5fb58b8869240f692504dbfc9676529c30fafa5ed6eed04cb2c"

fun main() = runBlocking {
    val vertx = Vertx.vertx()
    val webClient = WebClient.create(vertx)
//    val oauth = KeycloakAuth.discover(
//        vertx,
//        OAuth2Options().setSite("http://localhost:3000/").setClientId(clientId).setClientSecret(clientSecret).addSupportedGrantType("client_credentials")
//    ).toCompletionStage().await()
//    val client =
//        OAuth2WebClient.create(webClient, oauth).withCredentials(UsernamePasswordCredentials(clientId, clientSecret))

    val client = OAuth2WebClient.create(
        webClient,
        OAuth2Auth.create(
            vertx, OAuth2Options().setSite("http://localhost:3000").setTokenPath("/.oidc/token").setClientId(
                clientId
            ).setClientSecret(clientSecret).setFlow(OAuth2FlowType.PASSWORD)
        )
    ).withCredentials(
        UsernamePasswordCredentials(
            clientId, clientSecret
        )
    )


    val resp = client.getAbs("http://localhost:3000/private/profile/").send().toCompletionStage().await()
    println(resp.bodyAsString())
}
