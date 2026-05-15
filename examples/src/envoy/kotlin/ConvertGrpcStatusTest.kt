package com.engine.protoc.openapi.example

import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.google.protobuf.compiler.PluginProtos
import io.kotest.assertions.withClue
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import tools.jackson.module.kotlin.readValue

class ConvertGrpcStatusTest : EnvoyTestBase(GrpcJsonTranscoder(convertGrpcStatus = true)) {
    init {
        context("confirm envoy behaviors") {
            test("successful request returns 200") {
                val response = postJson("/hello", mapOf("yourName" to "World"))
                response.statusCode() shouldBe 200
                val body = jsonMapper.readValue<Map<String, Any>>(response.body())
                body["replyMessage"].shouldNotBeNull()
            }

            data class ErrorTestData(val description: String, val expectedStatus: Int)
            withData<ErrorTestData>(
                { it.description },
                listOf(ErrorTestData("empty yourName triggers NOT_FOUND gRPC error → 404", 404)),
            ) {
                // HelloServiceImpl throws NOT_FOUND when yourName is empty
                val response = postJson("/hello", mapOf("yourName" to ""))
                response.statusCode() shouldBe it.expectedStatus
                val body = jsonMapper.readValue<Map<String, Any>>(response.body())
                // Envoy translates gRPC status to google.rpc.Status JSON
                body["code"].shouldNotBeNull()
                body["code"] shouldNotBe 0
            }
        }

        context("validate convertGrpcStatus openapi output") {
            fun request() =
                ConvertGrpcStatusTest::class.java
                    .getResourceAsStream("/code-generator-request.binpb")
                    .shouldNotBeNull()

            val result = ProtocGenOpenAPI.from(request()) {
                convertGrpcStatus = true
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
                    ConvertGrpcStatusTest::class.java
                        .getResourceAsStream("/${file.name}.ConvertGrpcStatusTest.json")
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
