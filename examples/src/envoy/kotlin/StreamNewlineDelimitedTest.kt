import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.google.protobuf.compiler.PluginProtos
import io.kotest.assertions.withClue
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import tools.jackson.module.kotlin.readValue

class StreamNewlineDelimitedTest :
    EnvoyTestBase(GrpcJsonTranscoder(printOptions = GrpcJsonTranscoder.PrintOptions(streamNewlineDelimited = true))) {
    init {
        context("confirm envoy behaviors") {
            test("unary /hello returns single JSON object") {
                val response = postJson("/hello", mapOf("yourName" to "World"))
                response.statusCode() shouldBe 200
                val body = jsonMapper.readValue<Map<String, Any>>(response.body())
                body["replyMessage"].shouldNotBeNull()
            }

            test("streaming /hellos returns newline-delimited JSON objects (not an array)") {
                val response = postJson("/hellos", mapOf("yourName" to "World"))
                response.statusCode() shouldBe 200
                val body = response.body().trim()
                // With stream_newline_delimited each message is a separate line, not a JSON array
                body.startsWith("[") shouldBe false
                val lines = body.lines().filter { it.isNotBlank() }
                lines.size shouldBe 3
                lines.forEachIndexed { i, line ->
                    val msg = jsonMapper.readValue<Map<String, Any>>(line)
                    (msg["replyMessage"] as? String).shouldNotBeNull()
                    msg["replyMessage"] shouldBe "Hello, World #$i!"
                }
            }
        }

        context("validate streamNewlineDelimited openapi output") {
            fun request() =
                StreamNewlineDelimitedTest::class.java
                    .getResourceAsStream("/code-generator-request.binpb")
                    .shouldNotBeNull()

            val result = ProtocGenOpenAPI.from(request()) {
                streamNewlineDelimited = true
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
                    StreamNewlineDelimitedTest::class.java
                        .getResourceAsStream("/${file.name}.StreamNewlineDelimitedTest.json")
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
