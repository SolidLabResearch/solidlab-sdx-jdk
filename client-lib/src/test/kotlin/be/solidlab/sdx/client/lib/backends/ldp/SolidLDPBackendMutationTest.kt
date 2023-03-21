package be.solidlab.sdx.client.lib.backends.ldp

import io.vertx.core.json.JsonObject
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class SolidLDPBackendMutationTest {

    private val backend = SolidLDPBackend(schemaFile = "src/test/resources/graphql/schema.graphqls")
    private val defaultLdpContext =
        SolidLDPContext(resolver = StaticTargetResolver("http://localhost:${SolidLDPBackendQueryTest.httpServer.actualPort()}/contacts/jdoe.ttl"))

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
