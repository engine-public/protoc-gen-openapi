package com.engine.protoc.openapi.example

import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.engine.protoc.openapi.example.envoy.Greeting
import io.kotest.matchers.shouldBe
import tools.jackson.module.kotlin.readValue

/**
 * T3 for the case_insensitive_enum_parsing gap: verifies that combining
 * caseInsensitiveEnumParsing + alwaysPrintEnumsAsInts works correctly.
 *
 * When both are enabled, Envoy accepts integers (and strings in any case) for request enum
 * fields, and returns integers in responses. Case-insensitivity is moot for integer input —
 * the test confirms the combination does not interfere with integer enum handling.
 *
 * From the OAS compiler perspective, NUMERIC_VALUE format already covers this combination;
 * no separate compiler option is needed.
 */
class CaseInsensitiveEnumParsingAndAlwaysPrintAsIntsTest :
    EnvoyTestBase(
        GrpcJsonTranscoder(
            caseInsensitiveEnumParsing = true,
            printOptions = GrpcJsonTranscoder.PrintOptions(alwaysPrintEnumsAsInts = true),
        ),
    ) {
    init {
        context("confirm envoy behaviors") {
            test("integer input works; response is integer") {
                val response = postJson("/hello", mapOf("yourName" to "World", "greetingType" to Greeting.GREETING_HELLO.number))
                response.statusCode() shouldBe 200
                val body = jsonMapper.readValue<Map<String, Any>>(response.body())
                body["greeting"] shouldBe Greeting.GREETING_HELLO.number
            }

            test("lowercase string input still accepted alongside integer response") {
                val response = postJson("/hello", mapOf("yourName" to "World", "greetingType" to "greeting_hi"))
                response.statusCode() shouldBe 200
                val body = jsonMapper.readValue<Map<String, Any>>(response.body())
                // Response is integer (alwaysPrintEnumsAsInts), not a string
                body["greeting"] shouldBe Greeting.GREETING_HI.number
            }

            test("canonical string input still accepted alongside integer response") {
                val response = postJson("/hello", mapOf("yourName" to "World", "greetingType" to Greeting.GREETING_HI.name))
                response.statusCode() shouldBe 200
                val body = jsonMapper.readValue<Map<String, Any>>(response.body())
                body["greeting"] shouldBe Greeting.GREETING_HI.number
            }
        }

        context("validate OAS output — NUMERIC_VALUE covers this combination") {
            test("NUMERIC_VALUE format produces integer enum schema regardless of caseInsensitiveEnumParsing") {
                val request = CaseInsensitiveEnumParsingAndAlwaysPrintAsIntsTest::class.java
                    .getResourceAsStream("/code-generator-request.binpb")!!
                val result = ProtocGenOpenAPI.from(request) {
                    enumValueFormat = ProtocGenOpenAPI.Options.EnumValueFormat.NUMERIC_VALUE
                    version = "1.0.0"
                }.compile()
                result.hasError() shouldBe false
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
