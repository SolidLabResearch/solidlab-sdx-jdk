package be.ugent.solidlab.sdx.client.lib.backends.ldp

import be.ugent.solidlab.sdx.client.lib.backends.ldp.data.addresses
import be.ugent.solidlab.sdx.client.lib.backends.ldp.SolidLDPBackend
import be.ugent.solidlab.sdx.client.lib.backends.ldp.SolidLDPContext
import be.ugent.solidlab.sdx.client.lib.backends.ldp.StaticTargetResolver
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test

class SolidLDPBackendMutationTest {

    private val backend = SolidLDPBackend(schemaFile = "src/test/resources/graphql/schema.graphqls")
    private val targetUrl = "http://localhost:${httpServer.actualPort()}/addresses/addresses.ttl"
    private val defaultLdpContext =
        SolidLDPContext(resolver = StaticTargetResolver(targetUrl))

    companion object {

        var vertx = Vertx.vertx()
        var httpServer = vertx.createHttpServer()

        @JvmStatic
        @BeforeAll
        fun setup(): Unit = runBlocking {
            httpServer.requestHandler(MockedLDPRequestHandler())
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

    @Test
    fun testDeleteAddress(): Unit = runBlocking {
        val result = JsonObject(
            backend.execute(
                """
            mutation {
                mutateAddress(id: "${addresses.random().id}") {
                    delete {
                        id
                    }
                }
            }
        """.trimIndent(), defaultLdpContext, mapOf()
            )
        )

        println(result)
    }

    @Test
    fun testUpdateAddress(): Unit = runBlocking {
        val result = JsonObject(
            backend.execute(
                """
            mutation {
                mutateAddress(id: "${addresses.random().id}") {
                    update(input: {
                        streetLine: "Some other street 9999"
                    }) {
                        id
                    }
                }
            }
        """.trimIndent(), defaultLdpContext, mapOf()
            )
        )

        println(result)
    }

}
