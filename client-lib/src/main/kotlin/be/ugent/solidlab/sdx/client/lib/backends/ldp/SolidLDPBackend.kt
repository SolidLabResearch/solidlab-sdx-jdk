package be.ugent.solidlab.sdx.client.lib.backends.ldp

import be.ugent.solidlab.sdx.client.commons.auth.SolidClientCredentials
import be.ugent.solidlab.sdx.client.commons.graphql.isScalar
import be.ugent.solidlab.sdx.client.commons.graphql.rawType
import be.ugent.solidlab.sdx.client.commons.ldp.LdpClient
import be.ugent.solidlab.sdx.client.lib.SolidExecutionContext
import be.ugent.solidlab.sdx.client.lib.SolidTargetBackend
import be.ugent.solidlab.sdx.client.lib.SolidTargetBackendContext
import be.ugent.solidlab.sdx.client.lib.backends.ldp.impl.MutationHandler
import be.ugent.solidlab.sdx.client.lib.backends.ldp.impl.QueryHandler
import com.apollographql.apollo3.annotations.ApolloExperimental
import com.apollographql.apollo3.api.*
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.schema.*
import graphql.schema.idl.*
import io.vertx.core.Vertx
import io.vertx.core.json.Json
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import java.io.File

const val MUTATION_OPERATION_NAME = "MUTATION"

data class SolidLDPContext(val resolver: TargetResolver) :
    SolidTargetBackendContext

data class SolidLDPBackend(
    private val clientCredentials: SolidClientCredentials? = null,
    private val schemaFile: String = "src/main/graphql/schema.graphqls"
) :
    SolidTargetBackend<SolidLDPContext> {

    private val vertx = Vertx.vertx()
    private val ldpClient = LdpClient(vertx, clientCredentials)
    private val queryHandler = QueryHandler(ldpClient)
    private val mutationHandler = MutationHandler(ldpClient)
    private val graphql = buildGraphQL()

    override fun dispose() {
        vertx.close().result()
    }

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
                        queryHandler.handleIdProperty(runtimeEnv)
                    } else if (setupEnv.directives.any { it.name == "property" }) {
                        if (runtimeEnv.fieldType.rawType().isScalar()) {
                            queryHandler.handleScalarProperty(runtimeEnv)
                        } else {
                            queryHandler.handleRelationProperty(runtimeEnv)
                        }
                    } else {
                        if (runtimeEnv.operationDefinition.operation.name == MUTATION_OPERATION_NAME) {
                            mutationHandler.handleMutationEntrypoint(runtimeEnv)
                        } else {
                            queryHandler.handleEntrypoint(runtimeEnv)
                        }
                    }
                }
            }
        }
        return RuntimeWiring.newRuntimeWiring().wiringFactory(dynamicWiringFactory).build()
    }
}
