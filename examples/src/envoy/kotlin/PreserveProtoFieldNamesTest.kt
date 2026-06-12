package com.engine.protoc.openapi.example

import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.engine.protoc.openapi.example.envoy.Greeting
import com.google.protobuf.compiler.PluginProtos
import io.kotest.assertions.withClue
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import tools.jackson.module.kotlin.readValue

class PreserveProtoFieldNamesTest : EnvoyTestBase(GrpcJsonTranscoder(printOptions = GrpcJsonTranscoder.PrintOptions(preserveProtoFieldNames = true))) {
    init {
        context("confirm envoy behaviors") {
            data class TestData(
                val description: String,
                val body: Map<String, Any>,
                val expectedGreeting: Greeting = Greeting.GREETING_HELLO,
            )
            withData<TestData>(
                { it.description },
                // proto field name (snake_case) accepted in requests
                TestData("snake_case your_name", mapOf("your_name" to "World", "greeting_type" to Greeting.GREETING_HELLO.name)),
                // camelCase is also accepted — proto3 JSON parsers must accept both forms
                TestData("camelCase yourName", mapOf("yourName" to "World", "greetingType" to Greeting.GREETING_HELLO.name)),
                TestData("hi via snake_case greeting_type", mapOf("your_name" to "World", "greeting_type" to Greeting.GREETING_HI.name), Greeting.GREETING_HI),
            ) {
                val response = postJson("/hello", it.body)
                response.statusCode() shouldBe 200
                val body = jsonMapper.readValue<Map<String, Any>>(response.body())
                // With preserve_proto_field_names all fields revert to their proto name,
                // including fields with an explicit json_name annotation.
                // reply_message (not replyMessage), greeting_used (not "greeting" json_name).
                body["reply_message"].shouldNotBeNull()
                body["greeting_used"] shouldBe it.expectedGreeting.name
            }
        }

        context("validate preserveProtoFieldNames openapi output") {
            fun request() =
                PreserveProtoFieldNamesTest::class.java
                    .getResourceAsStream("/code-generator-request.binpb")
                    .shouldNotBeNull()

            val result = ProtocGenOpenAPI.from(request()) {
                preserveProtoFieldNames = true
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
                    PreserveProtoFieldNamesTest::class.java
                        .getResourceAsStream("/${file.name}.PreserveProtoFieldNamesTest.json")
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
