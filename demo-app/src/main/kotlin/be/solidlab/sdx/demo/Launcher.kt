package be.solidlab.sdx.demo

import be.solid.sdx.demo.queries.GetContactBasicQuery
import be.solidlab.sdx.client.lib.SolidClient
import be.solidlab.sdx.client.lib.backends.ldp.SolidLDPBackend
import be.solidlab.sdx.client.lib.backends.ldp.SolidLDPContext
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val client = SolidClient.using(SolidLDPBackend()).build()

    // Execute query
    val context = SolidLDPContext(target = "http://localhost:3000/contacts/wkerckho.ttl")
    val response = client.query(GetContactBasicQuery("https://example.com/persons/wkerckho"), context).execute()

    println(response.dataAssertNoErrors)
}
