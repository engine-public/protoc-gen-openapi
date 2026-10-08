package com.engine.protoc.openapi.example

import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.engine.protoc.util.markdown.ReferenceLinkMode
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

/**
 * Exercises `resolveReferenceLinksMode` and `referenceLink` across every kind of emitted string:
 * proto comments and annotation descriptions resolve to anchors or code spans, plain-text
 * summaries and titles are scrubbed of Markdown, and unresolved or ambiguous references fail the
 * run under `FAIL_ON_INVALID`.
 */
class ReferenceLinkResolutionTest :
    FunSpec({

        assertSoftly = true

        fun compile(
            mode: ReferenceLinkMode,
            target: ProtocGenOpenAPI.Options.ReferenceLinkTarget,
        ) = ProtocGenOpenAPI.from(
            ReferenceLinkResolutionTest::class.java.getResourceAsStream("/code-generator-request.binpb").shouldNotBeNull(),
        ) {
            merge = true
            autoTagServices = true
            inlineRequestSchemas = false
            inlineResponseSchemas = false
            validateOutput = true
            resolveReferenceLinksMode = mode
            referenceLinkTarget = target
            referenceLink = mapOf("google.rpc.Status" to "https://cloud.google.com/tasks/docs/reference/rpc/google.rpc#status")
        }.compile()

        val mapper = ObjectMapper()

        context("FAIL_ON_INVALID") {
            val response = compile(ReferenceLinkMode.FAIL_ON_INVALID, ProtocGenOpenAPI.Options.ReferenceLinkTarget.REDOC)

            test("fails the run listing each bad reference once") {
                response.error shouldContain "protoc-gen-openapi failed:\n2 reference-link failures under resolveReferenceLinksMode=FAIL_ON_INVALID:"
                response.error shouldContain
                    "catalog.proto :: engine.protoc.openapi.example.referenceLinkResolution.catalog.ListGizmosRequest.filter :: [NoSuchThing] — no matching"
                response.error shouldContain
                    "catalog.proto :: engine.protoc.openapi.example.referenceLinkResolution.catalog.Gadget :: [Gadget] — ambiguous; candidates:"
            }
        }

        context("WARN with REDOC") {
            val response = compile(ReferenceLinkMode.WARN, ProtocGenOpenAPI.Options.ReferenceLinkTarget.REDOC)
            val content = response.fileList.single().content
            GoldenFiles.maybeWriteGolden("referenceLinkResolution", "redoc.openapi.json", content)
            val doc: JsonNode = mapper.readTree(content)
            val getGizmo = doc["paths"]["/gizmos/{id}"]["get"]
            val listGizmos = doc["paths"]["/gizmos"]["get"]
            val tag = doc["tags"].first { it["name"].asString() == "CatalogService" }

            test("succeeds") { response.error shouldBe "" }

            test("comments resolve to anchors, external links, and code spans") {
                tag["description"].asString() shouldBe
                    """
                    |Manages [Gizmo](#tag/Gizmo) resources.  Fetch one with [GetGizmo](#operation/CatalogService_GetGizmo); on failure, details are in
                    |[Status.details](https://cloud.google.com/tasks/docs/reference/rpc/google.rpc#status).
                    |
                    |Unmapped RPCs like `CatalogService.Reindex` have no operation, and imported types like
                    |`engine.protoc.openapi.example.referenceLinkResolution.shared.Money` have no schema here, so both render as code.
                    """.trimMargin()
            }

            test("multi-line comments are dedented and keep their Markdown structure") {
                getGizmo["description"].asString() shouldContain "\n\nFurther notes:\n\n- `id` must be non-empty\n  and stable\n- see [ListGizmos](#operation/CatalogService_ListGizmos) for collections"
            }

            test("summaries are plain text") {
                getGizmo["summary"].asString() shouldBe "Gets a single Gizmo by GetGizmoRequest.id."
                listGizmos["summary"].asString() shouldBe "Lists every Gizmo in the catalog."
            }

            test("annotation descriptions and titles are processed too") {
                listGizmos["description"].asString() shouldBe
                    "Pages through [Gizmo](#tag/Gizmo) results; see [GetGizmo](#operation/CatalogService_GetGizmo) for one."
                val pageSize = listGizmos["parameters"].first { it["name"].asString() == "pageSize" }
                pageSize["description"].asString() shouldBe
                    "At most 100 [Gizmo](#tag/Gizmo) items."
                pageSize["schema"]["title"].asString() shouldBe "Page size"
            }

            test("unresolved references render as code") {
                val filter = listGizmos["parameters"].first { it["name"].asString() == "filter" }
                filter["description"].asString() shouldBe "Deliberately broken reference: `NoSuchThing`."
            }
        }

        context("WARN with SWAGGER_UI") {
            val response = compile(ReferenceLinkMode.WARN, ProtocGenOpenAPI.Options.ReferenceLinkTarget.SWAGGER_UI)
            val content = response.fileList.single().content
            GoldenFiles.maybeWriteGolden("referenceLinkResolution", "swagger.openapi.json", content)
            val doc: JsonNode = mapper.readTree(content)

            test("schemas render as code; operations link under their tag") {
                val tag = doc["tags"].first { it["name"].asString() == "CatalogService" }
                tag["description"].asString() shouldContain "Manages `Gizmo` resources.  Fetch one with [GetGizmo](#/CatalogService/CatalogService_GetGizmo)"
            }
        }

        context("NONE") {
            val response = compile(ReferenceLinkMode.NONE, ProtocGenOpenAPI.Options.ReferenceLinkTarget.NONE)
            val content = response.fileList.single().content
            GoldenFiles.maybeWriteGolden("referenceLinkResolution", "none.openapi.json", content)
            val doc: JsonNode = mapper.readTree(content)

            test("text is emitted as written, brackets included") {
                response.error shouldBe ""
                val listGizmos = doc["paths"]["/gizmos"]["get"]
                listGizmos["description"].asString() shouldBe "Pages through [Gizmo] results; see [GetGizmo] for one."
                withClue("plain-text fields still lose their Markdown") {
                    listGizmos["summary"].asString() shouldNotContain "**"
                }
            }
        }

        test("goldens match") {
            for ((name, mode, target) in listOf(
                Triple("redoc.openapi.json", ReferenceLinkMode.WARN, ProtocGenOpenAPI.Options.ReferenceLinkTarget.REDOC),
                Triple("swagger.openapi.json", ReferenceLinkMode.WARN, ProtocGenOpenAPI.Options.ReferenceLinkTarget.SWAGGER_UI),
                Triple("none.openapi.json", ReferenceLinkMode.NONE, ProtocGenOpenAPI.Options.ReferenceLinkTarget.NONE),
            )) {
                val expected = ReferenceLinkResolutionTest::class.java.getResourceAsStream("/$name").shouldNotBeNull().reader().readText()
                withClue(name) {
                    collectJsonDiffs(mapper.readTree(expected), mapper.readTree(compile(mode, target).fileList.single().content))
                        .forEach { (path, exp, act) -> withClue("at $path") { act shouldBe exp } }
                }
            }
        }
    })
