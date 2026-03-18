import com.engine.protoc.util.extensions.wrap
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos
import engine.protoc.utils.RegisteredExtensions
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class EnumDescriptorProtoWrapperTests :
    FunSpec({

        val registry = ExtensionRegistry.newInstance().apply {
            RegisteredExtensions.registerAllExtensions(this)
        }

        val cgreq = this::class.java.getResourceAsStream("/code-generator-request.binpb")
            .shouldNotBeNull()
            .use { input -> PluginProtos.CodeGeneratorRequest.parseFrom(input, registry) }
            .wrap()

        // comments.proto contains EnumWithComment (index 0) and EnumWithoutComment (index 1)
        val file = cgreq.sourceFileDescriptors.find {
            it.name == "engine/protoc/utils/comments.proto"
        }.shouldNotBeNull()

        // ---------------------------------------------------------------------------
        // enum count
        // ---------------------------------------------------------------------------

        test("comments.proto has exactly two top-level enums") {
            file.enumTypes shouldHaveSize 2
        }

        // ---------------------------------------------------------------------------
        // EnumDescriptorProtoWrapper.name
        // ---------------------------------------------------------------------------

        context("enum name") {
            test("first enum name is EnumWithComment") {
                file.enumTypes[0].name.shouldNotBeNull().value shouldBe "EnumWithComment"
            }

            test("second enum name is EnumWithoutComment") {
                file.enumTypes[1].name.shouldNotBeNull().value shouldBe "EnumWithoutComment"
            }
        }

        // ---------------------------------------------------------------------------
        // EnumDescriptorProtoWrapper.values
        // ---------------------------------------------------------------------------

        context("enum values") {
            test("EnumWithComment has one value") {
                file.enumTypes[0].values shouldHaveSize 1
            }

            test("EnumWithComment default value name is ENUM_WITH_COMMENT_DEFAULT") {
                file.enumTypes[0].values[0].name.shouldNotBeNull().value shouldBe
                    "ENUM_WITH_COMMENT_DEFAULT"
            }

            test("EnumWithComment default value number is 0") {
                file.enumTypes[0].values[0].number.shouldNotBeNull().value shouldBe 0
            }

            test("EnumWithoutComment has one value") {
                file.enumTypes[1].values shouldHaveSize 1
            }

            test("EnumWithoutComment default value name is ENUM_WITHOUT_COMMENT_DEFAULT") {
                file.enumTypes[1].values[0].name.shouldNotBeNull().value shouldBe
                    "ENUM_WITHOUT_COMMENT_DEFAULT"
            }

            test("EnumWithoutComment default value number is 0") {
                file.enumTypes[1].values[0].number.shouldNotBeNull().value shouldBe 0
            }
        }

        // ---------------------------------------------------------------------------
        // EnumDescriptorProtoWrapper.options
        // ---------------------------------------------------------------------------

        context("enum options") {
            test("EnumWithComment has non-null options (has registered/unregistered extension options)") {
                file.enumTypes[0].options.shouldNotBeNull()
            }

            test("EnumWithoutComment has null options") {
                file.enumTypes[1].options.shouldBeNull()
            }
        }

        // ---------------------------------------------------------------------------
        // EnumDescriptorProtoWrapper.reservedRanges and reservedNames
        // Neither test enum in comments.proto uses reserved, so both should be empty.
        // ---------------------------------------------------------------------------

        context("reserved ranges and names") {
            test("EnumWithComment has no reserved ranges") {
                file.enumTypes[0].reservedRanges.shouldBeEmpty()
            }

            test("EnumWithComment has no reserved names") {
                file.enumTypes[0].reservedNames.shouldBeEmpty()
            }

            test("EnumWithoutComment has no reserved ranges") {
                file.enumTypes[1].reservedRanges.shouldBeEmpty()
            }

            test("EnumWithoutComment has no reserved names") {
                file.enumTypes[1].reservedNames.shouldBeEmpty()
            }
        }

        // ---------------------------------------------------------------------------
        // EnumDescriptorProtoWrapper.visibility
        // proto3 enums never carry an explicit visibility modifier.
        // ---------------------------------------------------------------------------

        context("visibility") {
            test("proto3 EnumWithComment has null visibility") {
                file.enumTypes[0].visibility.shouldBeNull()
            }

            test("proto3 EnumWithoutComment has null visibility") {
                file.enumTypes[1].visibility.shouldBeNull()
            }
        }

        // ---------------------------------------------------------------------------
        // EnumValueDescriptorProtoWrapper.options
        // The default value of EnumWithComment has no options itself.
        // ---------------------------------------------------------------------------

        context("enum value options") {
            test("ENUM_WITH_COMMENT_DEFAULT value has null options") {
                file.enumTypes[0].values[0].options.shouldBeNull()
            }
        }
    })