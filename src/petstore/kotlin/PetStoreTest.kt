import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

private data class JsonDiff(val path: String, val expected: JsonNode?, val actual: JsonNode?)

private fun collectJsonDiffs(
    expectedResource: String,
    actual: JsonNode,
    vararg ignoredPath: String,
): List<JsonDiff> {
    val expectedStream = PetStoreTest::class.java.getResourceAsStream("swagger-api.petstore.openapi.yaml").shouldNotBeNull()
    val expected = if (expectedResource.endsWith(".yaml") || expectedResource.endsWith(".yml")) {
        YAMLMapper().readTree(expectedStream)
    } else if (expectedResource.endsWith(".json")) {
        ObjectMapper().readTree(expectedStream)
    } else {
        TODO()
    }
    val ignoredPaths = ignoredPath.toSet()
    return collectJsonDiffs("$", expected, actual).filterNot {
        ignoredPaths.contains(it.path)
    }
}

private fun collectJsonDiffs(
    path: String,
    expected: JsonNode,
    actual: JsonNode,
): List<JsonDiff> {
    val diffs = mutableListOf<JsonDiff>()
    when {
        expected.isObject && actual.isObject -> {
            val allFields = (expected.fieldNames().asSequence() + actual.fieldNames().asSequence()).toSet()
            for (field in allFields) {
                val exp = expected.get(field)
                val act = actual.get(field)
                when {
                    exp == null -> diffs.add(JsonDiff("$path.$field", null, act))
                    // A missing field is semantically equivalent to an empty array in OpenAPI
                    // (e.g. `parameters: []` and omitting `parameters` are identical).
                    act == null && exp.isArray && exp.size() == 0 -> {}
                    act == null -> diffs.add(JsonDiff("$path.$field", exp, null))
                    else -> diffs.addAll(collectJsonDiffs("$path.$field", exp, act))
                }
            }
        }

        expected.isArray && actual.isArray -> {
            if (expected.size() != actual.size()) {
                diffs.add(JsonDiff(path, expected, actual))
            } else {
                for (i in 0 until expected.size()) {
                    diffs.addAll(collectJsonDiffs("$path[$i]", expected[i], actual[i]))
                }
            }
        }

        expected != actual -> diffs.add(JsonDiff(path, expected, actual))
    }
    return diffs
}

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
            assertSoftly {
                collectJsonDiffs(
                    "swagger-api.petstore.openapi.yaml",
                    json,
                    "$.openapi" // our compiler is 3.1.0, and the comp is 3.0.4, but this is intentional and not a problem
                ).forEach { (path, exp, act) ->
                    withClue("at $path — expected: $exp, actual: $act") {
                        act shouldBe exp
                    }
                }
            }
        }
    })
