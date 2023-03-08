package be.solidlab.sdx.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.nio.file.Files
import com.apollographql.apollo3.gradle.internal.DefaultApolloExtension

const val DEFAULT_CATALOG = "https://catalog.solidlab.be/"

interface SdxExtension {
    val source: Property<String>
    val additionalSources: ListProperty<String>
    val importShapesFromURL: ListProperty<String>

    val packageName: Property<String>
//    val importShapes: ListProperty<ShapeImportResource>
}
//
//data class ShapeImportResource(
//    val packageId: String,
//    val versionId: String
//)

interface ApolloExtension {
    val packageName: Property<String>
}

class SolidGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("sdx", SdxExtension::class.java)

        project.task("sdxBuild") {
            it.doFirst {
                val additionalSources =
                    extension.additionalSources.orNull?.takeIf { it.isNotEmpty() }
                        ?.let { " + ${it.joinToString()} (additional)" } ?: ""
                println("Installing shapes using sources: ${extension.source.getOrElse(DEFAULT_CATALOG)} (main)$additionalSources")
                val shapeDir = project.layout.projectDirectory.dir("src/main/shapes")
                shapeDir.asFile.mkdirs()
                extension.importShapesFromURL.get().map { URL(it) }.forEach { shapeImportURL ->
                    println("  Starting download for $shapeImportURL")
                    downloadFile(
                        shapeImportURL,
                        shapeDir.file(shapeImportURL.path.substringAfterLast("/")).asFile.absolutePath
                    )
                    println("  --> Done!")
                }
                println("Generating GraphQL schema from installed Shapes")
                val graphqlDir = project.layout.projectDirectory.dir("src/main/graphql")
                graphqlDir.asFile.mkdirs()
                val schema = SHACLToGraphQL.getSchema(shapeDir)
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


