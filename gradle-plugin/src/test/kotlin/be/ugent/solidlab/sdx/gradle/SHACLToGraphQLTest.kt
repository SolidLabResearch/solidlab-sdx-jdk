package be.ugent.solidlab.sdx.gradle


import be.ugent.solidlab.shapeshift.shacl2graphql.SHACLToGraphQL
import be.ugent.solidlab.shapeshift.shacl2graphql.ShapeConfig
import be.ugent.solidlab.shapeshift.shacl2graphql.Context
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class SHACLToGraphQLTest {

    @Test
    fun testBasicShapeConversion() {
        val result = SHACLToGraphQL.getSchema(Context(
            null, false,
            mapOf(
                Pair(
                    "file://${Paths.get("").toAbsolutePath()}/src/test/resources/contact-SHACL.ttl",
                    ShapeConfig(false, listOf())
                )
            )
        ))
        val resultFile = File("src/test/resources/schema.graphqls")
        resultFile.delete()
        Files.writeString(resultFile.toPath(), result)
    }

}
