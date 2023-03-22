package be.solidlab.sdx.demo

import be.solid.sdx.demo.queries.GetContactBasicQuery
import be.solidlab.sdx.client.lib.SolidClient
import be.solidlab.sdx.client.lib.backends.ldp.SolidLDPBackend
import be.solidlab.sdx.client.lib.backends.ldp.SolidLDPContext
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

fun main(): Unit = runBlocking {
    val client = SolidClient(backend = SolidLDPBackend())

    // Execute query
    val context = SolidLDPContext(target = "https://cloud.ilabt.imec.be/index.php/s/Sb9oJ5YKX4DXaMo/download/jdoe.ttl")
    val response = client.query(GetContactBasicQuery("https://example.org/persons/jdoe"), context).execute()

    println(response.dataAssertNoErrors.contact)
    exitProcess(0)
}
