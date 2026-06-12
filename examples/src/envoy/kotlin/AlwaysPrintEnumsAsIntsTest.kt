package com.engine.protoc.openapi.example

import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.engine.protoc.openapi.example.envoy.Greeting
import com.google.protobuf.compiler.PluginProtos
import io.kotest.assertions.withClue
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldNotBeNull
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
                TestData("wrong case", "greeting_hello", shouldFail = true),
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
                    responseBody["greeting"] shouldBe it.expectedGreetingUsed.number
                }
            }

            // T1: without alwaysPrintPrimitiveFields, integer 0 (proto3 default) is omitted
            test("integer 0 (UNSPECIFIED) is omitted from response without alwaysPrintPrimitiveFields") {
                val response = postJson("/hello", mapOf("yourName" to "World", "greetingType" to Greeting.GREETING_UNSPECIFIED.number))
                response.statusCode() shouldBe 200
                val body = jsonMapper.readValue<Map<String, Any>>(response.body())
                body.containsKey("greeting") shouldBe false
            }
        }

        context("validate NUMERIC_VALUE openapi output") {
            fun request() =
                AlwaysPrintEnumsAsIntsTest::class.java
                    .getResourceAsStream("/code-generator-request.binpb")
                    .shouldNotBeNull()

            val result = ProtocGenOpenAPI.from(request()) {
                enumValueFormat = ProtocGenOpenAPI.Options.EnumValueFormat.NUMERIC_VALUE
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
                    AlwaysPrintEnumsAsIntsTest::class.java
                        .getResourceAsStream("/${file.name}.AlwaysPrintEnumsAsIntsTest.json")
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

            // T3: enum $ref in message properties resolves to integer type throughout schema hierarchy
            test("Greeting component schema has integer type") {
                val tree = jsonMapper.readTree(result.fileList.first().content)
                val greeting = tree.path("components").path("schemas").path("Greeting")
                greeting.path("type").asString() shouldBe "integer"
                val enumValues = mutableListOf<Int>()
                greeting.path("enum").forEach { enumValues.add(it.intValue()) }
                enumValues shouldBe listOf(0, 1, 2)
            }
        }
    }
}
