package be.solidlab.sdx.gradle.plugin.schemagen

import be.solidlab.sdx.client.commons.graphql.isScalar
import be.solidlab.sdx.client.commons.graphql.rawType
import graphql.Scalars
import graphql.schema.*
import org.apache.jena.shacl.engine.TargetType
import org.apache.jena.shacl.engine.constraint.MaxCount
import org.apache.jena.shacl.engine.constraint.MinCount
import org.apache.jena.shacl.parser.NodeShape
import org.apache.jena.shacl.parser.PropertyShape
import org.apache.jena.shacl.vocabulary.SHACL

internal fun generateMutations(context: ParseContext, typesMap: Map<String, GraphQLObjectType>): GraphQLObjectType {
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
                    .type(generateMutationType(shapeName, typesMap))
                    .build()
            )
        })
        .build()
}

internal fun generateMutationType(
    shapeName: String, typesMap: Map<String, GraphQLObjectType>
): GraphQLObjectType {
    return GraphQLObjectType.newObject()
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
}

internal fun generateInputType(
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
            .map { property -> generateInputField(property, inputTypeConfiguration) }
        ))
        .build()
}

internal fun generateInputField(
    property: PropertyShape,
    inputTypeConfiguration: InputTypeConfiguration
): GraphQLInputObjectField {
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
    return GraphQLInputObjectField.newInputObjectField()
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

