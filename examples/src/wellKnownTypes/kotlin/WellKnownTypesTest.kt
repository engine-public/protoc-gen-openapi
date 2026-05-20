package com.engine.protoc.openapi.example

import com.engine.protoc.openapi.ProtocGenOpenAPI
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import tools.jackson.databind.ObjectMapper

class WellKnownTypesTest :
    FunSpec({

        assertSoftly = true

        val request =
            WellKnownTypesTest::class.java
                .getResourceAsStream("/code-generator-request.binpb")
                .shouldNotBeNull()
        val response =
            ProtocGenOpenAPI.from(request) {
                validateOutput = true
                validationErrorsAreFatal = true
            }.compile()

        val mapper = ObjectMapper()
        val outputName =
            "engine.protoc.openapi.example.wellKnownTypes.EnvelopeService.openapi.json"
        val generatedFile = response.fileList.find { it.name == outputName }.shouldNotBeNull()
        val expected =
            WellKnownTypesTest::class.java
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
