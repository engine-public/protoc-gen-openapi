import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class PetStoreTest :
    FunSpec({

        val request = PetStoreTest::class.java.getResourceAsStream("/code-generator-request.binpb").shouldNotBeNull()
        val response = ProtocGenOpenAPI.from(request) {
            merge = false
            validateOutput = true
            caseStrategy = ProtocGenOpenAPI.Options.CaseStrategy.CAMEL_CASE
        }.compile()
        val generatedFile = response.fileList.find { it.name == "swagger.api.PetService.openapi.json" }.shouldNotBeNull()
        val mapper = ObjectMapper()
        val json = mapper.readTree(generatedFile.content)
        assertSoftly = true

        test("has no validation errors") {
            response.hasError() shouldBe false
            response.error.shouldBe("")
        }

        test("matches petstore.openapi.json") {
            val expectedStream = PetStoreTest::class.java.getResourceAsStream("swagger-api.petstore.openapi.yaml").shouldNotBeNull()
            val expected = YAMLMapper().readTree(expectedStream)
            assertSoftly {
                collectJsonDiffs(
                    expected,
                    json,
                    "$.openapi", // our compiler is 3.1.0, and the comp is 3.0.4, but this is intentional and not a problem
                ).forEach { (path, exp, act) ->
                    withClue("at $path — expected: $exp, actual: $act") {
                        act shouldBe exp
                    }
                }
            }
        }
    })
