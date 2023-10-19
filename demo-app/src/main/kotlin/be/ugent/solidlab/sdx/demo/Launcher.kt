package be.ugent.solidlab.sdx.demo

import be.solid.sdx.demo.queries.GetContactBasicQuery
import be.ugent.solidlab.sdx.client.lib.SolidClient
import be.ugent.solidlab.sdx.client.lib.backends.ldp.SolidLDPBackend
import be.ugent.solidlab.sdx.client.lib.backends.ldp.SolidLDPContext
import be.ugent.solidlab.sdx.client.lib.backends.ldp.StaticTargetResolver
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    SolidClient(backend = SolidLDPBackend(schemaFile = "demo-app/src/main/graphql/schema.graphqls")).use { client ->
        // Execute query
        val context =
            SolidLDPContext(resolver = StaticTargetResolver("http://localhost:3000/contacts/"))
        val response =
            client.query(GetContactBasicQuery("http://localhost:3000/contacts/contacts.ttl#jdoe"), context).execute()

        println(response.dataAssertNoErrors.contact)
    }
}
