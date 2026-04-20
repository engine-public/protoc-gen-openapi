import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.engine.protoc.openapi.ProtocGenOpenAPI.Options.SchemaNamespaceCasing.CAPITALIZED
import com.engine.protoc.openapi.ProtocGenOpenAPI.Options.SchemaNamespaceCasing.UPPER_CASE
import com.engine.protoc.openapi.ProtocGenOpenAPI.Options.SchemaNamespaceSeparator.DASH
import com.engine.protoc.openapi.ProtocGenOpenAPI.Options.SchemaNamespaceSeparator.DOT
import com.engine.protoc.openapi.ProtocGenOpenAPI.Options.SchemaNamespaceSeparator.UNDERSCORE
import com.engine.protoc.openapi.ProtocGenOpenAPI.Options.SchemaNamespaceStrategy.FULL_PACKAGE
import com.engine.protoc.openapi.ProtocGenOpenAPI.Options.SchemaNamespaceStrategy.SIMPLIFIED_PACKAGE
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class NamespacingTest :
    FunSpec({

        assertSoftly = true

        fun request() =
            NamespacingTest::class.java
                .getResourceAsStream("/code-generator-request.binpb")
                .shouldNotBeNull()

        val mapper = ObjectMapper()

        fun reference(name: String) =
            NamespacingTest::class.java
                .getResourceAsStream("/$name")
                .shouldNotBeNull()
                .reader()
                .readText()

        fun assertMatchesReference(
            label: String,
            actual: String,
            refName: String,
        ) {
            val actualTree = mapper.readTree(actual)
            val expectedTree = mapper.readTree(reference(refName))
            assertSoftly {
                collectJsonDiffs(expectedTree, actualTree).forEach { (path, exp, act) ->
                    withClue("[$label] at $path — expected: $exp, actual: $act") {
                        act shouldBe exp
                    }
                }
            }
        }

        // -----------------------------------------------------------------------
        // Run 1 — strategy=NONE, sep=NONE, casing=NONE, VE=true, ST=true
        //
        // With strategy=NONE schema keys are unqualified message names. Both
        // packages define Item, so keys collide and only one definition survives.
        // setSchemaTitleToMessageName still applies the title to the surviving schema.
        // -----------------------------------------------------------------------
        val run1 =
            ProtocGenOpenAPI.from(request()) {
                merge = true
                schemaNamespaceVersionExtraction = true
                setSchemaTitleToMessageName = true
            }.compile()

        test("run1: no errors") {
            run1.hasError() shouldBe false
            run1.error shouldBe ""
        }

        test("run1: matches reference output") {
            assertMatchesReference("run1", run1.fileList.first().content, "namespacing.run1.openapi.json")
        }

        // -----------------------------------------------------------------------
        // Run 2 — strategy=NONE, sep=DASH, casing=NONE, VE=true, ST=false
        //
        // With strategy=NONE the separator and casing settings have no segments
        // to apply to; output is the same unqualified-key form as run1.
        // -----------------------------------------------------------------------
        val run2 =
            ProtocGenOpenAPI.from(request()) {
                merge = true
                schemaNamespaceSeparator = DASH
                schemaNamespaceVersionExtraction = true
            }.compile()

        test("run2: no errors") {
            run2.hasError() shouldBe false
            run2.error shouldBe ""
        }

        test("run2: matches reference output") {
            assertMatchesReference("run2", run2.fileList.first().content, "namespacing.run2.openapi.json")
        }

        // -----------------------------------------------------------------------
        // Run 3 — strategy=FULL_PACKAGE, sep=NONE, casing=CAPITALIZED, VE=true, ST=true
        //
        // Full package segments capitalised, no separator, version moved to end.
        // Title is the unqualified message name.
        // -----------------------------------------------------------------------
        val run3 =
            ProtocGenOpenAPI.from(request()) {
                merge = true
                schemaNamespaceStrategy = FULL_PACKAGE
                schemaNamespaceCasing = CAPITALIZED
                schemaNamespaceVersionExtraction = true
                setSchemaTitleToMessageName = true
            }.compile()

        test("run3: no errors") {
            run3.hasError() shouldBe false
            run3.error shouldBe ""
        }

        test("run3: matches reference output") {
            assertMatchesReference("run3", run3.fileList.first().content, "namespacing.run3.openapi.json")
        }

        // -----------------------------------------------------------------------
        // Run 4 — strategy=FULL_PACKAGE, sep=DOT, casing=NONE, VE=true, ST=true
        //
        // Full package segments joined with dots, no casing change, version moved
        // to end.  Title is the unqualified message name.
        // -----------------------------------------------------------------------
        val run4 =
            ProtocGenOpenAPI.from(request()) {
                merge = true
                schemaNamespaceStrategy = FULL_PACKAGE
                schemaNamespaceSeparator = DOT
                schemaNamespaceVersionExtraction = true
                setSchemaTitleToMessageName = true
            }.compile()

        test("run4: no errors") {
            run4.hasError() shouldBe false
            run4.error shouldBe ""
        }

        test("run4: matches reference output") {
            assertMatchesReference("run4", run4.fileList.first().content, "namespacing.run4.openapi.json")
        }

        // -----------------------------------------------------------------------
        // Run 5 — strategy=FULL_PACKAGE, sep=UNDERSCORE, casing=UPPER_CASE, VE=false, ST=true
        //
        // All segments (including the message name and version) uppercased and
        // joined with underscores.  No version extraction.  Title is the
        // original proto message name (mixed-case).
        // -----------------------------------------------------------------------
        val run5 =
            ProtocGenOpenAPI.from(request()) {
                merge = true
                schemaNamespaceStrategy = FULL_PACKAGE
                schemaNamespaceSeparator = UNDERSCORE
                schemaNamespaceCasing = UPPER_CASE
                setSchemaTitleToMessageName = true
            }.compile()

        test("run5: no errors") {
            run5.hasError() shouldBe false
            run5.error shouldBe ""
        }

        test("run5: matches reference output") {
            assertMatchesReference("run5", run5.fileList.first().content, "namespacing.run5.openapi.json")
        }

        // -----------------------------------------------------------------------
        // Run 6 — strategy=SIMPLIFIED_PACKAGE, sep=UNDERSCORE, casing=CAPITALIZED,
        //          VE=true, ST=true
        //
        // Common prefix (engine.protoc.openapi.example.namespacing) stripped,
        // remaining segments capitalised, version moved to end.  Title is the
        // unqualified message name.
        // -----------------------------------------------------------------------
        val run6 =
            ProtocGenOpenAPI.from(request()) {
                merge = true
                schemaNamespaceStrategy = SIMPLIFIED_PACKAGE
                schemaNamespaceSeparator = UNDERSCORE
                schemaNamespaceCasing = CAPITALIZED
                schemaNamespaceVersionExtraction = true
                setSchemaTitleToMessageName = true
            }.compile()

        test("run6: no errors") {
            run6.hasError() shouldBe false
            run6.error shouldBe ""
        }

        test("run6: matches reference output") {
            assertMatchesReference("run6", run6.fileList.first().content, "namespacing.run6.openapi.json")
        }

        test("run6: annotation title takes precedence over auto-generated title") {
            val schemas = mapper.readTree(run6.fileList.first().content)["components"]["schemas"]
            // catalog.proto annotates Item with title: "CatalogItem" — annotation wins over auto "Item"
            schemas["Catalog_Item_v1"]["title"].asText() shouldBe "CatalogItem"
            // inventory.proto has no annotation — auto title "Item" is used
            schemas["Inventory_Item_v2"]["title"].asText() shouldBe "Item"
        }
    })
