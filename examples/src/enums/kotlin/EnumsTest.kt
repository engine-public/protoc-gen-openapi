import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.google.protobuf.compiler.PluginProtos
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import tools.jackson.databind.ObjectMapper

class EnumsTest :
    FunSpec({

        assertSoftly = true

        fun request() =
            EnumsTest::class.java
                .getResourceAsStream("/code-generator-request.binpb")
                .shouldNotBeNull()

        val mapper = ObjectMapper()

        // -----------------------------------------------------------------------
        // Run 1: inlineEnums=false, suppressDefaultEnumValues=false (all defaults)
        //
        //   OrderStatus      → component schema, all 5 values present
        //   ShipmentPriority → inline (annotation inline=true overrides option)
        //   ReturnReason     → component (annotation inline=false overrides option)
        //   PaymentMethod    → component (CRYPTO suppressed by value annotation)
        // -----------------------------------------------------------------------
        val run1 =
            ProtocGenOpenAPI.from(request()) {
                validateOutput = true
            }.compile()

        test("run1: no errors") {
            run1.hasError() shouldBe false
            run1.error shouldBe ""
        }

        withData<PluginProtos.CodeGeneratorResponse.File>(
            { "run1: matches reference: " + it.name },
            run1.fileList,
        ) { file ->
            val expected = mapper.readTree(
                EnumsTest::class.java
                    .getResourceAsStream("/${file.name}.run1.json")
                    .shouldNotBeNull()
                    .reader()
                    .readText(),
            )
            assertSoftly {
                collectJsonDiffs(expected, mapper.readTree(file.content))
                    .forEach { (path, exp, act) ->
                        withClue("run1 at $path — expected: $exp, actual: $act") {
                            act shouldBe exp
                        }
                    }
            }
        }

        // -----------------------------------------------------------------------
        // Run 2: inlineEnums=true, suppressDefaultEnumValues=false
        //
        //   OrderStatus      → inline (follows global inlineEnums=true)
        //   ShipmentPriority → inline (annotation inline=true, same result)
        //   ReturnReason     → component (annotation inline=false overrides inlineEnums=true)
        //   PaymentMethod    → inline (follows global inlineEnums=true; CRYPTO still suppressed)
        // -----------------------------------------------------------------------
        val run2 =
            ProtocGenOpenAPI.from(request()) {
                inlineEnums = true
                validateOutput = true
            }.compile()

        test("run2: no errors") {
            run2.hasError() shouldBe false
            run2.error shouldBe ""
        }

        withData<PluginProtos.CodeGeneratorResponse.File>(
            { "run2: matches reference: " + it.name },
            run2.fileList,
        ) { file ->
            val expected = mapper.readTree(
                EnumsTest::class.java
                    .getResourceAsStream("/${file.name}.run2.json")
                    .shouldNotBeNull()
                    .reader()
                    .readText(),
            )
            assertSoftly {
                collectJsonDiffs(expected, mapper.readTree(file.content))
                    .forEach { (path, exp, act) ->
                        withClue("run2 at $path — expected: $exp, actual: $act") {
                            act shouldBe exp
                        }
                    }
            }
        }

        // -----------------------------------------------------------------------
        // Run 3: inlineEnums=false, suppressDefaultEnumValues=true
        //
        //   OrderStatus      → component, UNSET (number=0) suppressed by global option
        //   ShipmentPriority → inline, PRIORITY_UNSET (number=0) suppressed by global option
        //   ReturnReason     → component, REASON_UNSET (number=0) suppressed by global option
        //   PaymentMethod    → component, PAYMENT_UNSET (number=0) suppressed by global option
        //                       CRYPTO still suppressed by value annotation
        // -----------------------------------------------------------------------
        val run3 =
            ProtocGenOpenAPI.from(request()) {
                suppressDefaultEnumValues = true
                validateOutput = true
            }.compile()

        test("run3: no errors") {
            run3.hasError() shouldBe false
            run3.error shouldBe ""
        }

        withData<PluginProtos.CodeGeneratorResponse.File>(
            { "run3: matches reference: " + it.name },
            run3.fileList,
        ) { file ->
            val expected = mapper.readTree(
                EnumsTest::class.java
                    .getResourceAsStream("/${file.name}.run3.json")
                    .shouldNotBeNull()
                    .reader()
                    .readText(),
            )
            assertSoftly {
                collectJsonDiffs(expected, mapper.readTree(file.content))
                    .forEach { (path, exp, act) ->
                        withClue("run3 at $path — expected: $exp, actual: $act") {
                            act shouldBe exp
                        }
                    }
            }
        }

        // -----------------------------------------------------------------------
        // Run 4: inlineEnums=true, suppressDefaultEnumValues=true
        //
        //   OrderStatus      → inline, UNSET suppressed
        //   ShipmentPriority → inline, PRIORITY_UNSET suppressed
        //   ReturnReason     → component (annotation overrides), REASON_UNSET suppressed
        //   PaymentMethod    → inline, PAYMENT_UNSET suppressed, CRYPTO suppressed
        // -----------------------------------------------------------------------
        val run4 =
            ProtocGenOpenAPI.from(request()) {
                inlineEnums = true
                suppressDefaultEnumValues = true
                validateOutput = true
            }.compile()

        test("run4: no errors") {
            run4.hasError() shouldBe false
            run4.error shouldBe ""
        }

        withData<PluginProtos.CodeGeneratorResponse.File>(
            { "run4: matches reference: " + it.name },
            run4.fileList,
        ) { file ->
            val expected = mapper.readTree(
                EnumsTest::class.java
                    .getResourceAsStream("/${file.name}.run4.json")
                    .shouldNotBeNull()
                    .reader()
                    .readText(),
            )
            assertSoftly {
                collectJsonDiffs(expected, mapper.readTree(file.content))
                    .forEach { (path, exp, act) ->
                        withClue("run4 at $path — expected: $exp, actual: $act") {
                            act shouldBe exp
                        }
                    }
            }
        }
    })
