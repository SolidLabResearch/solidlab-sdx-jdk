package be.ugent.solidlab.sdx.demo

import be.solid.sdx.demo.queries.CreateContactBasicMutation
import be.solid.sdx.demo.queries.DeleteContactBasicMutation
import be.solid.sdx.demo.queries.GetContactBasicQuery
import be.ugent.solidlab.sdx.client.lib.SolidClient
import be.ugent.solidlab.sdx.client.lib.backends.ldp.SolidLDPBackend
import be.ugent.solidlab.sdx.client.lib.backends.ldp.SolidLDPContext
import be.ugent.solidlab.sdx.client.lib.backends.ldp.StaticTargetResolver
import com.apollographql.apollo3.api.Optional
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    SolidClient(backend = SolidLDPBackend(schemaFile = "demo-app/src/main/graphql/schema.graphqls")).use { client ->
        // Execute query
        val context =
            SolidLDPContext(resolver = StaticTargetResolver("http://localhost:3000/contacts/"))

        // Create contact
        val createResp =
            client.mutation(
                CreateContactBasicMutation(Optional.present("jdoe"), "John", "Doe", "john.doe@gmail.com"),
                context
            ).execute()
        val contactId = createResp.dataAssertNoErrors.createContact.id
        println("Created contact with id '$contactId'")

        // Get contact
        val getResp = client.query(GetContactBasicQuery(contactId), context).execute()
        println("Retrieved contact: ${getResp.dataAssertNoErrors.contact}")

        // Delete contact
        val deleteResp = client.mutation(DeleteContactBasicMutation(contactId), context).execute()
        println(deleteResp.data)
        println(deleteResp.errors)
        println("Deleted contact with id '$contactId'")
    }
}
