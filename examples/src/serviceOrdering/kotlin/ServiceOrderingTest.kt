package com.engine.protoc.openapi.example

import com.engine.protoc.openapi.ProtocGenOpenAPI
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ArrayNode

class ServiceOrderingTest :
    FunSpec({

        assertSoftly = true

        val request =
            ServiceOrderingTest::class.java
                .getResourceAsStream("/code-generator-request.binpb")
                .shouldNotBeNull()
        val response =
            ProtocGenOpenAPI.from(request) {
                inlineRequestSchemas = false
                inlineResponseSchemas = false
                merge = true
                autoTagServices = true
                validateOutput = true
                validationErrorsAreFatal = true
            }.compile()

        val mapper = ObjectMapper()
        val outputName = "engine.protoc.openapi.example.serviceOrdering.openapi.json"
        val generatedFile = response.fileList.find { it.name == outputName }.shouldNotBeNull()
        val expected =
            ServiceOrderingTest::class.java
                .getResourceAsStream("/$outputName")
                .shouldNotBeNull()
                .reader()
                .readText()

        test("compile has no errors") {
            response.hasError() shouldBe false
            response.error shouldBe ""
        }

        test("paths appear in index_order then encounter-ordinal order") {
            val tree = mapper.readTree(generatedFile.content)
            val pathKeys = tree.get("paths").propertyNames().asSequence().toList()
            // Beta (-1), Alpha (impl 0, src 0), Epsilon (expl 0, src 4 — collides with Alpha,
            // falls back to source order), Gamma (impl 2), Delta (expl 10).
            pathKeys shouldBe listOf(
                "/beta/{id}",
                "/alpha/{id}",
                "/epsilon/{id}",
                "/gamma/{id}",
                "/delta/{id}",
            )
        }

        test("auto-generated service tags follow the same order") {
            val tree = mapper.readTree(generatedFile.content)
            val tags = tree.get("tags") as ArrayNode
            val tagNames = tags.elements().asSequence().map { it.get("name").asString() }.toList()
            tagNames shouldBe listOf(
                "BetaService",
                "AlphaService",
                "EpsilonService",
                "GammaService",
                "DeltaService",
            )
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
