package be.solidlab.sdx.gradle.plugin

import com.apollographql.apollo3.compiler.capitalizeFirstLetter
import graphql.Scalars
import graphql.introspection.Introspection
import graphql.schema.*
import graphql.schema.idl.SchemaPrinter
import org.apache.jena.graph.Graph
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.Node_URI
import org.apache.jena.graph.Triple
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.shacl.Shapes
import org.apache.jena.shacl.engine.TargetType
import org.apache.jena.shacl.engine.constraint.MaxCount
import org.apache.jena.shacl.engine.constraint.MinCount
import org.apache.jena.shacl.parser.NodeShape
import org.apache.jena.shacl.parser.PropertyShape
import org.apache.jena.shacl.vocabulary.SHACL
import org.apache.jena.sparql.graph.GraphFactory
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.XSD
import org.gradle.api.file.Directory
import java.net.URI
import java.util.*
import kotlin.jvm.optionals.getOrNull

val importedFrom = NodeFactory.createURI("https://solidlab.be/vocab/importedFrom")

val identifierDirective: GraphQLDirective = GraphQLDirective.newDirective()
    .name("identifier")
    .validLocation(Introspection.DirectiveLocation.FIELD_DEFINITION)
    .build()

val idField: GraphQLFieldDefinition = GraphQLFieldDefinition.newFieldDefinition()
    .name("id")
    .description("Auto-generated property that will be assigned to the `iri` of the Thing that is being queried.")
    .type(GraphQLNonNull.nonNull(Scalars.GraphQLID))
    .withAppliedDirective(identifierDirective.toAppliedDirective())
    .build()

val isDirective: GraphQLDirective = GraphQLDirective.newDirective()
    .name("is")
    .argument(GraphQLArgument.newArgument().name("class").type(Scalars.GraphQLString))
    .validLocation(Introspection.DirectiveLocation.OBJECT)
    .build()
val propertyDirective: GraphQLDirective = GraphQLDirective.newDirective()
    .name("property")
    .argument(GraphQLArgument.newArgument().name("iri").type(Scalars.GraphQLString))
    .validLocation(Introspection.DirectiveLocation.FIELD_DEFINITION)
    .build()

object SHACLToGraphQL {
    fun getSchema(shaclDir: Directory, shapeImports: List<ShapeImport>): String {
        println("Looking at Shape definitions in ${shaclDir.asFile.path}")
        val graph = GraphFactory.createDefaultGraph()
        shapeImports.forEach { shapeImport ->
            println("  --> Importing ${shapeImport.importUrl} into graph")
            val shapeSubGraph = GraphFactory.createDefaultGraph()
            RDFDataMgr.read(shapeSubGraph, shaclDir.file(shapeImport.getTargetFileName()).asFile.toURI().toString())
            shapeSubGraph.find().filterDrop { it.subject.isURI && shapeImport.exclude.contains(it.subject.uri) }
                .forEach {
                    if (it.predicateMatches(RDF.type.asNode())) {
                        graph.add(Triple.create(it.subject, importedFrom, NodeFactory.createURI(shapeImport.importUrl)))
                    }
                    graph.add(it)
                }
        }
        val context =
            ParseContext(Shapes.parse(graph), shapeImports.associateBy { NodeFactory.createURI(it.importUrl) })

        println("Building GraphQL schema from Graph")
        println("  --> Constructing GraphQL object types")
        val graphQLTypes = context.allShapes.iteratorAll().asSequence()
            .map { shape -> generateObjectType(shape as NodeShape, context) }.distinctBy { it.name }.toSet()

        println("  --> Constructing GraphQL input types")
        val graphQLInputTypes = context.allShapes.iteratorAll().asSequence()
            .filter { shape -> context.getImportDefinition(shape.shapeNode).generateMutations }
            .flatMap { shape ->
                InputTypeConfiguration.values().map { generateInputType(shape as NodeShape, context, it) }
            }
            .distinctBy { it.name }.toSet()

        println("  --> Assembling the GraphQL schema")
        val schema = GraphQLSchema.newSchema()
            .query(generateEntryPoints(graphQLTypes))
            .mutation(generateMutations(graphQLInputTypes))
            .additionalTypes(graphQLTypes)
            .additionalDirectives(setOf(isDirective, propertyDirective, identifierDirective))
            .build()
        val schemaPrinter = SchemaPrinter(SchemaPrinter.Options.defaultOptions())
        return schemaPrinter.print(schema)
    }
}

fun generateEntryPoints(types: Set<GraphQLObjectType>): GraphQLObjectType {
    return GraphQLObjectType.newObject()
        .name("Query")
        .fields(types.flatMap { type ->
            listOf(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name(decapitalize(type.name))
                    .argument(GraphQLArgument.newArgument().name("id").type(Scalars.GraphQLString).build())
                    .type(type)
                    .build(),
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("${decapitalize(type.name)}Collection")
                    .type(GraphQLList.list(type))
                    .build()
            )
        })
        .build()
}

fun generateMutations(types: Set<GraphQLInputObjectType>): GraphQLObjectType {
    TODO()
}

fun generateObjectType(shape: NodeShape, context: ParseContext): GraphQLObjectType {
    val shapeName = parseName(shape.shapeNode, context)
    val childContext = context.copy(nodeShape = shape)

    return GraphQLObjectType.newObject()
        .name(shapeName)
        .withAppliedDirective(
            isDirective.toAppliedDirective().transform {
                it.argument(
                    GraphQLAppliedDirectiveArgument.newArgument().name("class").type(Scalars.GraphQLString)
                        .valueProgrammatic(shape.targets.find { it.targetType == TargetType.targetClass }?.`object`?.uri)
                )
            }
        )
        .fields(listOf(idField).plus(shape.propertyShapes.mapNotNull { generateFieldDefinition(it, childContext) }))
        .build()
}

fun generateInputType(
    shape: NodeShape,
    context: ParseContext,
    inputTypeConfiguration: InputTypeConfiguration
): GraphQLInputObjectType {
    val shapeName = parseName(shape.shapeNode, context)
    return GraphQLInputObjectType.newInputObject()
        .name("$inputTypeConfiguration${shapeName}Input")
        .withAppliedDirective(
            isDirective.toAppliedDirective().transform {
                it.argument(
                    GraphQLAppliedDirectiveArgument.newArgument().name("class").type(Scalars.GraphQLString)
                        .valueProgrammatic(shape.targets.find { it.targetType == TargetType.targetClass }?.`object`?.uri)
                )
            }
        )
        .fields(shape.propertyShapes
            // Only include literal properties
            .filter { property -> property.shapeGraph.getReference(property.shapeNode, SHACL.datatype) != null }
            .map { property ->
                val propName =
                    property.shapeGraph.getString(property.shapeNode, SHACL.name) ?: propertyNameFromPath(
                        property.path.toString().removeSurrounding("<", ">")
                    )
                val description = property.shapeGraph.getString(property.shapeNode, SHACL.description)
                val collection =
                    property.constraints.any { it is MaxCount && it.maxCount > 1 } || property.constraints.none { it is MaxCount }
                val minCount = property.constraints.find { it is MinCount }?.let { (it as MinCount).minCount } ?: 0
                val dataType = generateScalar(property.shapeGraph.getReference(property.shapeNode, SHACL.datatype)!!)
                val effectiveType = when (inputTypeConfiguration) {
                    InputTypeConfiguration.CREATE_TYPE -> if (collection) {
                        GraphQLNonNull.nonNull(GraphQLList.list(GraphQLNonNull.nonNull(dataType)))
                    } else {
                        if (minCount > 0) GraphQLNonNull(dataType) else dataType
                    }

                    InputTypeConfiguration.UPDATE_TYPE -> if (collection) {
                        GraphQLList.list(GraphQLNonNull.nonNull(dataType))
                    } else {
                        dataType
                    }
                }
                GraphQLInputObjectField.newInputObjectField()
                    .name(propName)
                    .description(description)
                    .type(effectiveType)
                    .withAppliedDirective(
                        propertyDirective.toAppliedDirective().transform {
                            it.argument(
                                GraphQLAppliedDirectiveArgument.newArgument().name("iri").type(Scalars.GraphQLString)
                                    .valueProgrammatic(property.path)
                            )
                        }
                    )
                    .build()
            }
        )
        .build()
}

fun generateFieldDefinition(property: PropertyShape, context: ParseContext): GraphQLFieldDefinition? {
    val propName =
        property.shapeGraph.getString(property.shapeNode, SHACL.name) ?: propertyNameFromPath(
            property.path.toString().removeSurrounding("<", ">")
        )
    val description = property.shapeGraph.getString(property.shapeNode, SHACL.description)
    val collection =
        property.constraints.any { it is MaxCount && it.maxCount > 1 } || property.constraints.none { it is MaxCount }
    val minCount = property.constraints.find { it is MinCount }?.let { (it as MinCount).minCount } ?: 0
    val dataType = property.shapeGraph.getReference(property.shapeNode, SHACL.datatype)
    val classRef = property.shapeGraph.getReference(property.shapeNode, SHACL.class_)
    val rawGraphQLType = if (dataType != null) {
        generateScalar(dataType)
    } else if (classRef != null) {
        val matchingShape = context.allShapes.getMatchingShape(classRef)
        if (matchingShape != null) {
            GraphQLTypeReference(parseName(matchingShape.shapeNode, context))
        } else {
            println("  --> Property '$propName' for Shape <${context.nodeShape?.shapeNode?.uri}> has no matching Shape definition (for sh:class <${classRef.uri}>) and will be ignored!")
            null
        }
    } else {
        println("  --> Property '$propName' for Shape ${context.nodeShape?.shapeNode?.uri} does not provide a dataType or class and will be ignored!")
        null
    }

    return if (rawGraphQLType != null) {
        val graphQLType = if (collection) {
            GraphQLNonNull.nonNull(GraphQLList.list(GraphQLNonNull.nonNull(rawGraphQLType)))
        } else {
            if (minCount > 0) GraphQLNonNull(rawGraphQLType) else rawGraphQLType
        }
        GraphQLFieldDefinition.newFieldDefinition()
            .name(propName)
            .description(description)
            .type(graphQLType)
            .withAppliedDirective(
                propertyDirective.toAppliedDirective().transform {
                    it.argument(
                        GraphQLAppliedDirectiveArgument.newArgument().name("iri").type(Scalars.GraphQLString)
                            .valueProgrammatic(property.path)
                    )
                }
            )
            .build()
    } else {
        null
    }
}

fun generateScalar(dataType: Node_URI): GraphQLScalarType {
    return when (dataType) {
        XSD.decimal, XSD.xfloat, XSD.xdouble -> Scalars.GraphQLFloat
        XSD.integer, XSD.positiveInteger, XSD.nonPositiveInteger, XSD.negativeInteger, XSD.nonNegativeInteger, XSD.unsignedInt, XSD.xint -> Scalars.GraphQLInt
        XSD.xboolean -> Scalars.GraphQLBoolean
        else -> Scalars.GraphQLString
    }
}

fun parseName(subject: Node, context: ParseContext): String {
    val shapeImport = context.getImportDefinition(subject)
    val uri = URI(subject.uri)
    // If the URI has a fragment, use fragment, otherwise use the last path segment
    val name = uri.fragment ?: uri.path.substringAfterLast("/")
    return "${shapeImport.typePrefix.capitalizeFirstLetter()}${name.capitalizeFirstLetter()}"
}

@OptIn(ExperimentalStdlibApi::class)
fun Graph.getString(subject: Node, predicate: Node): String? {
    return this.find(subject, predicate, null)
        .nextOptional().getOrNull()?.`object`?.takeIf { it.isLiteral }?.literalValue?.toString()
}

@OptIn(ExperimentalStdlibApi::class)
fun Graph.getReference(subject: Node, predicate: Node): Node_URI? {
    return this.find(subject, predicate, null)
        .nextOptional().getOrNull()?.`object`?.takeIf { it.isURI }?.let { it as Node_URI }
}

fun Shapes.getMatchingShape(shClass: Node_URI): NodeShape? {
    return this.filter { it.isNodeShape }.find {
        it.targets.any { target ->
            target.targetType == TargetType.targetClass && target.`object` == shClass
        }
    }?.let { it as NodeShape }
}

fun propertyNameFromPath(path: String): String {
    return if (path.contains("#")) {
        path.substringAfterLast("#")
    } else {
        path.substringAfterLast("/")
    }
}

data class ParseContext(
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

enum class InputTypeConfiguration(val typePrefix: String) {
    CREATE_TYPE("Create"), UPDATE_TYPE("Update")
}

private fun decapitalize(str: String): String {
    return str.replaceFirstChar { it.lowercase(Locale.getDefault()) }
}


