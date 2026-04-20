import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.fasterxml.jackson.databind.ObjectMapper
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

class ConventionsTest :
    FunSpec({

        assertSoftly = true

        val request =
            ConventionsTest::class.java.getResourceAsStream("/code-generator-request.binpb").shouldNotBeNull()
        val response =
            ProtocGenOpenAPI.from(request) {
                // The output omits info.version — that field requires an engine annotation and
                // this example deliberately uses only google.api.http.  OAS schema validation is
                // therefore skipped; structure is verified by the reference-file comparison below.
                validateOutput = false
            }.compile()

        val mapper = ObjectMapper()
        val generatedFile =
            response.fileList
                .find { it.name == "engine.protoc.openapi.example.conventions.Greeter.openapi.json" }
                .shouldNotBeNull()
        val json = mapper.readTree(generatedFile.content)
        val expected =
            ConventionsTest::class.java
                .getResourceAsStream("/engine.protoc.openapi.example.conventions.Greeter.openapi.json")
                .shouldNotBeNull()
                .reader()
                .readText()

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

            oasSchema.validate(expected, InputFormat.JSON) { ctx ->
                ctx.executionConfig { cfg -> cfg.formatAssertionsEnabled(true) }
            }.shouldBeEmpty()
        }

        test("has no errors") {
            response.hasError() shouldBe false
            response.error shouldBe ""
        }

        test("matches reference output") {
            assertSoftly {
                collectJsonDiffs(
                    mapper.readTree(expected),
                    json,
                    // ignore the version, because no annotation is specified to provide
                    // version and the default version option is not utilized.
                    "$.info.version",
                ).forEach { (path, exp, act) ->
                    withClue("at $path — expected: $exp, actual: $act") {
                        act shouldBe exp
                    }
                }
            }
        }
    })
