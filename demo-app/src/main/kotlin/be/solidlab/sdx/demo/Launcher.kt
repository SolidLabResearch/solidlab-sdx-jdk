package be.solidlab.sdx.demo

import be.solid.sdx.demo.queries.GetContactBasicQuery
import be.solidlab.sdx.client.lib.SolidClient
import be.solidlab.sdx.client.lib.backends.ldp.SolidLDPBackend
import be.solidlab.sdx.client.lib.backends.ldp.SolidLDPContext
import be.solidlab.sdx.client.lib.backends.ldp.StaticTargetResolver
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

fun main(): Unit = runBlocking {
    SolidClient(backend = SolidLDPBackend(schemaFile = "demo-app/src/main/graphql/schema.graphqls")).use { client ->
        // Execute query
        val context =
            SolidLDPContext(resolver = StaticTargetResolver("http://localhost:3000/contacts/"))
        val response =
            client.query(GetContactBasicQuery("http://localhost:3000/contacts/jdoe.ttl#jdoe"), context).execute()

        println(response.dataAssertNoErrors.contact)
    }
}
