package be.solidlab.sdx.gradle.plugin

import com.apollographql.apollo3.compiler.capitalizeFirstLetter
import graphql.GraphQL
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
import java.io.File
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
    .validLocations(Introspection.DirectiveLocation.OBJECT, Introspection.DirectiveLocation.INPUT_OBJECT)
    .build()
val propertyDirective: GraphQLDirective = GraphQLDirective.newDirective()
    .name("property")
    .argument(GraphQLArgument.newArgument().name("iri").type(Scalars.GraphQLString))
    .validLocations(
        Introspection.DirectiveLocation.FIELD_DEFINITION,
        Introspection.DirectiveLocation.INPUT_FIELD_DEFINITION
    )
    .build()

object SHACLToGraphQL {
    fun getSchema(shaclDir: File, shapeImports: List<ShapeImport>): String {
        println("Looking at Shape definitions in ${shaclDir.path}")
        val graph = GraphFactory.createDefaultGraph()
        shapeImports.forEach { shapeImport ->
            println("  --> Importing ${shapeImport.importUrl} into graph")
            val shapeSubGraph = GraphFactory.createDefaultGraph()
            RDFDataMgr.read(shapeSubGraph, File(shaclDir, shapeImport.getTargetFileName()).toURI().toString())
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
            .mutation(generateMutations(context, graphQLTypes.associateBy { it.name }))
            .additionalTypes(graphQLTypes.plus(graphQLInputTypes))
            .additionalDirectives(setOf(isDirective, propertyDirective, identifierDirective))
            .build()
        val schemaPrinter = SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeSchemaElement {
            it !is GraphQLDirective || setOf("property", "is", "identifier").contains(it.name)
        })
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

fun generateMutations(context: ParseContext, typesMap: Map<String, GraphQLObjectType>): GraphQLObjectType {
    return GraphQLObjectType.newObject()
        .name("Mutation")
        .fields(context.allShapes.flatMap { shape ->
            shape as NodeShape
            val shapeName = parseName(shape.shapeNode, context)
            listOf(
                // Create mutation
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("create$shapeName")
                    .description("Create a new instance of $shapeName")
                    .argument(
                        GraphQLArgument.newArgument().name("input")
                            .type(GraphQLNonNull.nonNull(GraphQLTypeReference.typeRef("${InputTypeConfiguration.CREATE_TYPE.typePrefix}${shapeName}Input")))
                    )
                    .type(GraphQLNonNull.nonNull(GraphQLTypeReference.typeRef(shapeName)))
                    .build(),
                // Per-instance specific mutations
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("mutate$shapeName")
                    .description("Access update/delete mutations for a specific instance of $shapeName")
                    .argument(
                        GraphQLArgument.newArgument().name("id").type(GraphQLNonNull.nonNull(Scalars.GraphQLID))
                    )
                    .type(
                        GraphQLObjectType.newObject()
                            .name("${shapeName}Mutation")
                            .fields(
                                listOf(
                                    GraphQLFieldDefinition.newFieldDefinition()
                                        .name("update")
                                        .description("Perform an update mutation based on the given input type.")
                                        .argument(
                                            GraphQLArgument.newArgument().name("input")
                                                .type(GraphQLNonNull.nonNull(GraphQLTypeReference.typeRef("${InputTypeConfiguration.UPDATE_TYPE.typePrefix}${shapeName}Input")))
                                        )
                                        .type(GraphQLNonNull.nonNull(GraphQLTypeReference.typeRef(shapeName)))
                                        .build(),
                                    GraphQLFieldDefinition.newFieldDefinition()
                                        .name("delete")
                                        .description("Delete this instance of $shapeName")
                                        .type(GraphQLNonNull.nonNull(GraphQLTypeReference.typeRef(shapeName)))
                                        .build(),
                                    *typesMap[shapeName]!!.fields.filter { !it.type.rawType().isScalar() }
                                        .flatMap { fieldDef ->
                                            val collection =
                                                GraphQLTypeUtil.isList(GraphQLTypeUtil.unwrapNonNull(fieldDef.type))
                                            val refName = (fieldDef.type.rawType() as GraphQLTypeReference).name
                                            val addPrefix = if (collection) "add" else "set"
                                            val addDescription =
                                                if (collection) "Add an instance of $refName to this $shapeName" else "Set the $refName for this $shapeName"
                                            val removePrefix = if (collection) "remove" else "clear"
                                            val removeDescription =
                                                if (collection) "Remove the specified instance of $refName from this $shapeName" else "Clear the $refName from this $shapeName"
                                            listOf(
                                                GraphQLFieldDefinition.newFieldDefinition()
                                                    .name("$addPrefix$refName")
                                                    .description(addDescription)
                                                    .argument(
                                                        GraphQLArgument.newArgument().name("input")
                                                            .type(GraphQLNonNull.nonNull(GraphQLTypeReference.typeRef("${InputTypeConfiguration.CREATE_TYPE.typePrefix}${refName}Input")))
                                                    )
                                                    .type(GraphQLNonNull.nonNull(GraphQLTypeReference.typeRef(shapeName)))
                                                    .build(),
                                                GraphQLFieldDefinition.newFieldDefinition()
                                                    .name("$removePrefix$refName")
                                                    .description(removeDescription)
                                                    .arguments(
                                                        if (collection) listOf(
                                                            GraphQLArgument.newArgument().name("id")
                                                                .type(GraphQLNonNull.nonNull(Scalars.GraphQLID)).build()
                                                        ) else emptyList()
                                                    )
                                                    .type(GraphQLNonNull.nonNull(GraphQLTypeReference.typeRef(shapeName)))
                                                    .build(),
                                                GraphQLFieldDefinition.newFieldDefinition()
                                                    .name("link$refName")
                                                    .description("Create a relation of type $refName between the instance of $shapeName and the given ID")
                                                    .argument(
                                                        GraphQLArgument.newArgument().name("id")
                                                            .type(GraphQLNonNull.nonNull(Scalars.GraphQLID))
                                                    )
                                                    .type(GraphQLNonNull.nonNull(GraphQLTypeReference.typeRef(shapeName)))
                                                    .build(),
                                                GraphQLFieldDefinition.newFieldDefinition()
                                                    .name("unlink$refName")
                                                    .description("Remove the relation of type $refName between the instance of $shapeName and the given ID (if it exists)")
                                                    .argument(
                                                        GraphQLArgument.newArgument().name("id")
                                                            .type(GraphQLNonNull.nonNull(Scalars.GraphQLID))
                                                    )
                                                    .type(GraphQLNonNull.nonNull(GraphQLTypeReference.typeRef(shapeName)))
                                                    .build()
                                            )
                                        }.toTypedArray()
                                )
                            ).build()
                    )
                    .build()
            )
        })
        .build()
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
    val fixedFields = if (inputTypeConfiguration == InputTypeConfiguration.CREATE_TYPE) {
        listOf(
            GraphQLInputObjectField.newInputObjectField()
                .name("id").type(Scalars.GraphQLID)
                .description("Optional URI to use as an identifier for the new instance. One of the 'id' or 'slug' fields must be set!")
                .build(),
            GraphQLInputObjectField.newInputObjectField()
                .name("slug")
                .description("Optional slug that is combined with the context of the request to generate an identifier for the new instance. One of the 'id' or 'slug' fields must be set!")
                .type(Scalars.GraphQLString)
                .build()
        )
    } else {
        emptyList()
    }
    return GraphQLInputObjectType.newInputObject()
        .name("${inputTypeConfiguration.typePrefix}${shapeName}Input")
        .withAppliedDirective(
            isDirective.toAppliedDirective().transform {
                it.argument(
                    GraphQLAppliedDirectiveArgument.newArgument().name("class").type(Scalars.GraphQLString)
                        .valueProgrammatic(shape.targets.find { it.targetType == TargetType.targetClass }?.`object`?.uri)
                )
            }
        )
        .fields(fixedFields.plus(shape.propertyShapes
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
        ))
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

// Get the raw type for a specific type (not wrapped in non-null or list)
private fun GraphQLType.rawType(): GraphQLType {
    return if (GraphQLTypeUtil.isNonNull(this)) {
        GraphQLTypeUtil.unwrapNonNull(this).rawType()
    } else if (GraphQLTypeUtil.isList(this)) {
        GraphQLTypeUtil.unwrapOne(this).rawType()
    } else {
        this
    }
}

private fun GraphQLType.isScalar(): Boolean {
    return GraphQLTypeUtil.isScalar(this)
}
