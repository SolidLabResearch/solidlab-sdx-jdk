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
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test

class SolidLDPBackendMutationTest {

    private val backend = SolidLDPBackend(schemaFile = "src/test/resources/graphql/schema.graphqls")
    private val targetUrl = "http://localhost:${httpServer.actualPort()}/contacts/jdoe.ttl"
    private val defaultLdpContext =
        SolidLDPContext(resolver = StaticTargetResolver(targetUrl))

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
                    HttpMethod.PATCH -> request.response().setStatusCode(204).end()
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
    fun testCreateAddress(): Unit = runBlocking {
        val slug = "test-address"
        val streetLine = "Some Street 99"
        val city = "Some City"
        val postalCode = "9999"
        val country = "Some Country"
        val result = JsonObject(
            backend.execute(
                """
            mutation {
                createAddress(input: {
                    slug: "$slug"
                    streetLine: "$streetLine"
                    city: "$city"
                    postalCode: "$postalCode"
                    country: "$country"
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

        result.getJsonObject("data").getJsonObject("createAddress").apply {
            Assertions.assertEquals("$targetUrl#$slug", this.getString("id"))
            Assertions.assertEquals(streetLine, this.getString("streetLine"))
            Assertions.assertEquals(city, this.getString("city"))
            Assertions.assertEquals(postalCode, this.getString("postalCode"))
            Assertions.assertEquals(country, this.getString("country"))
        }
    }

}
