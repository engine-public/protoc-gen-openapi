package com.engine.protoc.openapi.example

import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.google.protobuf.compiler.PluginProtos
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import tools.jackson.databind.ObjectMapper

class VersionTest :
    FunSpec({

        assertSoftly = true

        // Each compile() call consumes the InputStream, so we load a fresh one per run.
        fun request() = VersionTest::class.java.getResourceAsStream("/code-generator-request.binpb").shouldNotBeNull()

        val mapper = ObjectMapper()

        // -----------------------------------------------------------------------
        // Run 1: options.version = "global-2.0.0"
        //
        // This run demonstrates two of the four combinations:
        //
        //   [A] PinnedVersionService: annotation version "pinned-1.0.0" is present
        //       → the options version is silently discarded; annotation wins.
        //
        //   [B] UnversionedService: no annotation version
        //       → the options version "global-2.0.0" is written to info.version.
        //
        // Both documents now have info.version, so validateOutput = true is used and
        // the generated files are compared against checked-in reference schemas.
        // -----------------------------------------------------------------------
        val withVersion =
            ProtocGenOpenAPI.from(request()) {
                merge = false
                version = "global-2.0.0"
                validateOutput = true
                validationErrorsAreFatal = true
            }.compile()

        test("with options version: no errors") {
            withVersion.hasError() shouldBe false
            withVersion.error shouldBe ""
        }

        test("with options version: annotation version takes precedence") {
            // PinnedVersionService carries option(.engine.protoc.openapi.service) = { info { version: "pinned-1.0.0" } }.
            // The service annotation is layer 4 in the priority stack and overwrites the
            // options-provided value applied at layer 2.
            val doc =
                mapper.readTree(
                    withVersion.fileList
                        .find { it.name == "engine.protoc.openapi.example.version.PinnedVersionService.openapi.json" }
                        .shouldNotBeNull()
                        .content,
                )
            doc["info"]["version"].asString() shouldBe "pinned-1.0.0"
        }

        test("with options version: option fills in missing version") {
            // UnversionedService has no engine annotation at all.  The options version
            // is the only source of info.version and must therefore appear in the output.
            val doc =
                mapper.readTree(
                    withVersion.fileList
                        .find { it.name == "engine.protoc.openapi.example.version.UnversionedService.openapi.json" }
                        .shouldNotBeNull()
                        .content,
                )
            doc["info"]["version"].asString() shouldBe "global-2.0.0"
        }

        // Full reference-file comparison for regression protection.
        withData<PluginProtos.CodeGeneratorResponse.File>(
            { "with options version: matches reference: " + it.name },
            withVersion.fileList,
        ) { file ->
            val expected = mapper.readTree(
                VersionTest::class.java
                    .getResourceAsStream("/${file.name}")
                    .shouldNotBeNull()
                    .reader()
                    .readText(),
            )
            assertSoftly {
                collectJsonDiffs(expected, mapper.readTree(file.content))
                    .forEach { (path, exp, act) ->
                        withClue("at $path — expected: $exp, actual: $act") {
                            act shouldBe exp
                        }
                    }
            }
        }

        // -----------------------------------------------------------------------
        // Run 2: options.version absent (null / default)
        //
        // This run demonstrates the other two combinations:
        //
        //   [C] PinnedVersionService: annotation version is still "pinned-1.0.0"
        //       → no options version to compete with; annotation is the sole source.
        //
        //   [D] UnversionedService: neither annotation nor options provides a version
        //       → info.version is absent from the output entirely.
        //
        // UnversionedService's document is not valid OAS 3.1 (missing info.version),
        // so validateOutput = false is used here; correctness is verified inline.
        // -----------------------------------------------------------------------
        val withoutVersion =
            ProtocGenOpenAPI.from(request()) {
                merge = false
                validateOutput = false
            }.compile()

        test("without options version: no errors") {
            withoutVersion.hasError() shouldBe false
            withoutVersion.error shouldBe ""
        }

        test("without options version: annotation version still present") {
            // The annotation on PinnedVersionService is unaffected by the absence of an options
            // version — the two sources are independent; removing one does not remove the other.
            val doc =
                mapper.readTree(
                    withoutVersion.fileList
                        .find { it.name == "engine.protoc.openapi.example.version.PinnedVersionService.openapi.json" }
                        .shouldNotBeNull()
                        .content,
                )
            doc["info"]["version"].asString() shouldBe "pinned-1.0.0"
        }

        test("without options version: no version when neither source provides one") {
            // With no options version and no annotation, the info object has only the fields
            // derived from the service descriptor (title, description).  The version key
            // must be absent — not null, not an empty string, simply not present.
            val doc =
                mapper.readTree(
                    withoutVersion.fileList
                        .find { it.name == "engine.protoc.openapi.example.version.UnversionedService.openapi.json" }
                        .shouldNotBeNull()
                        .content,
                )
            doc.path("info").path("version").isMissingNode shouldBe true
        }
    })
