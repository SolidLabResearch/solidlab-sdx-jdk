package be.solidlab.sdx.gradle.plugin

import be.solidlab.sdx.gradle.plugin.schemagen.SHACLToGraphQL
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class SHACLToGraphQLTest {

    @Test
    fun testBasicShapeConversion() {
        val result = SHACLToGraphQL.getSchema(
            File("src/test/resources"),
            listOf(
                ShapeImport(
                    importUrl = "https://example.com/contact-SHACL.ttl",
                    shapeFileName = "contact-SHACL.ttl"
                )
            )
        )
        val resultFile = File("src/test/resources/schema.graphqls")
        resultFile.delete()
        Files.writeString(resultFile.toPath(), result)
    }

}
