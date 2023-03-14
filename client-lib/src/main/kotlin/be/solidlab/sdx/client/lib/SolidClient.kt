package be.solidlab.sdx.client.lib

import com.apollographql.apollo3.ApolloCall
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.ExecutionContext
import com.apollographql.apollo3.api.Mutation
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.network.NetworkTransport

data class SolidClient<C : SolidTargetBackendContext>(
    val backend: SolidTargetBackend<C>,
    val podUrl: String? = null,
    val clientID: String? = null,
    val secret: String? = null
) {

    private val apolloClient = ApolloClient.Builder()
        .networkTransport(backend)
        .build()

    fun <D : Query.Data> query(query: Query<D>, context: C? = null): ApolloCall<D> {
        val q = apolloClient.query(query)
        if (context != null) {
            q.addExecutionContext(SolidExecutionContext(context))
        }
        return q
    }

    fun <D : Mutation.Data> mutation(mutation: Mutation<D>, context: C? = null): ApolloCall<D> {
        TODO()
    }
}

interface SolidTargetBackend<T : SolidTargetBackendContext> : NetworkTransport

interface SolidTargetBackendContext

class SolidExecutionContext(val backendContext: SolidTargetBackendContext) : ExecutionContext.Element {

    override val key: ExecutionContext.Key<*>
        get() = Key

    companion object Key : ExecutionContext.Key<SolidExecutionContext>

}
