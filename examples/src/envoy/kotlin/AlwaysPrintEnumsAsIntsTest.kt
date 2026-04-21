import com.engine.protoc.openapi.example.envoy.Greeting
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import tools.jackson.module.kotlin.readValue

class AlwaysPrintEnumsAsIntsTest : EnvoyTestBase(GrpcJsonTranscoder(printOptions = GrpcJsonTranscoder.PrintOptions(alwaysPrintEnumsAsInts = true))) {
    init {
        context("confirm envoy behaviors") {
            data class TestData(val description: String, val input: Any, val expectedGreetingUsed: Greeting = Greeting.GREETING_HELLO, val shouldFail: Boolean = false)
            withData<TestData>(
                { it.description },
                TestData("integer (hello)", Greeting.GREETING_HELLO.number),
                TestData("canonical enum name (hello)", Greeting.GREETING_HELLO.name),
                TestData("integer (hi)", Greeting.GREETING_HI.number, Greeting.GREETING_HI),
                TestData("canonical enum name (hi)", Greeting.GREETING_HI.name, Greeting.GREETING_HI),
                TestData("invalid enum", "foo", shouldFail = true),
            ) {
                val request = mapOf("yourName" to "World", "greetingType" to it.input)
                val response = postJson(
                    "/engine.protoc.openapi.example.envoy.HelloService/SayHello",
                    request,
                )
                if (it.shouldFail) {
                    response.statusCode() shouldNotBe 200
                } else {
                    val responseBody = jsonMapper.readValue<Map<String, Any>>(response.body())
                    responseBody["greetingUsed"] shouldBe it.expectedGreetingUsed.number
                }
            }
        }
    }
}
