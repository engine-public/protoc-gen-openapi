package com.engine.protoc.openapi.example

import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.engine.protoc.openapi.example.envoy.Greeting
import com.google.protobuf.compiler.PluginProtos
import io.kotest.assertions.withClue
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import tools.jackson.module.kotlin.readValue

/**
 * T2 for the always_print_enums_as_ints gap: verifies that combining
 * alwaysPrintEnumsAsInts + alwaysPrintPrimitiveFields forces integer 0 (the proto3
 * default enum value) to appear in the response, and that the OAS schema reflects
 * both integer enum type and required arrays.
 */
class AlwaysPrintEnumsAsIntsPrimitiveFieldsTest :
    EnvoyTestBase(
        GrpcJsonTranscoder(
            printOptions = GrpcJsonTranscoder.PrintOptions(
                alwaysPrintEnumsAsInts = true,
                alwaysPrintPrimitiveFields = true,
            ),
        ),
    ) {
    init {
        context("confirm envoy behaviors") {
            // T2: with alwaysPrintPrimitiveFields, integer 0 is present in response
            test("integer 0 (UNSPECIFIED) is present in response with alwaysPrintPrimitiveFields") {
                val response = postJson("/hello", mapOf("yourName" to "World", "greetingType" to Greeting.GREETING_UNSPECIFIED.number))
                response.statusCode() shouldBe 200
                val body = jsonMapper.readValue<Map<String, Any>>(response.body())
                // alwaysPrintPrimitiveFields forces the default-value integer 0 to appear
                body["greeting"].shouldNotBeNull()
                body["greeting"] shouldBe 0
            }

            test("non-zero integer greeting still present") {
                val response = postJson("/hello", mapOf("yourName" to "World", "greetingType" to Greeting.GREETING_HELLO.number))
                response.statusCode() shouldBe 200
                val body = jsonMapper.readValue<Map<String, Any>>(response.body())
                body["greeting"] shouldBe Greeting.GREETING_HELLO.number
            }
        }

        context("validate combined NUMERIC_VALUE + alwaysPrintPrimitiveFields openapi output") {
            fun request() =
                AlwaysPrintEnumsAsIntsPrimitiveFieldsTest::class.java
                    .getResourceAsStream("/code-generator-request.binpb")
                    .shouldNotBeNull()

            val result = ProtocGenOpenAPI.from(request()) {

                inlineRequestSchemas = false

                inlineResponseSchemas = false
                enumValueFormat = ProtocGenOpenAPI.Options.EnumValueFormat.NUMERIC_VALUE
                alwaysPrintPrimitiveFields = true
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
                GoldenFiles.maybeWriteGolden("envoy", "${file.name}.AlwaysPrintEnumsAsIntsPrimitiveFieldsTest.json", file.content)
                val expected = jsonMapper.readTree(
                    AlwaysPrintEnumsAsIntsPrimitiveFieldsTest::class.java
                        .getResourceAsStream("/${file.name}.AlwaysPrintEnumsAsIntsPrimitiveFieldsTest.json")
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
