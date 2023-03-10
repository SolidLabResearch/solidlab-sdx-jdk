package be.solidlab.sdx.client.lib.backends.ldp.data

object TestGraphs {

    const val contactId = "https://example.com/persons/jdoe"
    const val contactGivenName = "John"
    const val contactFamilyName = "Doe"
    const val contactStreetLine = "Some Road 99"
    const val contactPostalCode = "99999"
    val contactEmail = setOf("john.doe@gmail.com", "john.doe@somecorp.org")

    val contact = """
        @prefix schema: <http://schema.org/> .

        <$contactId>
          a schema:Person ;
          schema:givenName "$contactGivenName" ;
          schema:familyName "$contactFamilyName" ;
          schema:email ${contactEmail.joinToString(", ") { "\"$it\"" }} ;
          schema:address [
            a schema:PostalAddress ;
            schema:streetAddress "$contactStreetLine" ;
            schema:addressLocality "Random City" ;
            schema:postalCode "$contactPostalCode" ;
            schema:addressCountry "Country"
          ] ;
          schema:worksFor [
            a schema:Organization ;
            schema:name "My Organization" ;
            schema:address [
              a schema:PostalAddress ;
              schema:streetAddress "Some Other Road 999" ;
              schema:addressLocality "Random City" ;
              schema:postalCode "88888" ;
              schema:addressCountry "Country"
            ]
          ] .
    """.trimIndent()

    val contacts = """
        
        
    """.trimIndent()

}
