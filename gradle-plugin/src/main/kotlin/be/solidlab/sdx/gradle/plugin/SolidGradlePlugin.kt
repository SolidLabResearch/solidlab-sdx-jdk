package be.solidlab.sdx.gradle.plugin

import be.ugent.solidlab.shapeshift.shacl2graphql.SHACLToGraphQL.getSchema
import be.ugent.solidlab.shapeshift.shacl2graphql.ShapeConfig
import be.ugent.solidlab.shapeshift.shacl2graphql.Context
import com.apollographql.apollo3.gradle.internal.DefaultApolloExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.net.URL
import java.nio.file.Files

interface SdxExtension {

    //URL of the Shape Catalog to be used
    val catalogURL : Property<String>

    // Shorthand for importShapes: imports the Shape packages with the specified catalog id using the default options
    val importShapesFromCatalog: ListProperty<String>

    // Shorthand for importShapes: imports the Shapes from the specified URLs using the default options.
    val importShapesFromURL: ListProperty<String>

    // List of the Shape imports
    val importShapes: ListProperty<ShapeImport>

    // Name of the package to use for the generated classes.
    val packageName: Property<String>
//    val importShapes: ListProperty<ShapeImportResource>
}

data class ShapeImport(
    // Id of the shape package in the configured shape catalog
    val catalogId: String? = null,
    // URL of the Shape package or file to import
    val importUrl: String? = null,
    // Set of Shapes to ignore, identified by their URIs.
    val exclude: Set<String> = emptySet(),
    // Determines if mutations should be generated for the Shapes
    val generateMutations: Boolean = true,
    // An optional prefix to add to types generated from the to be imported Shapes, can help to solve conflicts.
    val typePrefix: String = "",
    // An optional filename to use for the downloaded Shape import, can help to solve conflicts.
    val shapeFileName: String? = null
)


class SolidGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("sdx", SdxExtension::class.java)

        project.task("sdxBuild") {
            it.doFirst {
                println("")
                val shapeImports = extension.importShapesFromURL.get().map { url -> ShapeImport(importUrl = url) }
                    .plus(extension.importShapesFromCatalog.get().map { id -> ShapeImport( catalogId = id) })
                    .plus(extension.importShapes.get())


                val shapeConfigs = shapeImports
                .associateBy { si -> si.importUrl ?: si.catalogId!! }
                .mapValues {(_, value) ->
                    ShapeConfig(value.generateMutations)
                }
                println("Installing shapes and generating GraphQL schema from installed Shapes...")
                val graphqlDir = project.layout.projectDirectory.dir("src/main/graphql")
                graphqlDir.asFile.mkdirs()
                val schema = getSchema(Context(extension.catalogURL.orNull, false, shapeConfigs))
                Files.writeString(graphqlDir.file("schema.graphqls").asFile.toPath(), schema)
            }
        }

        project.afterEvaluate {
            project.pluginManager.apply("com.apollographql.apollo3")
            project.extensions.configure<DefaultApolloExtension>("apollo") {
                it.packageName.set(extension.packageName.get())
            }
        }
    }
}


