package com.engine.protoc.openapi.example

import com.engine.protoc.openapi.ProtocGenOpenAPI
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import tools.jackson.databind.ObjectMapper

class ErrorResponsesTest :
    FunSpec({

        assertSoftly = true

        val request =
            ErrorResponsesTest::class.java
                .getResourceAsStream("/code-generator-request.binpb")
                .shouldNotBeNull()
        val response =
            ProtocGenOpenAPI.from(request) {
                inlineRequestSchemas = false
                inlineResponseSchemas = false
                validateOutput = true
                validationErrorsAreFatal = true
            }.compile()

        val mapper = ObjectMapper()
        val outputName =
            "engine.protoc.openapi.example.errorResponses.ItemService.openapi.json"
        val generatedFile = response.fileList.find { it.name == outputName }.shouldNotBeNull()
        GoldenFiles.maybeWriteGolden("errorResponses", outputName, generatedFile.content)
        val expected =
            ErrorResponsesTest::class.java
                .getResourceAsStream("/$outputName")
                .shouldNotBeNull()
                .reader()
                .readText()

        test("compile has no errors") {
            response.hasError() shouldBe false
            response.error shouldBe ""
        }

        test("matches reference output") {
            assertSoftly {
                collectJsonDiffs(
                    mapper.readTree(expected),
                    mapper.readTree(generatedFile.content),
                ).forEach { (path, exp, act) ->
                    withClue("at $path — expected: $exp, actual: $act") {
                        act shouldBe exp
                    }
                }
            }
        }
    })
