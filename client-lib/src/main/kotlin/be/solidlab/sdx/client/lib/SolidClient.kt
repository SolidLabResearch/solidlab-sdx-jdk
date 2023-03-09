package be.solidlab.sdx.client.lib

import com.apollographql.apollo3.ApolloCall
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.ExecutionContext
import com.apollographql.apollo3.api.Mutation
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.network.NetworkTransport

class SolidClient<C : SolidTargetBackendContext> private constructor(
    private val backend: SolidTargetBackend<C>,
    private val podUrl: String?,
    clientID: String?,
    secret: String?
) {

    companion object {

        fun <C : SolidTargetBackendContext, T : SolidTargetBackend<C>> using(backend: T): Builder<C> {
            return Builder(backend)
        }

    }

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

    class Builder<C : SolidTargetBackendContext> constructor(private val backend: SolidTargetBackend<C>) {

        private var podUrl: String? = null
        private var clientID: String? = null
        private var secret: String? = null
        private var collectionEntryPointTarget = CollectionEntryPointTarget.DOCUMENT

        fun withCredentials(clientID: String, secret: String) = apply {
            this.clientID = clientID
            this.secret = secret
        }

        fun withPodUrl(podUrl: String) = apply {
            this.podUrl = podUrl
        }

        fun collectionEntryPointTarget(target: CollectionEntryPointTarget) = apply {
            this.collectionEntryPointTarget
        }

        fun build(): SolidClient<C> {
            return SolidClient(backend, podUrl, clientID, secret)
        }

    }
}

enum class CollectionEntryPointTarget {
    DOCUMENT, FOLDER
}

interface SolidTargetBackend<T : SolidTargetBackendContext> : NetworkTransport

interface SolidTargetBackendContext

class SolidExecutionContext(val backendContext: SolidTargetBackendContext) : ExecutionContext.Element {

    override val key: ExecutionContext.Key<*>
        get() = Key

    companion object Key : ExecutionContext.Key<SolidExecutionContext>

}
