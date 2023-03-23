package be.solidlab.sdx.client.commons.ldp

import be.solidlab.sdx.client.commons.linkeddata.GraphIO
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.WebClient
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.await
import org.apache.http.HttpHeaders
import org.apache.jena.graph.Graph
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFParserBuilder
import org.apache.jena.sparql.graph.GraphFactory
import java.io.StringReader
import java.io.StringWriter
import java.net.URL

private const val CONTENT_TYPE_TURTLE = "text/turtle"
private val LDP_CONTAINS = NodeFactory.createURI("http://www.w3.org/ns/ldp#contains")
private const val LINK_HEADER = "Link"
private const val IS_CONTAINER_LINK_HEADER_VAL = "<http://www.w3.org/ns/ldp#Container>; rel=\"type\""
private const val IS_RESOURCE_LiNK_HEADER_VAL = "<http://www.w3.org/ns/ldp#Resource>; rel=\"type\""

class LdpClient(vertx: Vertx) {

    private val webClient = WebClient.create(vertx)

    suspend fun downloadDocumentGraph(url: URL): Graph {
        val resp = webClient.getAbs(url.toString()).putHeader(HttpHeaders.ACCEPT, CONTENT_TYPE_TURTLE).send()
            .toCompletionStage().await()
        val document = resp.bodyAsString()
        return RDFParserBuilder.create().source(StringReader(document)).lang(Lang.TURTLE).toGraph()
    }

    suspend fun downloadContainerAsGraph(url: URL): Graph {
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

    suspend fun putDocument(url: URL, content: Graph) {
        val requestBody = StringWriter().use {
            RDFDataMgr.write(it, content, Lang.TURTLE)
            it.toString()
        }
        val resp = webClient.putAbs(url.toString()).putHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_TURTLE)
            .sendBuffer(Buffer.buffer(requestBody)).toCompletionStage().await()
        if (resp.statusCode() !in 200..399) {
            throw RuntimeException("The post was not completed successfully (status: ${resp.statusCode()}, message: ${resp.bodyAsString()})")
        }
    }

    suspend fun patchDocument(url: URL, inserts: Graph? = null, deletes: Graph? = null) {
        val n3Inserts = inserts?.let { insertGraph ->
            "solid:inserts { ${GraphIO.encodeAsString(insertGraph, Lang.N3)} }"
        }
        val n3Deletes = deletes?.let { deleteGraph ->
            "solid:deletes { ${GraphIO.encodeAsString(deleteGraph, Lang.N3)} }"
        }
        val patchContent = listOfNotNull(n3Inserts, n3Deletes).joinToString(separator = ";", postfix = ".")
        val requestBody =
            "@prefix solid: <http://www.w3.org/ns/solid/terms#>. _:rename a solid:InsertDeletePatch; $patchContent"

        val resp = webClient.patchAbs(url.toString()).putHeader(HttpHeaders.CONTENT_TYPE, "text/n3")
            .sendBuffer(Buffer.buffer(requestBody)).toCompletionStage().await()
        if (resp.statusCode() !in 200..399) {
            throw RuntimeException("The patch was not completed successfully (status: ${resp.statusCode()}, message: ${resp.bodyAsString()})")
        }
    }

    suspend fun deleteDocument(url: URL) {
        val resp = webClient.deleteAbs(url.toString()).send().toCompletionStage().await()
        if (resp.statusCode() !in 200..399) {
            throw RuntimeException("The delete was not completed successfully (status: ${resp.statusCode()}, message: ${resp.bodyAsString()})")
        }
    }

    suspend fun fetchResourceType(url: URL): ResourceType {
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

}

enum class ResourceType {
    CONTAINER, DOCUMENT
}

