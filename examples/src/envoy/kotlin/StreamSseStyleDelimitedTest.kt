package com.engine.protoc.openapi.example

import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.google.protobuf.compiler.PluginProtos
import io.kotest.assertions.withClue
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import tools.jackson.module.kotlin.readValue

class StreamSseStyleDelimitedTest : EnvoyTestBase(GrpcJsonTranscoder(printOptions = GrpcJsonTranscoder.PrintOptions(streamSseStyleDelimited = true))) {
    init {
        context("confirm envoy behaviors") {
            test("unary /hello returns plain JSON") {
                val response = postJson("/hello", mapOf("yourName" to "World"))
                response.statusCode() shouldBe 200
                val body = jsonMapper.readValue<Map<String, Any>>(response.body())
                body["replyMessage"].shouldNotBeNull()
            }

            test("streaming /hellos returns SSE-framed messages") {
                val response = postJson("/hellos", mapOf("yourName" to "World"))
                response.statusCode() shouldBe 200
                val body = response.body().trim()
                // With stream_sse_style_delimited, each message is prefixed with "data: "
                val dataLines = body.lines().filter { it.startsWith("data: ") }
                dataLines.size shouldBe 3
                dataLines.forEachIndexed { i, line ->
                    line shouldStartWith "data: "
                    val json = line.removePrefix("data: ")
                    val msg = jsonMapper.readValue<Map<String, Any>>(json)
                    (msg["replyMessage"] as? String).shouldNotBeNull()
                    msg["replyMessage"] shouldBe "Hello, World #$i!"
                }
            }
        }

        context("validate streamSseStyleDelimited openapi output") {
            fun request() =
                StreamSseStyleDelimitedTest::class.java
                    .getResourceAsStream("/code-generator-request.binpb")
                    .shouldNotBeNull()

            val result = ProtocGenOpenAPI.from(request()) {
                streamSseStyleDelimited = true
                serviceInclude = "HelloService"
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
                    serviceInclude = "HelloService"
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
                    serviceInclude = "HelloService"
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
