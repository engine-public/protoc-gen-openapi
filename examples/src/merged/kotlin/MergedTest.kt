import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class MergedTest :
    FunSpec({

        assertSoftly = true

        val request = MergedTest::class.java.getResourceAsStream("/code-generator-request.binpb").shouldNotBeNull()
        val response = ProtocGenOpenAPI.from(request) {
            merge = true
            validateOutput = true
            autoTagServices = true
        }.compile()
        val generatedFile = response.fileList.find { it.name == "engine.protoc.openapi.example.merged.openapi.json" }.shouldNotBeNull()
        val mapper = ObjectMapper()
        val json = mapper.readTree(generatedFile.content)

        val expected = MergedTest::class.java.getResourceAsStream("merged.openapi.json").shouldNotBeNull().reader().readText()

        test("validate reference file") {
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

            oasSchema.validate(expected, InputFormat.YAML) { ctx ->
                ctx.executionConfig { cfg -> cfg.formatAssertionsEnabled(true) }
            }.shouldBeEmpty()
        }

        test("has no validation errors") {
            response.hasError() shouldBe false
            response.error.shouldBe("")
        }

        test("matches reference OAS spec") {
            val expected = YAMLMapper().readTree(expected)
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
