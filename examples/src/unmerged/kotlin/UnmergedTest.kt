import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.google.protobuf.compiler.PluginProtos
import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import tools.jackson.databind.ObjectMapper
import java.io.File

class UnmergedTest :
    FunSpec({

        assertSoftly = true

        val request = UnmergedTest::class.java.getResourceAsStream("/code-generator-request.binpb").shouldNotBeNull()
        val response = ProtocGenOpenAPI.from(request) {
            merge = false
            validateOutput = true
        }.compile()
        val mapper = ObjectMapper()

        val oasSchema by lazy {
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12) {
                it.schemaIdResolvers {
                    it.mapPrefix(
                        "https://spec.openapis.org/oas/3.1",
                        "classpath:schemas/spec.openapis.org/oas/3.1",
                    )
                }
            }
                .getSchema(SchemaLocation.of("https://spec.openapis.org/oas/3.1/schema-base/2022-10-07"))
        }

        val expectedFiles = listOf(
            "engine.protoc.openapi.example.unmerged.DooDadService.openapi.json",
            "engine.protoc.openapi.example.unmerged.openapi.json",
            "engine.protoc.openapi.example.unmerged.SecondaryService.openapi.json",
        )

        test("the right files were generated") {
            response.fileList.map { it.name } shouldContainExactlyInAnyOrder expectedFiles
        }

        withData<PluginProtos.CodeGeneratorResponse.File>({ "validate reference: " + it.name }, response.fileList) { file ->
            val expected = UnmergedTest::class.java.getResourceAsStream(file.name).shouldNotBeNull().reader().readText()
            oasSchema.validate(expected, InputFormat.YAML) { ctx ->
                ctx.executionConfig { cfg -> cfg.formatAssertionsEnabled(true) }
            }.shouldBeEmpty()
        }

        test("has no validation errors") {
            response.hasError() shouldBe false
            response.error shouldBe ""
        }

        withData<PluginProtos.CodeGeneratorResponse.File>({ "validate output: " + it.name }, response.fileList) { file ->
            val json = mapper.readTree(file.content)
            val expected = mapper.readTree(UnmergedTest::class.java.getResourceAsStream(file.name).shouldNotBeNull().reader().readText())
            assertSoftly {
                collectJsonDiffs(
                    expected,
                    json,
                ).forEach { (path, exp, act) ->
                    withClue("at $path — expected: $exp, actual: $act") {
                        act shouldBe exp
                    }
                }
            }
        }
    })
