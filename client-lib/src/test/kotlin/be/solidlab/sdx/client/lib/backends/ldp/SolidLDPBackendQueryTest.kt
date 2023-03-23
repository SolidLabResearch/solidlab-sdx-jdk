package be.solidlab.sdx.client.lib.backends.ldp

import be.solidlab.sdx.client.lib.backends.ldp.data.*
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.apache.http.HttpHeaders
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test
import kotlin.test.assertEquals

class SolidLDPBackendQueryTest {

    private val backend = SolidLDPBackend(schemaFile = "src/test/resources/graphql/schema.graphqls")
    private val defaultLdpContext =
        SolidLDPContext(resolver = StaticTargetResolver("http://localhost:${httpServer.actualPort()}/contacts/jdoe.ttl"))

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
    fun testBasicQuery(): Unit = runBlocking {
        val result = JsonObject(
            backend.execute(
                """
            {
              contact(id: "${contact.id}") {
                id
                givenName
                familyName
              }
            }
        """.trimIndent(), defaultLdpContext, mapOf()
            )
        )

        result.getJsonObject("data").getJsonObject("contact").apply {
            assertEquals(contact.id, this.getString("id"))
            assertEquals(contact.givenName, this.getString("givenName"))
            assertEquals(contact.familyName, this.getString("familyName"))
        }
    }

    @Test
    fun testFetchScalarCollection(): Unit = runBlocking {
        val result = JsonObject(
            backend.execute(
                """
            {
              contact(id: "${contact.id}") {
                email
              }
            }
        """.trimIndent(), defaultLdpContext, mapOf()
            )
        )

        result.getJsonObject("data").getJsonObject("contact").apply {
            assertEquals(contact.email.toSet(), this.getJsonArray("email").toSet())
        }
    }

    @Test
    fun testFetchNestedScalar(): Unit = runBlocking {
        val result = JsonObject(
            backend.execute(
                """
            {
              contact(id: "${contact.id}") {
                address {
                  streetLine
                  postalCode
                }
              }
            }
        """.trimIndent(), defaultLdpContext, mapOf()
            )
        )

        result.getJsonObject("data").getJsonObject("contact").apply {
            assertEquals(contact.address.streetLine, this.getJsonObject("address").getString("streetLine"))
            assertEquals(contact.address.postalCode, this.getJsonObject("address").getString("postalCode"))
        }
    }

    @Test
    fun testFetchMultipleContacts(): Unit = runBlocking {
        val result = JsonObject(
            backend.execute(
                """
            {
              contactCollection {
                id
                givenName
                familyName
              }
            }
        """.trimIndent(),
                SolidLDPContext(resolver = StaticTargetResolver("http://localhost:${httpServer.actualPort()}/contacts/contacts.ttl")),
                mapOf()
            )
        )

        assertEquals(
            contacts.map { Triple(it.id, it.givenName, it.familyName) }.sortedBy { it.first },
            result.getJsonObject("data").getJsonArray("contactCollection").map {
                it as JsonObject
                Triple(it.getString("id"), it.getString("givenName"), it.getString("familyName"))
            }.sortedBy { it.first })
    }

    @Test
    fun testFetchAddresses(): Unit = runBlocking {
        val result = JsonObject(
            backend.execute(
                """
            {
              addressCollection {
                streetLine
                city
                postalCode
              }
            }
        """.trimIndent(), defaultLdpContext, mapOf()
            )
        )

        assertEquals(
            listOf(contact.address, *contact.organization.map { it.address }.toTypedArray()).map {
                Triple(
                    it.streetLine,
                    it.city,
                    it.postalCode
                )
            }.sortedBy { it.first },
            result.getJsonObject("data").getJsonArray("addressCollection").map {
                it as JsonObject
                Triple(it.getString("streetLine"), it.getString("city"), it.getString("postalCode"))
            }.sortedBy { it.first })
    }

}
