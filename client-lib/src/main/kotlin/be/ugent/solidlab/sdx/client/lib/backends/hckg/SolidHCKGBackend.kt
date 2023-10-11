package be.ugent.solidlab.sdx.client.lib.backends.hckg

import be.ugent.solidlab.sdx.client.lib.SolidTargetBackend
import be.ugent.solidlab.sdx.client.lib.SolidTargetBackendContext
import com.apollographql.apollo3.api.ApolloRequest
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.Operation
import kotlinx.coroutines.flow.Flow

data class SolidHCKGContext(val placeHolder: String) : SolidTargetBackendContext

class SolidHCKGBackend : SolidTargetBackend<SolidHCKGContext> {
    override fun dispose() {
        TODO("Not yet implemented")
    }

    override fun <D : Operation.Data> execute(request: ApolloRequest<D>): Flow<ApolloResponse<D>> {
        TODO("Not yet implemented")
    }
}
