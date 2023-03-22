package be.solidlab.sdx.client.lib.backends.ldp

import be.solidlab.sdx.client.lib.backends.ldp.data.convertToRDF
import be.solidlab.sdx.client.lib.backends.ldp.data.encodeAsTurtle
import be.solidlab.sdx.client.lib.backends.ldp.data.testGraphs
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.apache.http.HttpHeaders
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test

class SolidLDPBackendMutationTest {

    private val backend = SolidLDPBackend(schemaFile = "src/test/resources/graphql/schema.graphqls")
    private val defaultLdpContext =
        SolidLDPContext(resolver = StaticTargetResolver("http://localhost:${httpServer.actualPort()}/contacts/jdoe.ttl"))

    companion object {

        var vertx = Vertx.vertx()
        var httpServer = vertx.createHttpServer()

        @JvmStatic
        @BeforeAll
        fun setup(): Unit = runBlocking {
            httpServer.requestHandler { request ->
                when (request.method()) {
                    HttpMethod.HEAD -> request.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/turtle")
                        .putHeader("Link", "\t<http://www.w3.org/ns/ldp#Resource>; rel=\"type\"").end()

                    HttpMethod.POST -> request.response().setStatusCode(201).end()
                    HttpMethod.PUT -> request.response().setStatusCode(204).end()
                    else -> request.response().setStatusCode(500).end("Unexpected request")
                }
            }
            httpServer.listen(0).toCompletionStage().await()
        }

        @JvmStatic
        @AfterAll
        fun teardown(): Unit = runBlocking {
            httpServer.close().toCompletionStage().await()
        }

    }

    @Test
    fun testCreateAddress() = runBlocking {
        val result = JsonObject(
            backend.execute(
                """
            mutation {
                createAddress(input: {
                    slug: "test-address"
                    streetLine: "Some Street 99"
                    city: "Some City"
                    postalCode: "9999"
                    country: "Some Country"
                }) {
                  id
                  streetLine
                  city
                  postalCode
                  country
                }
            }
        """.trimIndent(), defaultLdpContext, mapOf()
            )
        )
        println(result)
    }

}
