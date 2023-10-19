package be.ugent.solidlab.sdx.client.lib.backends.ldp.impl

import be.ugent.solidlab.sdx.client.commons.graphql.isCollection
import be.ugent.solidlab.sdx.client.commons.graphql.unwrapNonNull
import be.ugent.solidlab.sdx.client.commons.ldp.LdpClient
import be.ugent.solidlab.sdx.client.commons.ldp.ResourceType
import be.ugent.solidlab.sdx.client.lib.backends.ldp.SolidLDPContext
import be.ugent.solidlab.sdx.client.lib.backends.ldp.TargetResolverContext
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLTypeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.apache.jena.graph.Node
import org.apache.jena.vocabulary.RDF
import java.util.concurrent.CompletionStage

internal class QueryHandler(private val ldpClient: LdpClient) {
    fun handleIdProperty(runtimeEnv: DataFetchingEnvironment): String {
        return runtimeEnv.getSource<IntermediaryResult>().getFQSubject()
    }

    fun handleScalarProperty(runtimeEnv: DataFetchingEnvironment): Any? {
        val source = runtimeEnv.getSource<IntermediaryResult>()
        val result = source.documentGraph
            .find(source.subject, getPropertyPath(runtimeEnv), Node.ANY)
            .mapWith { convertScalarValue(GraphQLTypeUtil.unwrapAll(runtimeEnv.fieldType), it.`object`.literal) }
        return if (runtimeEnv.fieldType.unwrapNonNull().isCollection()) result.toList() else result.asSequence()
            .firstOrNull()
    }

    fun handleRelationProperty(runtimeEnv: DataFetchingEnvironment): Any? {
        val source = runtimeEnv.getSource<IntermediaryResult>()
        val type = getTypeClassURI(runtimeEnv.fieldType)
        val result = source.documentGraph
            .find(source.subject, getPropertyPath(runtimeEnv), Node.ANY)
            .filterKeep { source.documentGraph.find(it.`object`, RDF.type.asNode(), type).hasNext() }
            .mapWith { IntermediaryResult(source.requestUrl, source.resourceType, source.documentGraph, it.`object`) }
        return if (runtimeEnv.fieldType.unwrapNonNull().isCollection()) result.toList() else result.asSequence()
            .firstOrNull()
    }

    fun handleEntrypoint(runtimeEnv: DataFetchingEnvironment): CompletionStage<Any?> =
        CoroutineScope(Dispatchers.IO).future {
            val classUri = getTypeClassURI(runtimeEnv.fieldType)
            val targetUrl = runtimeEnv.getLocalContext<SolidLDPContext>().resolver.resolve(
                classUri.uri.toString(), TargetResolverContext(ldpClient)
            )
            if (targetUrl != null) {
                val resourceType = ldpClient.fetchResourceType(targetUrl)
                if (runtimeEnv.containsArgument("id")) {
                    getInstanceById(ldpClient, targetUrl, runtimeEnv.getArgument("id"), classUri, resourceType)
                } else {
                    // Collection entrypoint
                    val documentGraph =
                        if (resourceType == ResourceType.DOCUMENT) {
                            ldpClient.downloadDocumentGraph(targetUrl)
                        } else {
                            ldpClient.downloadContainerAsGraph(targetUrl)
                        }
                    documentGraph.find(
                        Node.ANY,
                        RDF.type.asNode(),
                        classUri
                    ).mapWith { IntermediaryResult(targetUrl, resourceType, documentGraph, it.subject) }.toList()
                }
            } else {
                throw RuntimeException("A target URL for this request could not be resolved!")
            }
        }
}
