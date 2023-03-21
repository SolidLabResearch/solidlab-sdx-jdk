package be.solidlab.sdx.client.lib.backends.ldp

import be.solidlab.sdx.client.lib.SolidExecutionContext
import be.solidlab.sdx.client.lib.SolidTargetBackend
import be.solidlab.sdx.client.lib.SolidTargetBackendContext
import com.apollographql.apollo3.annotations.ApolloExperimental
import com.apollographql.apollo3.api.*
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.Scalars
import graphql.schema.*
import graphql.schema.idl.*
import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.ext.web.client.WebClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import org.apache.http.HttpHeaders
import org.apache.jena.graph.*
import org.apache.jena.graph.impl.LiteralLabel
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParserBuilder
import org.apache.jena.sparql.graph.GraphFactory
import org.apache.jena.vocabulary.RDF
import java.io.File
import java.io.StringReader
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import java.util.concurrent.CompletionStage

const val MUTATION_OPERATION_NAME = "MUTATION"
const val CONTENT_TYPE_TURTLE = "text/turtle"
const val LINK_HEADER = "Link"
const val IS_CONTAINER_LINK_HEADER_VAL = "<http://www.w3.org/ns/ldp#Container>; rel=\"type\""
const val IS_RESOURCE_LiNK_HEADER_VAL = "<http://www.w3.org/ns/ldp#Resource>; rel=\"type\""
val LDP_CONTAINS = NodeFactory.createURI("http://www.w3.org/ns/ldp#contains")

data class SolidLDPContext(val resolver: TargetResolver) : SolidTargetBackendContext

data class SolidLDPBackend(
    private val podUrl: String? = null,
    private val clientID: String? = null,
    private val secret: String? = null,
    private val schemaFile: String = "src/main/graphql/schema.graphqls"
) :
    SolidTargetBackend<SolidLDPContext> {

    init {
        // Validation of constructor arguments
        require(podUrl != null || clientID == null) { "When using client authentication, a podUrl must be provided!" }
        require(secret != null || clientID == null) { "When using client authentication, a client secret must be provided!" }
    }

    private val vertx = Vertx.vertx()
    private val webClient = WebClient.create(vertx)
    private val graphql = buildGraphQL()

    override fun dispose() {}

    @OptIn(ApolloExperimental::class)
    override fun <D : Operation.Data> execute(request: ApolloRequest<D>): Flow<ApolloResponse<D>> = flow {
        val context =
            request.executionContext[SolidExecutionContext.Key] ?: throw IllegalArgumentException("Missing context")
        val customScalarAdapters = request.executionContext[CustomScalarAdapters.Key]!!
        val q = request.operation.document()
        println(q)
        val result = execute(
            q,
            context.backendContext as SolidLDPContext,
            request.operation.variables(customScalarAdapters).valueMap
        )
        emit(request.operation.parseJsonResponse(Json.encode(result)))
    }

    suspend fun execute(
        queryStr: String,
        backendContext: SolidLDPContext,
        variables: Map<String, Any?>
    ): Map<String, Any?> {
        val result = graphql.executeAsync(
            ExecutionInput.newExecutionInput().localContext(backendContext).query(queryStr)
                .variables(variables)
        ).await()
        return result.toSpecification()
    }

    private fun buildGraphQL(): GraphQL {
        val inputFile = File(schemaFile)
        val typeDefinitionRegistry = SchemaParser().parse(inputFile)
        val runtimeWiring = buildRuntimeWiring()
        val schema = SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)
        return GraphQL.newGraphQL(schema).build()
    }

    private fun buildRuntimeWiring(): RuntimeWiring {
        val dynamicWiringFactory = object : WiringFactory {
            override fun providesDataFetcher(environment: FieldWiringEnvironment): Boolean {
                return true
            }

            override fun getDataFetcher(setupEnv: FieldWiringEnvironment): DataFetcher<*> {
                return DataFetcher { runtimeEnv ->
                    if (setupEnv.directives.any { it.name == "identifier" }) {
                        handleIdProperty(runtimeEnv)
                    } else if (setupEnv.directives.any { it.name == "property" }) {
                        if (isScalarType(runtimeEnv.fieldType)) {
                            handleScalarProperty(runtimeEnv)
                        } else {
                            handleRelationProperty(runtimeEnv)
                        }
                    } else {
                        if (runtimeEnv.operationDefinition.operation.name == MUTATION_OPERATION_NAME) {
                            handleMutationEntrypoint(runtimeEnv)
                        } else {
                            handleEntrypoint(runtimeEnv)
                        }
                    }
                }
            }
        }
        return RuntimeWiring.newRuntimeWiring().wiringFactory(dynamicWiringFactory).build()
    }

    private fun handleIdProperty(runtimeEnv: DataFetchingEnvironment): String {
        return runtimeEnv.getSource<IntermediaryResult>().subject.toString(false)
    }

    private fun handleScalarProperty(runtimeEnv: DataFetchingEnvironment): Any? {
        val source = runtimeEnv.getSource<IntermediaryResult>()
        val result = source.documentGraph
            .find(source.subject, getPropertyPath(runtimeEnv), Node.ANY)
            .mapWith { convertScalarValue(GraphQLTypeUtil.unwrapAll(runtimeEnv.fieldType), it.`object`.literal) }
        return if (isCollectionType(runtimeEnv.fieldType)) result.toList() else result.asSequence().firstOrNull()
    }

    private fun handleRelationProperty(runtimeEnv: DataFetchingEnvironment): Any? {
        val source = runtimeEnv.getSource<IntermediaryResult>()
        val type = getTypeClassURI(runtimeEnv.fieldType)
        val result = source.documentGraph
            .find(source.subject, getPropertyPath(runtimeEnv), Node.ANY)
            .filterKeep { source.documentGraph.find(it.`object`, RDF.type.asNode(), type).hasNext() }
            .mapWith { IntermediaryResult(source.documentGraph, it.`object`) }
        return if (isCollectionType(runtimeEnv.fieldType)) result.toList() else result.asSequence().firstOrNull()
    }

    private fun handleEntrypoint(runtimeEnv: DataFetchingEnvironment): CompletionStage<Any?> =
        CoroutineScope(Dispatchers.IO).future {
            val classUri = getTypeClassURI(runtimeEnv.fieldType)
            val targetUrl = runtimeEnv.getLocalContext<SolidLDPContext>().resolver.resolve(
                classUri.uri.toString(),
                object : TargetResolverContext {})
            if (targetUrl != null) {
                val resourceType = fetchResourceType(targetUrl)
                if (runtimeEnv.containsArgument("id")) {
                    val documentUrl =
                        if (resourceType == ResourceType.DOCUMENT) targetUrl else getAbsoluteURL(
                            runtimeEnv.getArgument(
                                "id"
                            ), targetUrl
                        )
                    if (!documentUrl.toString().startsWith(targetUrl.toString())) {
                        throw IllegalArgumentException("Entity with id $documentUrl is not in range of target URL $targetUrl")
                    }
                    val documentGraph = downloadDocumentGraph(documentUrl)
                    // Specific instance entrypoint
                    documentGraph.find(
                        NodeFactory.createURI(runtimeEnv.getArgument("id")),
                        RDF.type.asNode(),
                        classUri
                    ).mapWith { IntermediaryResult(documentGraph, it.subject) }.asSequence().firstOrNull()
                } else {
                    // Collection entrypoint
                    val documentGraph =
                        if (resourceType == ResourceType.DOCUMENT) downloadDocumentGraph(targetUrl) else downloadContainerAsGraph(
                            targetUrl
                        )
                    documentGraph.find(
                        Node.ANY,
                        RDF.type.asNode(),
                        classUri
                    ).mapWith { IntermediaryResult(documentGraph, it.subject) }.toList()
                }
            } else {
                throw RuntimeException("A target URL for this request could not be resolved!")
            }
        }

    private fun handleMutationEntrypoint(runtimeEnv: DataFetchingEnvironment): CompletionStage<Any?> =
        CoroutineScope(Dispatchers.IO).future {
            if (runtimeEnv.field.name.startsWith("create")) {
                val classUri = getTypeClassURI(runtimeEnv.fieldType)
                val targetUrl = runtimeEnv.getLocalContext<SolidLDPContext>().resolver.resolve(
                    classUri.uri.toString(),
                    object : TargetResolverContext {})
                if (targetUrl != null) {
                    TODO()
                } else {
                    throw RuntimeException("A target URL for this request could not be resolved!")
                }
            } else {
                TODO()
            }
        }

    private suspend fun downloadDocumentGraph(url: URL): Graph {
        val resp = webClient.getAbs(url.toString()).putHeader(HttpHeaders.ACCEPT, CONTENT_TYPE_TURTLE).send()
            .toCompletionStage().await()
        val document = resp.bodyAsString()
        return RDFParserBuilder.create().source(StringReader(document)).lang(Lang.TURTLE).toGraph()
    }

    private suspend fun downloadContainerAsGraph(url: URL): Graph {
        val containerResp = webClient.getAbs(url.toString()).putHeader(HttpHeaders.ACCEPT, CONTENT_TYPE_TURTLE).send()
            .toCompletionStage().await().bodyAsString()
        val containerIndex = RDFParserBuilder.create().source(containerResp).lang(Lang.TURTLE).toGraph()
        val resultGraph = GraphFactory.createDefaultGraph()
        containerIndex.find(Node.ANY, LDP_CONTAINS, Node.ANY).asSequence().asFlow()
            .map { containedResource ->
                val subGraph = downloadDocumentGraph(URL(containedResource.`object`.uri))
                subGraph.find().forEach { resultGraph.add(it) }
            }
        return resultGraph
    }

    private suspend fun fetchResourceType(url: URL): ResourceType {
        val resp = webClient.headAbs(url.toString()).send().toCompletionStage().await()
        // Get type using link header
        return if (resp.headers().getAll(LINK_HEADER).any { it == IS_CONTAINER_LINK_HEADER_VAL }) {
            ResourceType.CONTAINER
        } else if (resp.headers().getAll(LINK_HEADER).any { it == IS_RESOURCE_LiNK_HEADER_VAL }) {
            ResourceType.DOCUMENT
        } else {
            throw RuntimeException("The target URL does not represent an LDP container or resource type!")
        }
    }

    private fun isScalarType(type: GraphQLOutputType): Boolean {
        return GraphQLTypeUtil.isScalar(type) || GraphQLTypeUtil.isScalar((GraphQLTypeUtil.unwrapAll(type)))
    }

    private fun isCollectionType(type: GraphQLOutputType): Boolean {
        return GraphQLTypeUtil.isList(GraphQLTypeUtil.unwrapNonNull(type))
    }

    private fun getPropertyPath(runtimeEnv: DataFetchingEnvironment): Node {
        return NodeFactory.createURI(
            runtimeEnv.fieldDefinition.getAppliedDirective("property").getArgument("iri")
                .getValue<String>().removeSurrounding("<", ">")
        )
    }

    private fun getTypeClassURI(type: GraphQLOutputType) =
        NodeFactory.createURI(
            (GraphQLTypeUtil.unwrapAll(type) as GraphQLObjectType).getAppliedDirective("is")
                .getArgument("class")
                .getValue<String>()
        )

    private fun convertScalarValue(type: GraphQLUnmodifiedType, literal: LiteralLabel): Any? {
        if (!GraphQLTypeUtil.isScalar(type)) {
            throw IllegalArgumentException("$type is not a scalar type")
        }
        return when (type) {
            Scalars.GraphQLBoolean -> literal.toString(false).toBoolean()
            Scalars.GraphQLFloat -> literal.toString(false).toFloat()
            Scalars.GraphQLInt -> literal.toString(false).toInt()
            Scalars.GraphQLString -> literal.toString(false)
            else -> null
        }
    }

    private fun getAbsoluteURL(urlOrRelativePath: String, baseUrl: URL): URL {
        return try {
            URL(urlOrRelativePath)
        } catch (err: MalformedURLException) {
            URL("${baseUrl.toString()}/${urlOrRelativePath.removePrefix("/")}")
        }
    }
}

private data class IntermediaryResult(val documentGraph: Graph, val subject: Node)

private enum class ResourceType {
    CONTAINER, DOCUMENT
}
