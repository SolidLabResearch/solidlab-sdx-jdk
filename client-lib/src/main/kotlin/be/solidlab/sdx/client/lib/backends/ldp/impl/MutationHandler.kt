package be.solidlab.sdx.client.lib.backends.ldp.impl

import be.solidlab.sdx.client.commons.ldp.LdpClient
import be.solidlab.sdx.client.commons.ldp.ResourceType
import be.solidlab.sdx.client.lib.backends.ldp.SolidLDPContext
import be.solidlab.sdx.client.lib.backends.ldp.TargetResolverContext
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLTypeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.apache.jena.graph.Graph
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.sparql.graph.GraphFactory
import java.net.URL
import java.util.*
import java.util.concurrent.CompletionStage

private const val ID_FIELD = "id"
private const val SLUG_FIELD = "slug"

class MutationHandler(private val ldpClient: LdpClient) {
    fun handleMutationEntrypoint(runtimeEnv: DataFetchingEnvironment): CompletionStage<Any?> =
        CoroutineScope(Dispatchers.IO).future {
            if (runtimeEnv.field.name.startsWith("create")) {
                handleCreateMutation(runtimeEnv)
            } else {
                TODO()
            }
        }

    private suspend fun handleCreateMutation(runtimeEnv: DataFetchingEnvironment): Any? {
        val classUri = getTypeClassURI(runtimeEnv.fieldType)
        val targetUrl = runtimeEnv.getLocalContext<SolidLDPContext>().resolver.resolve(
            classUri.uri.toString(),
            object : TargetResolverContext {})
        val input: Map<String, Any?> = runtimeEnv.getArgument("input")
        return if (targetUrl != null) {
            val id = getNewInstanceID(input)
            val content = generateTriplesForInput(
                id,
                input,
                GraphQLTypeUtil.unwrapNonNull(runtimeEnv.fieldDefinition.getArgument("input").type) as GraphQLInputObjectType
            )
            when (ldpClient.fetchResourceType(targetUrl)) {
                ResourceType.DOCUMENT -> {
                    // Append triples to doc using patch
                    ldpClient.patchDocument(targetUrl, content)
                    IntermediaryResult(targetUrl, content, id)
                }

                ResourceType.CONTAINER -> {
                    // Post triples as new document in the container
                    val newDocumentURL = getNewDocumentURL(targetUrl, input)
                    ldpClient.putDocument(newDocumentURL, content)
                    IntermediaryResult(newDocumentURL, content, id)
                }
            }
        } else {
            throw RuntimeException("A target URL for this request could not be resolved!")
        }
    }

    private fun generateTriplesForInput(
        subject: Node,
        input: Map<String, Any?>,
        inputDefinition: GraphQLInputObjectType
    ): Graph {
        val resultGraph = GraphFactory.createDefaultGraph()
        inputDefinition.fields.filter { it.name != "slug" && it.name != "id" }.forEach { field ->
            input[field.name]?.let { literalVal ->
                resultGraph.add(
                    subject,
                    NodeFactory.createURI(
                        field.getAppliedDirective("property").getArgument("iri")
                            .getValue<String>().removeSurrounding("<", ">")
                    ), NodeFactory.createLiteral(literalVal.toString())
                )
            }
        }
        return resultGraph
    }

    private fun getNewInstanceID(input: Map<String, Any?>): Node {
        return NodeFactory.createURI(input[ID_FIELD]?.toString() ?: "#${input[SLUG_FIELD] ?: UUID.randomUUID()}")
    }

    private fun getNewDocumentURL(targetUrl: URL, input: Map<String, Any?>): URL {
        val id = input[ID_FIELD]?.toString()
        val slug = input[SLUG_FIELD]?.toString()
        return if (id != null && id.startsWith(targetUrl.toString())) {
            URL(id.removeSuffix(".ttl").plus(".ttl"))
        } else if (slug != null) {
            URL("${targetUrl.toString().removeSuffix("/")}/$slug.ttl")
        } else {
            URL("${targetUrl.toString().removeSuffix("/")}/${UUID.randomUUID()}.ttl")
        }
    }
}
