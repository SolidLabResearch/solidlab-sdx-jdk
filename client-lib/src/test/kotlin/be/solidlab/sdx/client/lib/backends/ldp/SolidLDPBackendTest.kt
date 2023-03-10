package be.solidlab.sdx.client.lib.backends.ldp

import be.solidlab.sdx.client.lib.backends.ldp.data.TestGraphs
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test
import kotlin.test.assertEquals

class SolidLDPBackendTest {

    private val backend = SolidLDPBackend("src/test/resources/graphql/schema.graphqls")
    private val ldpContext = SolidLDPContext(target = "http://localhost:${httpServer.actualPort()}/contacts/jdoe.ttl")

    companion object {

        var vertx = Vertx.vertx()
        var httpServer = vertx.createHttpServer()

        @JvmStatic
        @BeforeAll
        fun setup(): Unit = runBlocking {
            httpServer.requestHandler {
                it.response().end(
                    when (it.path()) {
                        "/contacts/jdoe.ttl" -> TestGraphs.contact
                        "/contacts/contacts.ttl" -> TestGraphs.contacts
                        else -> throw RuntimeException("Path not found!")
                    }
                )
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
    fun testBasicQuery(): Unit = runBlocking {
        val result = JsonObject(
            backend.execute(
                """
            {
              contact(id: "${TestGraphs.contactId}") {
                id
                givenName
                familyName
              }
            }
        """.trimIndent(), ldpContext, mapOf()
            )
        )

        result.getJsonObject("data").getJsonObject("contact").apply {
            assertEquals(TestGraphs.contactId, this.getString("id"))
            assertEquals(TestGraphs.contactGivenName, this.getString("givenName"))
            assertEquals(TestGraphs.contactFamilyName, this.getString("familyName"))
        }
    }

    @Test
    fun testFetchScalarCollection(): Unit = runBlocking {
        val result = JsonObject(
            backend.execute(
                """
            {
              contact(id: "${TestGraphs.contactId}") {
                email
              }
            }
        """.trimIndent(), ldpContext, mapOf()
            )
        )

        result.getJsonObject("data").getJsonObject("contact").apply {
            assertEquals(TestGraphs.contactEmail, this.getJsonArray("email").toSet())
        }
    }

    @Test
    fun testFetchNestedScalar(): Unit = runBlocking {
        val result = JsonObject(
            backend.execute(
                """
            {
              contact(id: "${TestGraphs.contactId}") {
                address {
                  streetLine
                  postalCode
                }
              }
            }
        """.trimIndent(), ldpContext, mapOf()
            )
        )

        result.getJsonObject("data").getJsonObject("contact").apply {
            assertEquals(TestGraphs.contactStreetLine, this.getJsonObject("address").getString("streetLine"))
            assertEquals(TestGraphs.contactPostalCode, this.getJsonObject("address").getString("postalCode"))
        }
    }

    @Test
    fun testFetchMultipleContacts(): Unit = runBlocking {
        val result = JsonObject(
            backend.execute(
                """
            {
              contacts {
                id
                givenName
                familyName
              }
            }
        """.trimIndent(), ldpContext, mapOf()
            )
        )

        println(result)
    }

}
