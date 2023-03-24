package be.solidlab.sdx.client.lib.backends.ldp.impl

import be.solidlab.sdx.client.commons.ldp.LdpClient
import be.solidlab.sdx.client.commons.ldp.ResourceType
import graphql.Scalars
import graphql.schema.*
import org.apache.jena.graph.Graph
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.impl.LiteralLabel
import org.apache.jena.vocabulary.RDF
import java.net.MalformedURLException
import java.net.URL

internal data class IntermediaryResult(
    val requestUrl: URL,
    val resourceType: ResourceType,
    val documentGraph: Graph,
    val subject: Node
) {
    fun getFQSubject(): String {
        val idVal = this.subject.toString(false)
        return if (idVal.isEmpty() || idVal.startsWith("#")) this.requestUrl.toString().plus(idVal) else idVal
    }
}

internal suspend fun getInstanceById(
    ldpClient: LdpClient,
    targetUrl: URL,
    id: String,
    classUri: Node,
    resourceType: ResourceType
): IntermediaryResult? {
    val documentUrl =
        if (resourceType == ResourceType.DOCUMENT) targetUrl else getAbsoluteURL(id, targetUrl)
    if (!documentUrl.toString().startsWith(targetUrl.toString())) {
        throw IllegalArgumentException("Entity with id $documentUrl is not in range of target URL $targetUrl")
    }
    val documentGraph = ldpClient.downloadDocumentGraph(documentUrl)
    // Specific instance entrypoint
    return documentGraph.find(
        NodeFactory.createURI(id),
        RDF.type.asNode(),
        classUri
    ).mapWith { IntermediaryResult(targetUrl, resourceType, documentGraph, it.subject) }.asSequence().firstOrNull()
}

internal fun getPropertyPath(runtimeEnv: DataFetchingEnvironment): Node {
    return NodeFactory.createURI(
        runtimeEnv.fieldDefinition.getAppliedDirective("property").getArgument("iri")
            .getValue<String>().removeSurrounding("<", ">")
    )
}

internal fun getTypeClassURI(type: GraphQLOutputType) =
    NodeFactory.createURI(
        (GraphQLTypeUtil.unwrapAll(type) as GraphQLObjectType).getAppliedDirective("is")
            .getArgument("class")
            .getValue<String>()
    )

internal fun convertScalarValue(type: GraphQLUnmodifiedType, literal: LiteralLabel): Any? {
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

internal fun getAbsoluteURL(urlOrRelativePath: String, baseUrl: URL): URL {
    return try {
        URL(urlOrRelativePath)
    } catch (err: MalformedURLException) {
        URL("${baseUrl.toString()}/${urlOrRelativePath.removePrefix("/")}")
    }
}
