import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.google.protobuf.compiler.PluginProtos
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import tools.jackson.databind.ObjectMapper

/**
 * Tests for the `streamSseStyleDelimited` compiler option.
 *
 * Envoy's `stream_sse_style_delimited` PrintOption was introduced after Envoy v1.33, so this
 * test validates OAS compilation output only (no live Envoy container).
 */
class StreamSseStyleDelimitedTest : FunSpec() {
    private val jsonMapper = ObjectMapper()

    init {
        context("validate streamSseStyleDelimited openapi output") {
            fun request() =
                StreamSseStyleDelimitedTest::class.java
                    .getResourceAsStream("/code-generator-request.binpb")
                    .shouldNotBeNull()

            val result = ProtocGenOpenAPI.from(request()) {
                streamSseStyleDelimited = true
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
                    StreamSseStyleDelimitedTest::class.java
                        .getResourceAsStream("/${file.name}.StreamSseStyleDelimitedTest.json")
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

        context("streamSseStyleDelimited takes precedence over streamNewlineDelimited") {
            test("when both are true, SSE content type is used for streaming responses") {
                val request = StreamSseStyleDelimitedTest::class.java
                    .getResourceAsStream("/code-generator-request.binpb")
                    .shouldNotBeNull()
                val result = ProtocGenOpenAPI.from(request) {
                    streamSseStyleDelimited = true
                    streamNewlineDelimited = true
                    version = "1.0.0"
                }.compile()
                result.hasError() shouldBe false
                val tree = jsonMapper.readTree(result.fileList.first().content)
                val hellosContent = tree.path("paths").path("/hellos").path("post")
                    .path("responses").path("200").path("content")
                hellosContent.has("text/event-stream") shouldBe true
                hellosContent.has("application/x-ndjson") shouldBe false
            }

            test("without streamSseStyleDelimited, unary method uses application/json") {
                val request = StreamSseStyleDelimitedTest::class.java
                    .getResourceAsStream("/code-generator-request.binpb")
                    .shouldNotBeNull()
                val result = ProtocGenOpenAPI.from(request) {
                    streamSseStyleDelimited = true
                    version = "1.0.0"
                }.compile()
                result.hasError() shouldBe false
                val tree = jsonMapper.readTree(result.fileList.first().content)
                val helloContent = tree.path("paths").path("/hello").path("post")
                    .path("responses").path("200").path("content")
                helloContent.has("application/json") shouldBe true
                helloContent.has("text/event-stream") shouldBe false
            }
        }
    }
}
