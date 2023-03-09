package be.solidlab.sdx.client.lib.backends.ldp

import be.solidlab.sdx.client.lib.SolidExecutionContext
import be.solidlab.sdx.client.lib.SolidTargetBackend
import be.solidlab.sdx.client.lib.SolidTargetBackendContext
import com.apollographql.apollo3.annotations.ApolloExperimental
import com.apollographql.apollo3.api.*
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.Scalars
import graphql.schema.DataFetcher
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeUtil
import graphql.schema.idl.*
import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.ext.web.client.WebClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.future.await
import org.apache.jena.graph.*
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParserBuilder
import org.apache.jena.vocabulary.RDF
import java.io.File
import java.io.StringReader
import java.util.*

data class SolidLDPContext(val target: String) : SolidTargetBackendContext

class SolidLDPBackend : SolidTargetBackend<SolidLDPContext> {

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
        val result = graphql.executeAsync(
            ExecutionInput.newExecutionInput().localContext(context.backendContext).query(q)
                .variables(request.operation.variables(customScalarAdapters).valueMap)
        ).await()
        emit(request.operation.parseJsonResponse(Json.encode(result.toSpecification())))
    }

    private fun buildGraphQL(): GraphQL {
        val inputFile = File("src/main/graphql/schema.graphqls")
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
                return if (setupEnv.directives.any { it.name == "identifier" }) {
                    DataFetcher { runtimeEnv ->
                        val parent = runtimeEnv.parentType as GraphQLObjectType
                        val className = parent.getAppliedDirective("is").getArgument("class").getValue<String>()
                        runtimeEnv.getSource<Graph>()
                            .find(Node.ANY, RDF.type.asNode(), NodeFactory.createURI(className))
                            .nextOptional().map { it.subject.uri }.orElse(null)
                    }
                } else if (setupEnv.directives.any { it.name == "property" }) {
                    DataFetcher { runtimeEnv ->
                        if (GraphQLTypeUtil.isScalar(runtimeEnv.fieldType) || (GraphQLTypeUtil.isWrapped(runtimeEnv.fieldType) && GraphQLTypeUtil.isScalar(
                                GraphQLTypeUtil.unwrapAll(runtimeEnv.fieldType)
                            ))
                        ) {
                            when (GraphQLTypeUtil.unwrapAll(runtimeEnv.fieldType)) {
                                Scalars.GraphQLBoolean -> false
                                Scalars.GraphQLFloat -> 0.0
                                Scalars.GraphQLInt -> 0
                                Scalars.GraphQLString -> {
                                    val propertyPath =
                                        runtimeEnv.fieldDefinition.getAppliedDirective("property").getArgument("iri")
                                            .getValue<String>().removeSurrounding("<", ">")
                                    runtimeEnv.getSource<Graph>()
                                        .find(Node.ANY, NodeFactory.createURI(propertyPath), Node.ANY)
                                        .nextOptional().map { it.`object`.literal.toString(false) }.orElse(null)
                                }

                                else -> null
                            }
                        } else {
                            NodeFactory.createURI("http://example.com/entities/${UUID.randomUUID()}")
                        }
                    }
                } else {
                    // Entry point
                    DataFetcher { runtimeEnv ->
                        if (runtimeEnv.containsArgument("id")) {
                            val targetUri = runtimeEnv.getLocalContext<SolidLDPContext>().target
                            webClient.getAbs(targetUri).send().toCompletionStage().thenApply { resp ->
                                val document = resp.bodyAsString()
                                val documentGraph =
                                    RDFParserBuilder.create().source(StringReader(document)).lang(Lang.TURTLE).toGraph()
                                val result = GraphExtract(TripleBoundary.stopNowhere).extract(
                                    NodeFactory.createURI(
                                        runtimeEnv.getArgument("id")
                                    ), documentGraph
                                )
                                result
                            }
                        } else {
                            TODO()
                        }
                    }
                }
            }
        }
        return RuntimeWiring.newRuntimeWiring().wiringFactory(dynamicWiringFactory).build()
    }
}
