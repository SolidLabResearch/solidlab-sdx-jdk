package be.ugent.solidlab.sdx.client.commons.graphql

import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeUtil

// Get the raw type for a specific type (not wrapped in non-null or list)
fun GraphQLType.rawType(): GraphQLType {
    return if (GraphQLTypeUtil.isNonNull(this)) {
        GraphQLTypeUtil.unwrapNonNull(this).rawType()
    } else if (GraphQLTypeUtil.isList(this)) {
        GraphQLTypeUtil.unwrapOne(this).rawType()
    } else {
        this
    }
}

fun GraphQLType.unwrapNonNull(): GraphQLType {
    return if (GraphQLTypeUtil.isNonNull(this)) {
        GraphQLTypeUtil.unwrapNonNull(this)
    } else {
        this
    }
}

fun GraphQLType.isScalar(): Boolean {
    return GraphQLTypeUtil.isScalar(this)
}

fun GraphQLType.isCollection(): Boolean {
    return GraphQLTypeUtil.isList(this)
}
