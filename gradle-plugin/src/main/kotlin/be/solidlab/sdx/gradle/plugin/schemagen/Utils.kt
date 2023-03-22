package be.solidlab.sdx.gradle.plugin.schemagen

import be.solidlab.sdx.gradle.plugin.ShapeImport
import com.apollographql.apollo3.compiler.capitalizeFirstLetter
import org.apache.jena.graph.Graph
import org.apache.jena.graph.Node
import org.apache.jena.graph.Node_URI
import org.apache.jena.shacl.Shapes
import org.apache.jena.shacl.engine.TargetType
import org.apache.jena.shacl.parser.NodeShape
import java.net.URI
import java.util.*
import kotlin.jvm.optionals.getOrNull

internal fun decapitalize(str: String): String {
    return str.replaceFirstChar { it.lowercase(Locale.getDefault()) }
}

internal fun parseName(subject: Node, context: ParseContext): String {
    val shapeImport = context.getImportDefinition(subject)
    val uri = URI(subject.uri)
    // If the URI has a fragment, use fragment, otherwise use the last path segment
    val name = uri.fragment ?: uri.path.substringAfterLast("/")
    return "${shapeImport.typePrefix.capitalizeFirstLetter()}${name.capitalizeFirstLetter()}"
}

@OptIn(ExperimentalStdlibApi::class)
internal fun Graph.getString(subject: Node, predicate: Node): String? {
    return this.find(subject, predicate, null)
        .nextOptional().getOrNull()?.`object`?.takeIf { it.isLiteral }?.literalValue?.toString()
}

@OptIn(ExperimentalStdlibApi::class)
internal fun Graph.getReference(subject: Node, predicate: Node): Node_URI? {
    return this.find(subject, predicate, null)
        .nextOptional().getOrNull()?.`object`?.takeIf { it.isURI }?.let { it as Node_URI }
}

internal fun Shapes.getMatchingShape(shClass: Node_URI): NodeShape? {
    return this.filter { it.isNodeShape }.find {
        it.targets.any { target ->
            target.targetType == TargetType.targetClass && target.`object` == shClass
        }
    }?.let { it as NodeShape }
}

internal fun propertyNameFromPath(path: String): String {
    return if (path.contains("#")) {
        path.substringAfterLast("#")
    } else {
        path.substringAfterLast("/")
    }
}

internal data class ParseContext(
    val allShapes: Shapes,
    val shapeImports: Map<Node, ShapeImport>,
    val nodeShape: NodeShape? = null
) {

    fun getImportDefinition(subject: Node): ShapeImport {
        val importNode = allShapes.graph.find(subject, importedFrom, Node.ANY).toList()
            .firstOrNull()?.`object`
        return shapeImports[importNode]!!
    }

}

internal enum class InputTypeConfiguration(val typePrefix: String) {
    CREATE_TYPE("Create"), UPDATE_TYPE("Update")
}
