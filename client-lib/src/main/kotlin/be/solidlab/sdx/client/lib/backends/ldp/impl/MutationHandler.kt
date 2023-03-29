package be.solidlab.sdx.client.lib.backends.ldp.impl

import be.solidlab.sdx.client.commons.graphql.isCollection
import be.solidlab.sdx.client.commons.graphql.unwrapNonNull
import be.solidlab.sdx.client.commons.ldp.LdpClient
import be.solidlab.sdx.client.commons.ldp.ResourceType
import be.solidlab.sdx.client.commons.linkeddata.add
import be.solidlab.sdx.client.commons.linkeddata.remove
import be.solidlab.sdx.client.lib.backends.ldp.SolidLDPContext
import be.solidlab.sdx.client.lib.backends.ldp.TargetResolverContext
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.apache.jena.graph.Graph
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.sparql.graph.GraphFactory
import org.apache.jena.vocabulary.RDF
import java.net.URL
import java.util.*
import java.util.concurrent.CompletionStage

private const val ID_FIELD = "id"
private const val SLUG_FIELD = "slug"

class MutationHandler(private val ldpClient: LdpClient) {
    fun handleMutationEntrypoint(runtimeEnv: DataFetchingEnvironment): CompletionStage<Any?> =
        CoroutineScope(Dispatchers.IO).future {
            val fieldName = runtimeEnv.field.name
            when {
                fieldName.startsWith("create") -> handleCreateMutation(runtimeEnv)
                fieldName.startsWith("mutate") -> handleGetMutateObjectType(runtimeEnv)
                fieldName == "update" -> handleUpdateMutation(runtimeEnv)
                fieldName == "delete" -> handleDeleteMutation(runtimeEnv)
                fieldName.startsWith("set") -> TODO()
                fieldName.startsWith("clear") -> TODO()
                fieldName.startsWith("add") -> TODO()
                fieldName.startsWith("remove") -> TODO()
                fieldName.startsWith("link") -> TODO()
                fieldName.startsWith("unlink") -> TODO()
                else -> throw UnsupportedOperationException()
            }
        }

    private suspend fun handleCreateMutation(runtimeEnv: DataFetchingEnvironment): IntermediaryResult {
        val classUri = getTypeClassURI(runtimeEnv.fieldType)
        val targetUrl = runtimeEnv.getLocalContext<SolidLDPContext>().resolver.resolve(
            classUri.uri.toString(), TargetResolverContext(ldpClient)
        )
        val input: Map<String, Any?> = runtimeEnv.getArgument("input")
        return if (targetUrl != null) {
            val resourceType = ldpClient.fetchResourceType(targetUrl)
            val id = getNewInstanceID(input, resourceType)
            val content = generateTriplesForInput(
                id,
                input,
                GraphQLTypeUtil.unwrapNonNull(runtimeEnv.fieldDefinition.getArgument("input").type) as GraphQLInputObjectType,
                classUri
            )
            when (resourceType) {
                ResourceType.DOCUMENT -> {
                    // Append triples to doc using patch
                    ldpClient.patchDocument(targetUrl, content)
                    IntermediaryResult(targetUrl, resourceType, content, id)
                }

                ResourceType.CONTAINER -> {
                    // Post triples as new document in the container
                    val newDocumentURL = getNewDocumentURL(targetUrl, input)
                    ldpClient.putDocument(newDocumentURL, content)
                    IntermediaryResult(newDocumentURL, resourceType, content, id)
                }
            }
        } else {
            throw RuntimeException("A target URL for this request could not be resolved!")
        }
    }

    private suspend fun handleGetMutateObjectType(runtimeEnv: DataFetchingEnvironment): IntermediaryResult? {
        val classUri = getTypeClassURI(runtimeEnv.fieldType)
        val targetUrl = runtimeEnv.getLocalContext<SolidLDPContext>().resolver.resolve(
            classUri.uri.toString(),
            TargetResolverContext(ldpClient)
        )
        return if (targetUrl != null) {
            val resourceType = ldpClient.fetchResourceType(targetUrl)
            getInstanceById(ldpClient, targetUrl, runtimeEnv.getArgument("id"), classUri, resourceType)
        } else {
            throw RuntimeException("A target URL for this request could not be resolved!")
        }
    }

    private suspend fun handleDeleteMutation(runtimeEnv: DataFetchingEnvironment): IntermediaryResult? {
        val source = runtimeEnv.getSource<IntermediaryResult>()
        when (source.resourceType) {
            ResourceType.CONTAINER -> ldpClient.deleteDocument(URL(source.getFQSubject()))
            ResourceType.DOCUMENT -> ldpClient.patchDocument(source.requestUrl, deletes = source.documentGraph)
        }
        return source
    }

    private suspend fun handleUpdateMutation(runtimeEnv: DataFetchingEnvironment): Any {
        val source = runtimeEnv.getSource<IntermediaryResult>()
        val inputType = runtimeEnv.fieldDefinition.getArgument("input").type.unwrapNonNull() as GraphQLInputObjectType
        // Get object type associated with input type
        val objType =
            runtimeEnv.graphQLSchema.getObjectType(inputType.name.removePrefix("Update").removeSuffix("Input"))
        val (insertTriples, deleteTriples) = generateTriplesForUpdate(source, runtimeEnv.getArgument("input"), objType)
        ldpClient.patchDocument(source.requestUrl, insertTriples, deleteTriples)
        val updatedGraph =
            GraphFactory.createDefaultGraph().add(source.documentGraph).remove(deleteTriples).add(insertTriples)
        return IntermediaryResult(source.requestUrl, source.resourceType, updatedGraph, source.subject)
    }

    private fun generateTriplesForInput(
        subject: Node,
        input: Map<String, Any?>,
        inputDefinition: GraphQLInputObjectType,
        classUri: Node
    ): Graph {
        val resultGraph = GraphFactory.createDefaultGraph()
        resultGraph.add(subject, RDF.type.asNode(), classUri)
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

    private fun generateTriplesForUpdate(
        sourceContext: IntermediaryResult,
        input: Map<String, Any?>,
        objectTypeDefinition: GraphQLObjectType
    ): Pair<Graph, Graph> {
        val deleteGraph = GraphFactory.createDefaultGraph()
        val updateGraph = GraphFactory.createDefaultGraph()
        input.forEach { (fieldName, value) ->
            val fieldDef = objectTypeDefinition.getFieldDefinition(fieldName)
            val propertyUri = NodeFactory.createURI(
                fieldDef.getAppliedDirective("property").getArgument("iri")
                    .getValue<String>().removeSurrounding("<", ">")
            )
            if (value == null) {
                // Check if this field may be null
                require(!GraphQLTypeUtil.isNonNull(fieldDef.type)) { "Update input provided null value for non-nullable field '$fieldName'" }
                sourceContext.documentGraph.find(sourceContext.subject, propertyUri, Node.ANY)
                    .forEach { deleteGraph.add(it) }
            } else {
                sourceContext.documentGraph.find(sourceContext.subject, propertyUri, Node.ANY)
                    .forEach { deleteGraph.add(it) }
                if (fieldDef.type.isCollection()) {
                    (value as List<*>).forEach { valueEntry ->
                        updateGraph.add(
                            sourceContext.subject, propertyUri, NodeFactory.createLiteral(valueEntry.toString())
                        )
                    }
                } else {
                    updateGraph.add(
                        sourceContext.subject, propertyUri, NodeFactory.createLiteral(value.toString())
                    )
                }
            }
        }
        return Pair(updateGraph, deleteGraph)
    }

    private fun getNewInstanceID(input: Map<String, Any?>, resourceType: ResourceType): Node {
        return when (resourceType) {
            ResourceType.CONTAINER -> NodeFactory.createURI("")
            ResourceType.DOCUMENT -> NodeFactory.createURI(
                input[ID_FIELD]?.toString() ?: "#${input[SLUG_FIELD] ?: UUID.randomUUID()}"
            )
        }
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
