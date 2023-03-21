package be.solidlab.sdx.client.lib.backends.ldp.data

import org.apache.jena.graph.Graph
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.sparql.graph.GraphFactory
import org.apache.jena.vocabulary.RDF
import java.io.StringWriter
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotations

val contact = Contact(
    "https://example.org/persons/jdoe",
    "John",
    "Doe",
    listOf("jdoe@some-company.org", "john.doe@gmail.com"),
    Address(null, "Some street 99", "Some City", "9999", "Some Country"),
    listOf(
        Organization(
            null,
            "Some Corpo",
            Address(null, "Some other street 77", "Some Other City", "7777", "Some Country")
        )
    )
)

val contacts = (1..10).map {
    Contact(
        "https://example.org/persons/jdoe$it",
        "John",
        "Doe$it",
        listOf("jdoe$it@some-company.org", "john.doe$it@gmail.com"),
        Address(null, "Some street 99$it", "Some City$it", "9999$it", "Some Country"),
        listOf(
            Organization(
                null,
                "Some Corpo$it",
                Address(null, "Some other street 77$it", "Some Other City$it", "7777$it", "Some Country")
            )
        )
    )
}


val testGraphs = mapOf(
    "/contacts/jdoe.ttl" to contact,
    "/contacts/contacts.ttl" to contacts
)

@Iri("http://schema.org/Person")
data class Contact(
    val id: String? = null,
    @Iri("http://schema.org/givenName")
    val givenName: String,
    @Iri("http://schema.org/familyName")
    val familyName: String,
    @Iri("http://schema.org/email")
    val email: Collection<String>,
    @Iri("http://schema.org/address")
    val address: Address,
    @Iri("http://schema.org/worksFor")
    val organization: Collection<Organization>
)

@Iri("http://schema.org/PostalAddress")
data class Address(
    val id: String? = null,
    @Iri("http://schema.org/streetAddress")
    val streetLine: String,
    @Iri("http://schema.org/addressLocality")
    val city: String,
    @Iri("http://schema.org/postalCode")
    val postalCode: String,
    @Iri("http://schema.org/addressCountry")
    val country: String
)

@Iri("http://schema.org/Organization")
data class Organization(
    val id: String? = null,
    @Iri("http://schema.org/name")
    val name: String,
    @Iri("http://schema.org/address")
    val address: Address
)


@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class Iri(val value: String)

fun convertToRDF(instance: Any): Graph {
    val graph = GraphFactory.createDefaultGraph()
    (if (instance is Collection<*>) instance else listOf(instance)).filterNotNull().forEach { target ->
        val subject =
            target::class.declaredMemberProperties.find { it.name == "id" }?.call(target)
                ?.let { NodeFactory.createURI(it as String) }
                ?: NodeFactory.createBlankNode()
        val className = target::class.findAnnotations(Iri::class).firstOrNull()?.value
            ?: throw IllegalArgumentException("Class ${target::class} is not annotated with @Iri")
        graph.add(subject, RDF.type.asNode(), NodeFactory.createURI(className))
        target::class.declaredMemberProperties.filter { it.annotations.any { it is Iri } }.forEach { member ->
            val predicate = NodeFactory.createURI(member.findAnnotations(Iri::class).firstOrNull()?.value)
            member.call(target).let {
                when (it) {
                    null -> emptyList<Any>()
                    is Collection<*> -> it
                    else -> listOf(it)
                }
            }.filterNotNull().forEach { propVal ->
                when (propVal) {
                    is String -> graph.add(
                        subject,
                        predicate,
                        NodeFactory.createLiteral(propVal as String)
                    )
                    // TODO: handle all literal types
                    else -> {
                        val propClassName = propVal::class.findAnnotations(Iri::class).firstOrNull()?.value
                            ?: throw IllegalArgumentException("Class ${target::class} is not annotated with @Iri")
                        val subGraph = convertToRDF(propVal)
                        val root = subGraph.find(Node.ANY, RDF.type.asNode(), NodeFactory.createURI(propClassName))
                            .next().subject
                        subGraph.stream().forEach { triple -> graph.add(triple) }
                        graph.add(subject, predicate, root)
                    }
                }
            }
        }
    }
    return graph
}
fun Graph.encodeAsTurtle(): String {
    return StringWriter().use { writer ->
        RDFDataMgr.write(writer, this, Lang.TURTLE)
        writer.toString()
    }
}

fun main() {
    println(convertToRDF( contact).encodeAsTurtle())
}
