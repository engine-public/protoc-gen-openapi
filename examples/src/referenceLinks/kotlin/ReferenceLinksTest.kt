package com.engine.protoc.openapi.example

import com.engine.protoc.openapi.ProtocGenOpenAPI
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import tools.jackson.databind.ObjectMapper

/**
 * Exercises the `referenceLinkTarget` option: CommonMark reference links (`[Widget]`,
 * `[WidgetService.ListWidgets]`) in proto comments are rewritten to same-document anchors.
 *
 * Two runs over the same proto compare the two renderer dialects:
 *  - the default `SWAGGER_UI` target — operation and tag references resolve, schema references do
 *    not (Swagger UI has no schema anchors) and fall back to plain text;
 *  - the `REDOC` target — operation, tag, and schema references resolve, and a `<SchemaDefinition>`
 *    section tag is emitted per component schema so the schema anchors have a target.
 */
class ReferenceLinksTest :
    FunSpec({

        assertSoftly = true

        // Each compile() consumes the InputStream, so load a fresh one per run.
        fun request() =
            ReferenceLinksTest::class.java
                .getResourceAsStream("/code-generator-request.binpb")
                .shouldNotBeNull()

        val mapper = ObjectMapper()

        val swagger =
            ProtocGenOpenAPI.from(request()) {
                merge = true
                autoTagServices = true
                inlineRequestSchemas = false
                inlineResponseSchemas = false
                validateOutput = false
                // referenceLinkTarget defaults to SWAGGER_UI.
            }.compile()

        val redoc =
            ProtocGenOpenAPI.from(request()) {
                merge = true
                autoTagServices = true
                inlineRequestSchemas = false
                inlineResponseSchemas = false
                validateOutput = false
                referenceLinkTarget = ProtocGenOpenAPI.Options.ReferenceLinkTarget.REDOC
            }.compile()

        val swaggerContent = swagger.fileList.first().content
        val redocContent = redoc.fileList.first().content
        GoldenFiles.maybeWriteGolden("referenceLinks", "swagger.openapi.json", swaggerContent)
        GoldenFiles.maybeWriteGolden("referenceLinks", "redoc.openapi.json", redocContent)

        test("no errors") {
            swagger.hasError() shouldBe false
            swagger.error shouldBe ""
            redoc.hasError() shouldBe false
            redoc.error shouldBe ""
        }

        test("swagger: matches reference output") {
            val expected =
                mapper.readTree(
                    ReferenceLinksTest::class.java
                        .getResourceAsStream("/swagger.openapi.json")
                        .shouldNotBeNull()
                        .reader()
                        .readText(),
                )
            assertSoftly {
                collectJsonDiffs(expected, mapper.readTree(swaggerContent)).forEach { (path, exp, act) ->
                    withClue("at $path — expected: $exp, actual: $act") { act shouldBe exp }
                }
            }
        }

        test("redoc: matches reference output") {
            val expected =
                mapper.readTree(
                    ReferenceLinksTest::class.java
                        .getResourceAsStream("/redoc.openapi.json")
                        .shouldNotBeNull()
                        .reader()
                        .readText(),
                )
            assertSoftly {
                collectJsonDiffs(expected, mapper.readTree(redocContent)).forEach { (path, exp, act) ->
                    withClue("at $path — expected: $exp, actual: $act") { act shouldBe exp }
                }
            }
        }

        test("swagger: operation references resolve, schema references become code spans") {
            val get = mapper.readTree(swaggerContent)["paths"]["/widgets/{id}"]["get"]
            val description = get["description"].asString()
            // [WidgetService.ListWidgets] → operation anchor (tag + operationId).
            description shouldContain "#/WidgetService/WidgetService_ListWidgets"
            // [Widget] is a schema; Swagger UI has no schema anchor, so it has no link...
            description shouldNotContain "#tag/Widget"
            // ...and the unresolvable reference is stripped to an inline code span, not raw brackets.
            description shouldContain "`Widget`"
            description shouldNotContain "[Widget]"
            // The summary is plain text: reference brackets are stripped entirely (no code, no link).
            get["summary"].asString() shouldContain "Fetches a single Widget by id."
        }

        test("redoc: operation and schema references resolve to redoc anchors") {
            val doc = mapper.readTree(redocContent)
            val getWidget = doc["paths"]["/widgets/{id}"]["get"]["description"].asString()
            getWidget shouldContain "#operation/WidgetService_ListWidgets"
            getWidget shouldContain "#tag/Widget"

            // A <SchemaDefinition> section tag is emitted for each component schema.
            val tags = doc["tags"].toList()
            val tagNames = tags.map { it["name"].asString() }
            tagNames shouldContain "Widget"
            tagNames shouldContain "WidgetStatus"
            tags.first { it["name"].asString() == "Widget" }["description"].asString() shouldContain
                "<SchemaDefinition schemaRef=\"#/components/schemas/Widget\" />"
        }
    })
