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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.future.await
import org.apache.jena.graph.*
import org.apache.jena.graph.impl.LiteralLabel
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParserBuilder
import org.apache.jena.vocabulary.RDF
import java.io.File
import java.io.StringReader
import java.util.*
import java.util.concurrent.CompletionStage

data class SolidLDPContext(val target: String = "src/main/graphql/schema.graphqls") : SolidTargetBackendContext

class SolidLDPBackend(private val schemaFile: String) : SolidTargetBackend<SolidLDPContext> {

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
                        handleEntrypoint(runtimeEnv)
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
        val result = source.documentGraph
            .find(source.subject, getPropertyPath(runtimeEnv), Node.ANY)
            .mapWith { IntermediaryResult(source.documentGraph, it.`object`) }
        return if (isCollectionType(runtimeEnv.fieldType)) result.toList() else result.asSequence().firstOrNull()
    }

    private fun handleEntrypoint(runtimeEnv: DataFetchingEnvironment): CompletionStage<Any?> {
        val targetUri = runtimeEnv.getLocalContext<SolidLDPContext>().target
        val className =
            (GraphQLTypeUtil.unwrapAll(runtimeEnv.fieldType) as GraphQLObjectType).getAppliedDirective("is")
                .getArgument("class")
                .getValue<String>()
        return downloadDocumentGraph(targetUri).thenApply { documentGraph ->
            if (runtimeEnv.containsArgument("id")) {
                // Specific instance entrypoint
                documentGraph.find(
                    NodeFactory.createURI(runtimeEnv.getArgument("id")),
                    RDF.type.asNode(),
                    NodeFactory.createURI(className)
                ).mapWith { IntermediaryResult(documentGraph, it.subject) }.asSequence().firstOrNull()
            } else {
                // Collection entrypoint
                documentGraph.find(
                    Node.ANY,
                    RDF.type.asNode(),
                    NodeFactory.createURI(className)
                ).mapWith { IntermediaryResult(documentGraph, it.subject) }.toList()
            }
        }
    }

    private fun downloadDocumentGraph(url: String): CompletionStage<Graph> {
        return webClient.getAbs(url).send().toCompletionStage().thenApply { resp ->
            val document = resp.bodyAsString()
            RDFParserBuilder.create().source(StringReader(document)).lang(Lang.TURTLE).toGraph()
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
}

private data class IntermediaryResult(val documentGraph: Graph, val subject: Node)
