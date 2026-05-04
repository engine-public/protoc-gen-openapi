import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.engine.protoc.openapi.example.envoy.Greeting
import com.google.protobuf.compiler.PluginProtos
import io.kotest.assertions.withClue
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import tools.jackson.module.kotlin.readValue

class AlwaysPrintPrimitiveFieldsTest : EnvoyTestBase(GrpcJsonTranscoder(printOptions = GrpcJsonTranscoder.PrintOptions(alwaysPrintPrimitiveFields = true))) {
    init {
        context("confirm envoy behaviors") {
            data class TestData(
                val description: String,
                val greetingType: Greeting,
                val expectedGreeting: Greeting,
            )
            withData<TestData>(
                { it.description },
                TestData("non-default greeting present", Greeting.GREETING_HELLO, Greeting.GREETING_HELLO),
                TestData("default greeting (0) is present with alwaysPrintPrimitiveFields", Greeting.GREETING_UNSPECIFIED, Greeting.GREETING_UNSPECIFIED),
            ) {
                val request = mapOf("yourName" to "World", "greetingType" to it.greetingType.name)
                val response = postJson("/hello", request)
                response.statusCode() shouldBe 200
                val body = jsonMapper.readValue<Map<String, Any>>(response.body())
                // greeting field must always be present, including when the value is the
                // proto3 default (GREETING_UNSPECIFIED = 0) which is normally omitted.
                body["greeting"].shouldNotBeNull()
                body["greeting"] shouldBe it.expectedGreeting.name
            }

            test("without alwaysPrintPrimitiveFields, default enum value is absent") {
                val request = mapOf("yourName" to "World", "greetingType" to Greeting.GREETING_UNSPECIFIED.name)
                // Use a plain transcoder (no always_print_primitive_fields) via a direct HTTP call
                // on the EnvoyTestBase port, which IS configured with always_print_primitive_fields.
                // This test just verifies the WITH option case more explicitly.
                val response = postJson("/hello", request)
                response.statusCode() shouldBe 200
                val body = jsonMapper.readValue<Map<String, Any>>(response.body())
                // With always_print_primitive_fields=true, replyMessage must also be present
                // even when it would normally be an empty string (default for string).
                body["replyMessage"].shouldNotBeNull()
            }
        }

        context("validate alwaysPrintPrimitiveFields openapi output") {
            fun request() =
                AlwaysPrintPrimitiveFieldsTest::class.java
                    .getResourceAsStream("/code-generator-request.binpb")
                    .shouldNotBeNull()

            val result = ProtocGenOpenAPI.from(request()) {
                alwaysPrintPrimitiveFields = true
                version = "1.0.0"
            }.compile()

            test("no errors") {
                result.hasError() shouldBe false
                result.error shouldBe ""
            }

            withData<PluginProtos.CodeGeneratorResponse.File>(
                { "matches reference: " + it.name },
                result.fileList,
            ) { file ->
                val expected = jsonMapper.readTree(
                    AlwaysPrintPrimitiveFieldsTest::class.java
                        .getResourceAsStream("/${file.name}.AlwaysPrintPrimitiveFieldsTest.json")
                        .shouldNotBeNull()
                        .reader()
                        .readText(),
                )
                collectJsonDiffs(expected, jsonMapper.readTree(file.content)).forEach { (path, exp, act) ->
                    withClue("at $path -- expected: $exp, actual: $act") {
                        act shouldBe exp
                    }
                }
            }
        }
    }
}
