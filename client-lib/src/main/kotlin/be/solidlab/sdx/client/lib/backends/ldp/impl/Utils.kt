package be.solidlab.sdx.client.lib.backends.ldp.impl

import graphql.Scalars
import graphql.schema.*
import org.apache.jena.graph.Graph
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.impl.LiteralLabel
import java.net.MalformedURLException
import java.net.URL

internal data class IntermediaryResult(val requestUrl: URL, val documentGraph: Graph, val subject: Node)

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
