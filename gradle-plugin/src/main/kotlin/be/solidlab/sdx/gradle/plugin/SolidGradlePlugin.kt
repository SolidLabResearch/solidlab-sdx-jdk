package be.solidlab.sdx.gradle.plugin

import be.solidlab.sdx.gradle.plugin.schemagen.SHACLToGraphQL
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.nio.file.Files
import com.apollographql.apollo3.gradle.internal.DefaultApolloExtension

interface SdxExtension {
    // Shorthand for importShapes: imports the Shapes from the specified URLs using the default options.
    val importShapesFromURL: ListProperty<String>

    // List of the Shape imports
    val importShapes: ListProperty<ShapeImport>

    // Name of the package to use for the generated classes.
    val packageName: Property<String>
//    val importShapes: ListProperty<ShapeImportResource>
}

data class ShapeImport(
    // URL of the Shape package or file to import
    val importUrl: String,
    // Set of Shapes to ignore, identified by their URIs.
    val exclude: Set<String> = emptySet(),
    // Determines if mutations should be generated for the Shapes
    val generateMutations: Boolean = true,
    // An optional prefix to add to types generated from the to be imported Shapes, can help to solve conflicts.
    val typePrefix: String = "",
    // An optional filename to use for the downloaded Shape import, can help to solve conflicts.
    val shapeFileName: String? = null
) {
    fun getTargetFileName(): String {
        val importUrl = URL(importUrl)
        return this.shapeFileName ?: (importUrl.path.substringAfterLast("/"))
    }
}

class SolidGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("sdx", SdxExtension::class.java)

        project.task("sdxBuild") {
            it.doFirst {
                println("Installing shapes...")
                val shapeDir = project.layout.buildDirectory.dir("sdx/shapes")
                shapeDir.get().asFile.mkdirs()
                val shapeImports = extension.importShapesFromURL.get().map { url -> ShapeImport(importUrl = url) }
                    .plus(extension.importShapes.get())

                // Download the Shapes
                shapeImports.forEach { shapeImport ->
                    val shapeImportURL = URL(shapeImport.importUrl)
                    println("  Starting download for $shapeImportURL")
                    downloadFile(
                        URL(shapeImport.importUrl),
                        shapeDir.get().file(shapeImport.getTargetFileName()).asFile.absolutePath
                    )
                    println("  --> Done!")
                }

                println("Generating GraphQL schema from installed Shapes...")
                val graphqlDir = project.layout.projectDirectory.dir("src/main/graphql")
                graphqlDir.asFile.mkdirs()
                val schema = SHACLToGraphQL.getSchema(shapeDir.get().asFile, shapeImports)
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

private fun downloadFile(url: URL, outputFileName: String) {
    url.openStream().use {
        Channels.newChannel(it).use { rbc ->
            FileOutputStream(outputFileName).use { fos ->
                fos.channel.transferFrom(rbc, 0, Long.MAX_VALUE)
            }
        }
    }
}


