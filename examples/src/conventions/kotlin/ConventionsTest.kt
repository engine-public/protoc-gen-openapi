package com.engine.protoc.openapi.example

import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import tools.jackson.databind.ObjectMapper

class ConventionsTest :
    FunSpec({

        assertSoftly = true

        val request =
            ConventionsTest::class.java.getResourceAsStream("/code-generator-request.binpb").shouldNotBeNull()
        val response =
            ProtocGenOpenAPI.from(request) {
                inlineRequestSchemas = false
                inlineResponseSchemas = false
                validateOutput = true
                // The output omits info.version — that field requires an engine annotation and
                // this example deliberately uses only google.api.http.  OAS schema validation is
                // therefore skipped; structure is verified by the reference-file comparison below.
                validationErrorsAreFatal = false
            }.compile()

        val mapper = ObjectMapper()
        val generatedFile =
            response.fileList
                .find { it.name == "engine.protoc.openapi.example.conventions.Greeter.openapi.json" }
                .shouldNotBeNull()
        GoldenFiles.maybeWriteGolden("conventions", generatedFile.name, generatedFile.content)
        val json = mapper.readTree(generatedFile.content)
        val expected =
            ConventionsTest::class.java
                .getResourceAsStream("/engine.protoc.openapi.example.conventions.Greeter.openapi.json")
                .shouldNotBeNull()
                .reader()
                .readText()

        test("validate reference file") {
            val oasSchema by lazy {
                SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12) {
                    it.schemaIdResolvers {
                        it.mapPrefix(
                            "https://spec.openapis.org/oas/3.1",
                            "classpath:schemas/spec.openapis.org/oas/3.1",
                        )
                    }
                }
                    .getSchema(SchemaLocation.of("https://spec.openapis.org/oas/3.1/schema-base/2022-10-07"))
            }

            // This example deliberately supplies no version (no `version` option and no version
            // annotation — only google.api.http), so the generated document, and therefore this
            // reference file, omits `info.version`.  The OAS 3.1 schema requires that field, so a
            // single "missing version" error is expected; assert it is the *only* deviation rather
            // than that the document is fully valid, so any other schema violation still fails.
            val errors =
                oasSchema.validate(expected, InputFormat.JSON) { ctx ->
                    ctx.executionConfig { cfg -> cfg.formatAssertionsEnabled(true) }
                }

            errors shouldHaveSize 1
            errors.single().apply {
                instanceLocation.toString() shouldBe "/info"
                keyword shouldBe "required"
                arguments shouldContainExactly arrayOf("version")
            }
        }

        test("has no errors") {
            response.hasError() shouldBe false
            response.error shouldBe ""
        }

        test("matches reference output") {
            assertSoftly {
                collectJsonDiffs(
                    mapper.readTree(expected),
                    json,
                ).forEach { (path, exp, act) ->
                    withClue("at $path — expected: $exp, actual: $act") {
                        act shouldBe exp
                    }
                }
            }
        }
    })
