import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.engine.protoc.openapi.example.envoy.Greeting
import com.google.protobuf.compiler.PluginProtos
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import tools.jackson.module.kotlin.readValue

class CaseInsensitiveEnumParsingTest : EnvoyTestBase(GrpcJsonTranscoder(caseInsensitiveEnumParsing = true)) {
    init {
        context("confirm envoy behaviors") {
            data class TestData(val description: String, val input: Any, val expectedGreetingUsed: Greeting = Greeting.GREETING_HELLO, val shouldFail: Boolean = false)
            withData<TestData>(
                { it.description },
                TestData("integer (hello)", Greeting.GREETING_HELLO.number),
                TestData("canonical enum name (hello)", Greeting.GREETING_HELLO.name),
                TestData("lowercase enum name (hello)", "greeting_hello"),
                TestData("integer (hi)", Greeting.GREETING_HI.number, Greeting.GREETING_HI),
                TestData("canonical enum name (hi)", Greeting.GREETING_HI.name, Greeting.GREETING_HI),
                TestData("lowercase enum name (hi)", "greeting_hi", Greeting.GREETING_HI),
                TestData("invalid enum", "foo", shouldFail = true),
            ) {
                val request = mapOf("yourName" to "World", "greetingType" to it.input)
                val response = postJson(
                    "/hello",
                    request,
                )
                if (it.shouldFail) {
                    response.statusCode() shouldNotBe 200
                } else {
                    val responseBody = jsonMapper.readValue<Map<String, Any>>(response.body())
                    responseBody["greeting"] shouldBe it.expectedGreetingUsed.name
                }
            }
        }

        context("validate LOWER_CASE openapi output") {
            fun request() =
                CaseInsensitiveEnumParsingTest::class.java
                    .getResourceAsStream("/code-generator-request.binpb")
                    .shouldNotBeNull()

            val result = ProtocGenOpenAPI.from(request()) {
                enumValueFormat = ProtocGenOpenAPI.Options.EnumValueFormat.LOWER_CASE
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
                    CaseInsensitiveEnumParsingTest::class.java
                        .getResourceAsStream("/${file.name}.CaseInsensitiveEnumParsingTest.json")
                        .shouldNotBeNull()
                        .reader()
                        .readText(),
                )
                jsonMapper.readTree(file.content) shouldBe expected
            }
        }
    }
}
