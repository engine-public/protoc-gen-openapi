package com.engine.protoc.openapi.example

import com.engine.protoc.openapi.ProtocGenOpenAPI
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

class FilteringTest :
    FunSpec({

        assertSoftly = true

        fun request() =
            FilteringTest::class.java
                .getResourceAsStream("/code-generator-request.binpb")
                .shouldNotBeNull()

        val mapper = ObjectMapper()

        fun paths(content: String): JsonNode = mapper.readTree(content).path("paths")

        fun schemas(content: String): JsonNode = mapper.readTree(content).path("components").path("schemas")

        // -----------------------------------------------------------------------
        // Run 1 — defaults: all services included
        // -----------------------------------------------------------------------
        val run1 =
            ProtocGenOpenAPI.from(request()) {
                merge = true
                validateOutput = true
            }.compile()

        test("run1: no errors") {
            run1.hasError() shouldBe false
            run1.error shouldBe ""
        }

        test("run1: all paths present") {
            val p = paths(run1.fileList.first().content)
            p.has("/alpha") shouldBe true
            p.has("/beta") shouldBe true
            p.has("/gamma") shouldBe true
        }

        test("run1: all schemas present") {
            val s = schemas(run1.fileList.first().content)
            s.has("AlphaRequest") shouldBe true
            s.has("AlphaResponse") shouldBe true
            s.has("BetaRequest") shouldBe true
            s.has("BetaResponse") shouldBe true
            s.has("GammaRequest") shouldBe true
            s.has("GammaResponse") shouldBe true
        }

        // -----------------------------------------------------------------------
        // Run 2 — serviceInclude=AlphaService: only AlphaService included
        // -----------------------------------------------------------------------
        val run2 =
            ProtocGenOpenAPI.from(request()) {
                merge = true
                serviceInclude = "AlphaService"
            }.compile()

        test("run2: no errors") {
            run2.hasError() shouldBe false
            run2.error shouldBe ""
        }

        test("run2: only alpha path present") {
            val p = paths(run2.fileList.first().content)
            p.has("/alpha") shouldBe true
            p.has("/beta") shouldBe false
            p.has("/gamma") shouldBe false
        }

        test("run2: only Alpha schemas present") {
            val s = schemas(run2.fileList.first().content)
            s.has("AlphaRequest") shouldBe true
            s.has("AlphaResponse") shouldBe true
            s.has("BetaRequest") shouldBe false
            s.has("BetaResponse") shouldBe false
            s.has("GammaRequest") shouldBe false
            s.has("GammaResponse") shouldBe false
        }

        // -----------------------------------------------------------------------
        // Run 3 — serviceExclude=BetaService: Alpha and Gamma only
        // -----------------------------------------------------------------------
        val run3 =
            ProtocGenOpenAPI.from(request()) {
                merge = true
                serviceExclude = "BetaService"
            }.compile()

        test("run3: no errors") {
            run3.hasError() shouldBe false
            run3.error shouldBe ""
        }

        test("run3: alpha and gamma paths present, beta absent") {
            val p = paths(run3.fileList.first().content)
            p.has("/alpha") shouldBe true
            p.has("/beta") shouldBe false
            p.has("/gamma") shouldBe true
        }

        test("run3: Alpha and Gamma schemas present, Beta schemas absent") {
            val s = schemas(run3.fileList.first().content)
            s.has("AlphaRequest") shouldBe true
            s.has("AlphaResponse") shouldBe true
            s.has("BetaRequest") shouldBe false
            s.has("BetaResponse") shouldBe false
            s.has("GammaRequest") shouldBe true
            s.has("GammaResponse") shouldBe true
        }

        // -----------------------------------------------------------------------
        // Run 4 — serviceInclude=Alpha|Gamma (regex alternation)
        // -----------------------------------------------------------------------
        val run4 =
            ProtocGenOpenAPI.from(request()) {
                merge = true
                serviceInclude = "Alpha|Gamma"
            }.compile()

        test("run4: no errors") {
            run4.hasError() shouldBe false
            run4.error shouldBe ""
        }

        test("run4: alpha and gamma paths present, beta absent") {
            val p = paths(run4.fileList.first().content)
            p.has("/alpha") shouldBe true
            p.has("/beta") shouldBe false
            p.has("/gamma") shouldBe true
        }

        // -----------------------------------------------------------------------
        // Run 5 — serviceInclude anchored to full FQN with escaped dots
        // -----------------------------------------------------------------------
        val run5 =
            ProtocGenOpenAPI.from(request()) {
                merge = true
                serviceInclude =
                    """engine\.protoc\.openapi\.example\.filtering\.GammaService"""
            }.compile()

        test("run5: no errors") {
            run5.hasError() shouldBe false
            run5.error shouldBe ""
        }

        test("run5: only gamma path present") {
            val p = paths(run5.fileList.first().content)
            p.has("/alpha") shouldBe false
            p.has("/beta") shouldBe false
            p.has("/gamma") shouldBe true
        }

        test("run5: only Gamma schemas present") {
            val s = schemas(run5.fileList.first().content)
            s.has("AlphaRequest") shouldBe false
            s.has("AlphaResponse") shouldBe false
            s.has("BetaRequest") shouldBe false
            s.has("BetaResponse") shouldBe false
            s.has("GammaRequest") shouldBe true
            s.has("GammaResponse") shouldBe true
        }

        // -----------------------------------------------------------------------
        // Run 6 — serviceExclude excludes multiple services via alternation
        // -----------------------------------------------------------------------
        val run6 =
            ProtocGenOpenAPI.from(request()) {
                merge = true
                serviceExclude = "AlphaService|BetaService"
            }.compile()

        test("run6: no errors") {
            run6.hasError() shouldBe false
            run6.error shouldBe ""
        }

        test("run6: only gamma path present") {
            val p = paths(run6.fileList.first().content)
            p.has("/alpha") shouldBe false
            p.has("/beta") shouldBe false
            p.has("/gamma") shouldBe true
        }

        test("run6: only Gamma schemas present") {
            val s = schemas(run6.fileList.first().content)
            s.has("AlphaRequest") shouldBe false
            s.has("AlphaResponse") shouldBe false
            s.has("BetaRequest") shouldBe false
            s.has("BetaResponse") shouldBe false
            s.has("GammaRequest") shouldBe true
            s.has("GammaResponse") shouldBe true
        }

        // -----------------------------------------------------------------------
        // Run 7 — unmerged mode: filter to AlphaService only
        //
        // Only AlphaService.openapi.json is produced; the other two service files
        // are suppressed.  The proto file carries a file-level annotation but has
        // services, so no file-only aggregate output is generated.
        // -----------------------------------------------------------------------
        val run7 =
            ProtocGenOpenAPI.from(request()) {
                merge = false
                serviceInclude = "AlphaService"
            }.compile()

        test("run7: no errors") {
            run7.hasError() shouldBe false
            run7.error shouldBe ""
        }

        test("run7: only AlphaService file generated in unmerged mode") {
            run7.fileList.map { it.name }.shouldContainExactlyInAnyOrder(
                "engine.protoc.openapi.example.filtering.AlphaService.openapi.json",
            )
        }
    })
