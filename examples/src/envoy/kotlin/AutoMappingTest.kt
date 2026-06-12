package com.engine.protoc.openapi.example

import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.engine.protoc.openapi.example.envoy.Greeting
import com.google.protobuf.compiler.PluginProtos
import io.kotest.assertions.withClue
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import tools.jackson.module.kotlin.readValue

class AutoMappingTest : EnvoyTestBase(GrpcJsonTranscoder(autoMapping = true)) {
    init {
        context("confirm envoy behaviors") {
            data class TestData(
                val description: String,
                val path: String,
                val greetingType: Greeting,
            )
            withData<TestData>(
                { it.description },
                TestData("explicit annotation: /hello routes SayHello", "/hello", Greeting.GREETING_HELLO),
                TestData("auto-mapped: /pkg.Service/PingHello routes PingHello", "/engine.protoc.openapi.example.envoy.HelloService/PingHello", Greeting.GREETING_HELLO),
                TestData("auto-mapped with hi greeting", "/engine.protoc.openapi.example.envoy.HelloService/PingHello", Greeting.GREETING_HI),
            ) {
                val request = mapOf("yourName" to "World", "greetingType" to it.greetingType.name)
                val response = postJson(it.path, request)
                response.statusCode() shouldBe 200
                val body = jsonMapper.readValue<Map<String, Any>>(response.body())
                body["replyMessage"].shouldNotBeNull()
                body["greeting"] shouldBe it.greetingType.name
            }
        }

        context("validate autoMapping openapi output") {
            fun request() =
                AutoMappingTest::class.java
                    .getResourceAsStream("/code-generator-request.binpb")
                    .shouldNotBeNull()

            val result = ProtocGenOpenAPI.from(request()) {

                inlineRequestSchemas = false

                inlineResponseSchemas = false
                autoMapping = true
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
                    AutoMappingTest::class.java
                        .getResourceAsStream("/${file.name}.AutoMappingTest.json")
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
